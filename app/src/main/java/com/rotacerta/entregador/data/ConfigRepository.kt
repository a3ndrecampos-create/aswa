package com.rotacerta.entregador.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class Vehicle(val avgSpeedKmh: Double) { MOTO(28.0), CARRO(22.0), BIKE(14.0), PE(5.0) }
enum class NavApp { GOOGLE, WAZE }
enum class RouteSortDirection { NEAREST_FIRST, FARTHEST_FIRST }

data class SavedDestination(val label: String, val address: String, val lat: Double, val lng: Double)

data class AppConfig(
    val originAddress: String = "",
    val originLat: Double? = null,
    val originLng: Double? = null,
    val homeAddress: String = "",
    val homeLat: Double? = null,
    val homeLng: Double? = null,
    val savedDestinations: List<SavedDestination> = emptyList(),
    val vehicle: Vehicle = Vehicle.MOTO,
    val navApp: NavApp = NavApp.GOOGLE,
    val defaultValue: Double = 6.0,
    val notifications: Boolean = true,
    val sortDirection: RouteSortDirection = RouteSortDirection.NEAREST_FIRST,
    val roundTrip: Boolean = false,
    val lightTheme: Boolean = false
) {
    companion object {
        const val MAX_SAVED_DESTINATIONS = 3
    }
}

// Codificação simples (sem precisar de biblioteca de JSON) pra guardar a lista
// de destinos salvos como um texto só no DataStore.
private const val DEST_FIELD_SEP = "\u0001"
private const val DEST_ITEM_SEP = "\u0002"

private fun encodeDestinations(list: List<SavedDestination>): String =
    list.joinToString(DEST_ITEM_SEP) { "${it.label}$DEST_FIELD_SEP${it.address}$DEST_FIELD_SEP${it.lat}$DEST_FIELD_SEP${it.lng}" }

private fun decodeDestinations(raw: String): List<SavedDestination> {
    if (raw.isBlank()) return emptyList()
    return raw.split(DEST_ITEM_SEP).mapNotNull { entry ->
        val parts = entry.split(DEST_FIELD_SEP)
        if (parts.size == 4) {
            val lat = parts[2].toDoubleOrNull()
            val lng = parts[3].toDoubleOrNull()
            if (lat != null && lng != null) SavedDestination(parts[0], parts[1], lat, lng) else null
        } else null
    }
}

private val Context.dataStore by preferencesDataStore(name = "rotacerta_config")

class ConfigRepository(private val context: Context) {
    private object Keys {
        val ORIGIN_ADDRESS = stringPreferencesKey("origin_address")
        val ORIGIN_LAT = doublePreferencesKey("origin_lat")
        val ORIGIN_LNG = doublePreferencesKey("origin_lng")
        val HOME_ADDRESS = stringPreferencesKey("home_address")
        val HOME_LAT = doublePreferencesKey("home_lat")
        val HOME_LNG = doublePreferencesKey("home_lng")
        val SAVED_DESTINATIONS = stringPreferencesKey("saved_destinations")
        val VEHICLE = stringPreferencesKey("vehicle")
        val NAV_APP = stringPreferencesKey("nav_app")
        val DEFAULT_VALUE = doublePreferencesKey("default_value")
        val NOTIFICATIONS = booleanPreferencesKey("notifications")
        val SORT_DIRECTION = stringPreferencesKey("sort_direction")
        val ROUND_TRIP = booleanPreferencesKey("round_trip")
        val LIGHT_THEME = booleanPreferencesKey("light_theme")
    }

    private fun fromPrefs(prefs: androidx.datastore.preferences.core.Preferences): AppConfig = AppConfig(
        originAddress = prefs[Keys.ORIGIN_ADDRESS] ?: "",
        originLat = prefs[Keys.ORIGIN_LAT],
        originLng = prefs[Keys.ORIGIN_LNG],
        homeAddress = prefs[Keys.HOME_ADDRESS] ?: "",
        homeLat = prefs[Keys.HOME_LAT],
        homeLng = prefs[Keys.HOME_LNG],
        savedDestinations = decodeDestinations(prefs[Keys.SAVED_DESTINATIONS] ?: ""),
        vehicle = prefs[Keys.VEHICLE]?.let { runCatching { Vehicle.valueOf(it) }.getOrNull() } ?: Vehicle.MOTO,
        navApp = prefs[Keys.NAV_APP]?.let { runCatching { NavApp.valueOf(it) }.getOrNull() } ?: NavApp.GOOGLE,
        defaultValue = prefs[Keys.DEFAULT_VALUE] ?: 6.0,
        notifications = prefs[Keys.NOTIFICATIONS] ?: true,
        sortDirection = prefs[Keys.SORT_DIRECTION]?.let { runCatching { RouteSortDirection.valueOf(it) }.getOrNull() } ?: RouteSortDirection.NEAREST_FIRST,
        roundTrip = prefs[Keys.ROUND_TRIP] ?: false,
        lightTheme = prefs[Keys.LIGHT_THEME] ?: false
    )

    val configFlow: Flow<AppConfig> = context.dataStore.data.map { prefs -> fromPrefs(prefs) }

    private fun writeToPrefs(prefs: androidx.datastore.preferences.core.MutablePreferences, config: AppConfig) {
        prefs[Keys.ORIGIN_ADDRESS] = config.originAddress
        config.originLat?.let { prefs[Keys.ORIGIN_LAT] = it } ?: prefs.remove(Keys.ORIGIN_LAT)
        config.originLng?.let { prefs[Keys.ORIGIN_LNG] = it } ?: prefs.remove(Keys.ORIGIN_LNG)
        prefs[Keys.HOME_ADDRESS] = config.homeAddress
        config.homeLat?.let { prefs[Keys.HOME_LAT] = it } ?: prefs.remove(Keys.HOME_LAT)
        config.homeLng?.let { prefs[Keys.HOME_LNG] = it } ?: prefs.remove(Keys.HOME_LNG)
        prefs[Keys.SAVED_DESTINATIONS] = encodeDestinations(config.savedDestinations.take(AppConfig.MAX_SAVED_DESTINATIONS))
        prefs[Keys.VEHICLE] = config.vehicle.name
        prefs[Keys.NAV_APP] = config.navApp.name
        prefs[Keys.DEFAULT_VALUE] = config.defaultValue
        prefs[Keys.NOTIFICATIONS] = config.notifications
        prefs[Keys.SORT_DIRECTION] = config.sortDirection.name
        prefs[Keys.ROUND_TRIP] = config.roundTrip
        prefs[Keys.LIGHT_THEME] = config.lightTheme
    }

    /**
     * Atualiza a config lendo e escrevendo dentro do MESMO bloco `edit` do DataStore
     * (que é atômico/serializado internamente) — assim, mesmo que duas telas chamem
     * update() quase ao mesmo tempo, uma nunca sobrescreve o que a outra acabou de
     * gravar (o antigo `update(config: AppConfig)` lia um valor em cache que podia
     * já estar desatualizado quando a escrita de verdade acontecia).
     */
    suspend fun update(transform: (AppConfig) -> AppConfig) {
        context.dataStore.edit { prefs ->
            val current = fromPrefs(prefs)
            val updated = transform(current)
            writeToPrefs(prefs, updated)
        }
    }
}
