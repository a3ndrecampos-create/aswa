package com.rotacerta.entregador.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.rotacerta.entregador.MainActivity
import com.rotacerta.entregador.data.AppDatabase
import com.rotacerta.entregador.data.Delivery
import com.rotacerta.entregador.data.DeliveryStatus
import com.rotacerta.entregador.data.HistoryEntry
import com.rotacerta.entregador.domain.GpsLocationProvider
import com.rotacerta.entregador.domain.RouteOptimizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Roda em segundo plano (mesmo com o app minimizado ou usando outro app de navegação)
 * observando o GPS. Quando chega perto de uma parada pendente ainda não avisada,
 * mostra um cartão flutuante por cima de tudo com "Você chegou" + os pacotes daquela
 * parada + botão de marcar entregue.
 *
 * Precisa da permissão "Aparecer sobre outros apps" (SYSTEM_ALERT_WINDOW) pra desenhar
 * o cartão flutuante, e da permissão de localização pra saber onde o entregador está.
 */
class ArrivalMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager
    private lateinit var db: AppDatabase
    private var overlayView: View? = null
    private val notifiedStops = mutableSetOf<Int>()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        db = AppDatabase.getInstance(applicationContext)
        startForeground(NOTIF_ID, buildNotification())
        monitorLocation()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun monitorLocation() {
        scope.launch {
            try {
                GpsLocationProvider.locationUpdates(applicationContext).collect { location ->
                    checkArrival(location.latitude, location.longitude)
                }
            } catch (_: Exception) {
                // Sem permissão ou GPS desligado: o serviço continua vivo (notificação
                // fixa), só não consegue monitorar até a pessoa ativar o GPS.
            }
        }
    }

    private suspend fun checkArrival(lat: Double, lng: Double) {
        if (overlayView != null) return // já tem um cartão na tela, espera fechar

        val pendentes = withContext(Dispatchers.IO) {
            db.deliveryDao().observeAll().first().filter { it.status == DeliveryStatus.PENDENTE }
        }
        if (pendentes.isEmpty()) {
            // Rota concluída — não tem mais sentido continuar rodando em segundo plano
            stopSelf()
            return
        }

        val porParada = pendentes.groupBy { it.order }
        for ((stopOrder, pacotes) in porParada) {
            if (stopOrder in notifiedStops) continue
            val rep = pacotes.first()
            val distMeters = RouteOptimizer.haversineKm(lat, lng, rep.lat, rep.lng) * 1000
            if (distMeters <= ARRIVAL_RADIUS_METERS) {
                notifiedStops.add(stopOrder)
                showOverlay(stopOrder, rep.address, pacotes)
                break
            }
        }
    }

    // ---------------- Cartão flutuante (overlay) ----------------

    private fun showOverlay(stopOrder: Int, address: String, packages: List<Delivery>) {
        if (!Settings.canDrawOverlays(this)) return
        removeOverlay()

        val card = buildOverlayCard(stopOrder, address, packages)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = 60
        }

        runCatching { windowManager.addView(card, params) }
        overlayView = card
    }

    private fun buildOverlayCard(stopOrder: Int, address: String, packages: List<Delivery>): View {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E222B"))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor("#8B5CF6"))
            }
            val margin = dp(12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(margin, margin, margin, margin) }
        }

        root.addView(TextView(this).apply {
            text = "🎯 Você chegou! — Parada $stopOrder"
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = address
            setTextColor(Color.parseColor("#A9AFBC"))
            textSize = 13f
            setPadding(0, dp(4), 0, dp(10))
        })

        packages.forEachIndexed { i, d ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6) }
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(TextView(this).apply {
                text = if (d.trackingCode.isNotBlank()) "📦 ${d.trackingCode}" else "📦 Pacote ${i + 1}"
                setTextColor(Color.WHITE)
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(Button(this).apply {
                text = "Entregue"
                textSize = 12f
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#2FA86A"))
                    cornerRadius = dp(8).toFloat()
                }
                setPadding(dp(14), dp(4), dp(14), dp(4))
                minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
                setOnClickListener { markDelivered(d) }
            })
            root.addView(row)
        }

        root.addView(TextView(this).apply {
            text = "Não esqueça de marcar como entregue pra liberar a próxima parada."
            setTextColor(Color.parseColor("#8B93A3"))
            textSize = 11f
            setPadding(0, dp(10), 0, dp(8))
        })

        root.addView(Button(this).apply {
            text = "Fechar"
            textSize = 12f
            setTextColor(Color.parseColor("#8B93A3"))
            background = null
            setOnClickListener { removeOverlay() }
        })

        return root
    }

    private fun markDelivered(delivery: Delivery) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                db.deliveryDao().update(delivery.copy(status = DeliveryStatus.ENTREGUE, deliveredAt = now))
                db.historyDao().insert(
                    HistoryEntry(originalDeliveryId = delivery.id, address = delivery.address, value = delivery.value, deliveredAt = now)
                )
            }
            removeOverlay()
        }
    }

    private fun removeOverlay() {
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
    }

    // ---------------- Notificação (obrigatória pra rodar como foreground service) ----------------

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Monitoramento de rota", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Avisa quando você chega perto de uma parada da rota" }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }

        val openAppIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RotaCerta")
            .setContentText("Acompanhando sua rota em segundo plano")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        scope.cancel()
    }

    companion object {
        private const val NOTIF_ID = 8801
        private const val CHANNEL_ID = "arrival_monitor"
        private const val ARRIVAL_RADIUS_METERS = 100.0
    }
}
