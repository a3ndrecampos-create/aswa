package com.rotacerta.entregador

import android.app.Application
import com.rotacerta.entregador.billing.BillingManager
import com.rotacerta.entregador.billing.PremiumAccessManager
import com.rotacerta.entregador.data.AppDatabase
import com.rotacerta.entregador.data.ConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RotaCertaApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val configRepository: ConfigRepository by lazy { ConfigRepository(this) }

    // Vive enquanto o processo do app viver — usado só pra manter o acesso Premium
    // (trial/assinatura) sempre em dia, reagindo a mudanças de status da compra.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
                val logFile = File(getExternalFilesDir(null), "crash_$timestamp.txt")
                logFile.writeText(sw.toString())
            } catch (ignored: Throwable) {
                // não deixar o próprio handler de crash causar outro crash
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        PremiumAccessManager.initialize(this)
        BillingManager.initialize(this)
        appScope.launch {
            BillingManager.isPro.collect { PremiumAccessManager.recompute(this@RotaCertaApp) }
        }
    }
}
