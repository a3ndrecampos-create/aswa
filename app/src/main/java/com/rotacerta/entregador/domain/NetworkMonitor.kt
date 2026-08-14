package com.rotacerta.entregador.domain

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * O app depende de internet pra duas coisas: buscar CEP/geocodificar endereços novos
 * (ViaCEP + Nominatim) e carregar os tiles do mapa. Tudo o mais (ver/organizar entregas
 * já geocodificadas, marcar como entregue, ver histórico e ganhos) funciona 100% offline
 * porque já está no banco local. Esse utilitário existe pra dar mensagens claras nos dois
 * pontos que realmente precisam de rede, em vez de deixar a exceção genérica de timeout
 * confundir o entregador.
 */
object NetworkMonitor {

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService<ConnectivityManager>() ?: return true // não trava o app se o serviço não existir
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** Emite true/false a cada mudança de conectividade, pra banners reativos na UI. */
    fun observe(context: Context): Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService<ConnectivityManager>()
        if (cm == null) {
            trySend(true)
            awaitClose { }
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) { trySend(isOnline(context)) }
            override fun onLost(network: android.net.Network) { trySend(isOnline(context)) }
            override fun onCapabilitiesChanged(network: android.net.Network, caps: NetworkCapabilities) {
                trySend(isOnline(context))
            }
        }
        trySend(isOnline(context))
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
