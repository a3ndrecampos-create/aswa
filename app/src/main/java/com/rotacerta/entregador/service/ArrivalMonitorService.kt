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
    // Guarda os IDs das entregas (não o número da parada!) já avisadas. O número da
    // parada (order) é reatribuído toda vez que a rota é reotimizada ou reordenada
    // manualmente, então usar Int aqui faria uma parada nova "herdar" o número de uma
    // parada antiga já notificada e nunca mais avisar essa parada nova de verdade.
    // Os IDs (@PrimaryKey no Room) nunca mudam, então são a chave certa pra isso.
    private val notifiedDeliveryIds = mutableSetOf<Long>()

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
                    // Ignora leituras muito imprecisas (torre de celular/wifi) pra não
                    // disparar aviso de chegada errado — só confia em GPS/rede com
                    // precisão razoável.
                    if (!location.hasAccuracy() || location.accuracy <= 60f) {
                        checkArrival(location.latitude, location.longitude)
                    }
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
            // Já avisado se TODOS os pacotes desse grupo já foram notificados antes
            // (com pelo menos um pacote novo no grupo, precisa avisar de novo).
            if (pacotes.all { it.id in notifiedDeliveryIds }) continue
            val rep = pacotes.first()
            val distMeters = RouteOptimizer.haversineKm(lat, lng, rep.lat, rep.lng) * 1000
            if (distMeters <= ARRIVAL_RADIUS_METERS) {
                notifiedDeliveryIds.addAll(pacotes.map { it.id })
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
            y = 90
        }

        runCatching { windowManager.addView(card, params) }
        overlayView = card
    }

    private fun buildOverlayCard(stopOrder: Int, address: String, packages: List<Delivery>): View {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val colorBg = Color.parseColor("#1A1D24")
        val colorAccent = Color.parseColor("#8B5CF6")
        val colorSuccess = Color.parseColor("#2FA86A")
        val colorMuted = Color.parseColor("#9099AA")
        val colorChipBg = Color.parseColor("#242833")

        val outer = LinearLayout(this).apply {
            setPadding(dp(28), 0, dp(28), 0)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(colorBg)
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.parseColor("#33FFFFFF"))
            }
            elevation = dp(10).toFloat()
            outer.addView(this, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        // Cabeçalho: selo com ícone + título/subtítulo, e um "x" discreto pra fechar
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val badge = TextView(this).apply {
            text = "📍"
            textSize = 17f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(colorAccent) }
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
        }
        header.addView(badge)
        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = dp(10) }
        }
        titleBlock.addView(TextView(this).apply {
            text = "Você chegou"
            setTextColor(Color.WHITE)
            textSize = 15.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        titleBlock.addView(TextView(this).apply {
            text = "Parada $stopOrder da rota"
            setTextColor(colorAccent)
            textSize = 11.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        header.addView(titleBlock)
        header.addView(TextView(this).apply {
            text = "✕"
            setTextColor(colorMuted)
            textSize = 15f
            setPadding(dp(10), dp(6), dp(6), dp(6))
            setOnClickListener { removeOverlay() }
        })
        root.addView(header)

        root.addView(TextView(this).apply {
            text = address
            setTextColor(colorMuted)
            textSize = 13f
            setPadding(dp(44), dp(2), 0, dp(14))
        })

        // Linha fina separando o cabeçalho da lista de pacotes
        root.addView(View(this).apply {
            setBackgroundColor(Color.parseColor("#262A33"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
                .apply { bottomMargin = dp(12) }
        })

        val label = if (packages.size > 1) "PACOTES A ENTREGAR AQUI (${packages.size})" else "PACOTE A ENTREGAR"
        root.addView(TextView(this).apply {
            text = label
            setTextColor(colorMuted)
            textSize = 10.5f
            letterSpacing = 0.05f
            setPadding(0, 0, 0, dp(8))
        })

        packages.forEachIndexed { i, d ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(10), dp(10))
                background = GradientDrawable().apply { setColor(colorChipBg); cornerRadius = dp(12).toFloat() }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { if (i > 0) topMargin = dp(8) }
            }

            // Selo grande e bem visível com "posição/total" (ex: 1/4) — é a informação
            // mais importante quando tem vários pacotes na mesma parada.
            row.addView(TextView(this).apply {
                text = "${i + 1}/${packages.size}"
                setTextColor(Color.WHITE)
                textSize = 17f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply { setColor(colorAccent); cornerRadius = dp(10).toFloat() }
                setPadding(dp(12), dp(6), dp(12), dp(6))
                minWidth = dp(46)
            })

            val infoCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = dp(12) }
            }
            infoCol.addView(TextView(this).apply {
                text = "Pacote ${i + 1} de ${packages.size}"
                setTextColor(Color.WHITE)
                textSize = 14.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            if (d.trackingCode.isNotBlank()) {
                infoCol.addView(TextView(this).apply {
                    text = d.trackingCode
                    setTextColor(colorMuted)
                    textSize = 12f
                    setPadding(0, dp(2), 0, 0)
                })
            }
            row.addView(infoCol)

            row.addView(Button(this).apply {
                text = "✓  Entregue"
                textSize = 12.5f
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply { setColor(colorSuccess); cornerRadius = dp(10).toFloat() }
                setPadding(dp(16), dp(6), dp(16), dp(6))
                minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
                stateListAnimator = null
                setOnClickListener { markDelivered(d) }
            })
            root.addView(row)
        }

        root.addView(TextView(this).apply {
            text = "Marque como entregue pra liberar a próxima parada da rota."
            setTextColor(colorMuted)
            textSize = 11.5f
            setPadding(0, dp(12), 0, 0)
        })

        return outer
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
