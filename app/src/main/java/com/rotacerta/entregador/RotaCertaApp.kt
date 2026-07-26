package com.rotacerta.entregador

import android.app.Application
import com.rotacerta.entregador.data.AppDatabase
import com.rotacerta.entregador.data.ConfigRepository
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RotaCertaApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val configRepository: ConfigRepository by lazy { ConfigRepository(this) }

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
    }
}
