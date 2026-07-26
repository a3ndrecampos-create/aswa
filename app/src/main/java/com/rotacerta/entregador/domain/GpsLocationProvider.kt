package com.rotacerta.entregador.domain

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Obtém a localização atual do GPS sem depender do Google Play Services
 * (usa a LocationManager padrão do Android, mais leve e compatível desde o Android 7).
 */
object GpsLocationProvider {

    suspend fun getCurrentLocation(context: Context): Location = suspendCancellableCoroutine { cont ->
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> {
                cont.resumeWithException(IllegalStateException("Ative o GPS/localização do celular para usar sua posição atual"))
                return@suspendCancellableCoroutine
            }
        }

        try {
            // Última localização conhecida, se for recente o suficiente (instantâneo, sem esperar o GPS)
            val lastKnown = locationManager.getLastKnownLocation(provider)
            if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < 5 * 60_000) {
                cont.resume(lastKnown)
                return@suspendCancellableCoroutine
            }

            var finished = false
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (finished) return
                    finished = true
                    locationManager.removeUpdates(this)
                    cont.resume(location)
                }

                @Deprecated("Deprecated na API 29, mas necessário para compatibilidade com API 24+")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {
                    if (finished) return
                    finished = true
                    locationManager.removeUpdates(this)
                    cont.resumeWithException(IllegalStateException("Localização desativada. Ative o GPS e tente novamente."))
                }
            }

            cont.invokeOnCancellation { locationManager.removeUpdates(listener) }
            locationManager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
        } catch (e: SecurityException) {
            cont.resumeWithException(e)
        }
    }
}
