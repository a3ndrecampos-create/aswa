package com.rotacerta.entregador.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rotacerta.entregador.RotaCertaApp
import com.rotacerta.entregador.data.*
import com.rotacerta.entregador.domain.GeocodingService
import com.rotacerta.entregador.domain.LatLng
import com.rotacerta.entregador.domain.RouteOptimizer
import com.rotacerta.entregador.domain.XlsxImporter
import com.rotacerta.entregador.network.CepResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ImportProgress {
    object Idle : ImportProgress()
    data class Running(val current: Int, val total: Int, val message: String) : ImportProgress()
    data class Done(val added: Int, val failed: Int) : ImportProgress()
}

sealed class ScanLabelResult {
    data class Found(val position: Int, val total: Int, val address: String, val ambiguous: Boolean, val numero: String?) : ScanLabelResult()
    data class NotFound(val code: String) : ScanLabelResult()
}

class RotaViewModel(app: Application) : AndroidViewModel(app) {
    private val rotaCertaApp = app as RotaCertaApp
    private val db = rotaCertaApp.database
    private val configRepo = rotaCertaApp.configRepository
    private val deliveryDao = db.deliveryDao()
    private val historyDao = db.historyDao()

    val deliveries: StateFlow<List<Delivery>> =
        deliveryDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<HistoryEntry>> =
        historyDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val config: StateFlow<AppConfig> =
        configRepo.configFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppConfig())

    private val _importProgress = MutableStateFlow<ImportProgress>(ImportProgress.Idle)
    val importProgress: StateFlow<ImportProgress> = _importProgress

    private val _scanLabelResult = MutableStateFlow<ScanLabelResult?>(null)
    val scanLabelResult: StateFlow<ScanLabelResult?> = _scanLabelResult

    fun clearScanLabelResult() { _scanLabelResult.value = null }

    private val _toast = MutableSharedFlow<String>()
    val toast: SharedFlow<String> = _toast

    init {
        // Semeia algumas entregas de exemplo na primeira execução, como no app original
        viewModelScope.launch {
            if (deliveryDao.count() == 0) {
                seedSampleDeliveries()
            }
        }
    }

    private suspend fun seedSampleDeliveries() {
        listOf(
            Delivery(address = "Av. Paulista, 1578 - Bela Vista, São Paulo - SP", lat = -23.5613, lng = -46.6565, priority = Priority.ALTA, deadline = "14:30", value = 7.5, order = 1),
            Delivery(address = "Rua Augusta, 900 - Consolação, São Paulo - SP", lat = -23.5555, lng = -46.6553, priority = Priority.MEDIA, deadline = "15:15", value = 6.0, order = 2),
            Delivery(address = "Shopping Ibirapuera, São Paulo - SP", lat = -23.6084, lng = -46.6647, priority = Priority.BAIXA, deadline = "", value = 5.5, order = 3)
        ).forEach { deliveryDao.insert(it) }
    }

    // ---------------- CRUD de entregas ----------------

    fun addDelivery(address: String, priority: Priority, deadline: String, value: Double, cepData: CepResponse?, numero: String, trackingCode: String = "") {
        viewModelScope.launch {
            try {
                val geo = GeocodingService.geocode(address, cepData, numero)
                deliveryDao.insert(
                    Delivery(
                        address = address, lat = geo.lat, lng = geo.lng,
                        priority = priority, deadline = deadline,
                        value = value, approxLocation = geo.approx,
                        trackingCode = trackingCode
                    )
                )
                _toast.emit(if (geo.approx) "Endereço adicionado (localização aproximada)" else "Endereço adicionado à rota")
            } catch (e: Exception) {
                _toast.emit(e.message ?: "Não foi possível localizar o endereço")
            }
        }
    }

    fun markDelivered(delivery: Delivery) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            deliveryDao.update(delivery.copy(status = DeliveryStatus.ENTREGUE, deliveredAt = now))
            historyDao.insert(HistoryEntry(originalDeliveryId = delivery.id, address = delivery.address, value = delivery.value, deliveredAt = now))
            _toast.emit("Entrega confirmada ✔")
        }
    }

    fun removeDelivery(delivery: Delivery) {
        viewModelScope.launch { deliveryDao.delete(delivery) }
    }

    fun clearAllDeliveries() {
        viewModelScope.launch { deliveryDao.clearAll() }
    }

    fun resetHistory() {
        viewModelScope.launch {
            historyDao.clearAll()
            _toast.emit("Histórico apagado")
        }
    }

    // ---------------- Consulta de CEP ----------------

    suspend fun lookupCep(cep: String): CepResponse = GeocodingService.lookupCep(cep)

    // ---------------- Scanner de pacotes (lê a etiqueta: CEP + número) ----------------

    fun scanPackageByTrackingCode(code: String) {
        viewModelScope.launch {
            val clean = code.trim()
            if (clean.isBlank()) return@launch

            val pendentes = deliveries.value.filter { it.status == DeliveryStatus.PENDENTE }.sortedBy { it.order }
            val match = deliveryDao.findByTrackingCode(clean)
                ?: pendentes.firstOrNull { it.trackingCode.isNotBlank() && (it.trackingCode == clean || clean.contains(it.trackingCode) || it.trackingCode.contains(clean)) }

            if (match == null || match.status != DeliveryStatus.PENDENTE) {
                _scanLabelResult.value = ScanLabelResult.NotFound(clean)
                return@launch
            }

            val posicao = match.order
            val totalParadas = pendentes.map { it.order }.distinct().size
            val pacotesNestaParada = pendentes.count { it.order == match.order }
            deliveryDao.markVerified(match.id)
            _scanLabelResult.value = ScanLabelResult.Found(
                position = posicao, total = totalParadas, address = match.address,
                ambiguous = pacotesNestaParada > 1, numero = null
            )
        }
    }

    // ---------------- Otimização de rota ----------------

    fun optimizeRoute() {
        viewModelScope.launch {
            val pending = deliveries.value.filter { it.status == DeliveryStatus.PENDENTE }
            if (pending.size < 2) {
                _toast.emit("Adicione ao menos 2 entregas pendentes para otimizar")
                return@launch
            }
            val cfg = config.value
            val origin = cfg.originLat?.let { lat -> cfg.originLng?.let { lng -> LatLng(lat, lng) } }
            val optimized = RouteOptimizer.optimize(pending, origin, cfg.sortDirection, cfg.roundTrip)
            deliveryDao.updateAll(optimized)
            _toast.emit("Rota otimizada! ${optimized.size} paradas reordenadas.")
        }
    }

    fun routeStats(): RouteOptimizer.RouteStats {
        val cfg = config.value
        val origin = cfg.originLat?.let { lat -> cfg.originLng?.let { lng -> LatLng(lat, lng) } }
        val pending = deliveries.value.filter { it.status == DeliveryStatus.PENDENTE }
        return RouteOptimizer.computeStats(pending, origin, cfg.vehicle.avgSpeedKmh, cfg.roundTrip)
    }

    // ---------------- Config ----------------

    fun updateConfig(update: (AppConfig) -> AppConfig) {
        viewModelScope.launch { configRepo.update(update(config.value)) }
    }

    fun setOrigin(address: String) {
        viewModelScope.launch {
            if (address.isBlank()) {
                updateConfig { it.copy(originAddress = "", originLat = null, originLng = null) }
                return@launch
            }
            try {
                val geo = GeocodingService.geocode(address)
                updateConfig { it.copy(originAddress = address, originLat = geo.lat, originLng = geo.lng) }
                _toast.emit("Ponto de partida definido")
            } catch (e: Exception) {
                _toast.emit("Endereço de partida não encontrado")
            }
        }
    }

    fun setOriginFromGps() {
        viewModelScope.launch {
            try {
                val location = withContext(Dispatchers.IO) {
                    com.rotacerta.entregador.domain.GpsLocationProvider.getCurrentLocation(getApplication())
                }
                updateConfig {
                    it.copy(
                        originAddress = "Minha localização atual (GPS)",
                        originLat = location.latitude,
                        originLng = location.longitude
                    )
                }
                _toast.emit("Ponto de partida definido pelo GPS")
            } catch (e: SecurityException) {
                _toast.emit("Permita o acesso à localização para usar o GPS")
            } catch (e: Exception) {
                _toast.emit(e.message ?: "Não foi possível obter sua localização")
            }
        }
    }

    // ---------------- Importação de planilha (.xlsx) ----------------

    fun importXlsx(uri: Uri) {
        viewModelScope.launch {
            try {
                _importProgress.value = ImportProgress.Running(0, 0, "Lendo planilha...")
                val rows = withContext(Dispatchers.IO) {
                    XlsxImporter.import(getApplication(), uri, config.value.defaultValue)
                }
                if (rows.isEmpty()) {
                    _toast.emit("Não encontrei uma coluna de endereço na planilha. Verifique os cabeçalhos.")
                    return@launch
                }
                var added = 0
                var failed = 0
                rows.forEachIndexed { i, row ->
                    _importProgress.value = ImportProgress.Running(i + 1, rows.size, "Processando ${i + 1} de ${rows.size}...")
                    try {
                        val geo = if (row.lat != null && row.lng != null) {
                            com.rotacerta.entregador.domain.GeoResult(row.lat, row.lng)
                        } else {
                            val g = GeocodingService.geocode(row.address)
                            GeocodingService.politeDelay()
                            g
                        }
                        deliveryDao.insert(
                            Delivery(
                                address = row.address, lat = geo.lat, lng = geo.lng,
                                priority = row.priority, deadline = row.deadline,
                                value = row.value ?: config.value.defaultValue,
                                order = row.sequence ?: 999, approxLocation = geo.approx,
                                trackingCode = row.trackingCode
                            )
                        )
                        added++
                    } catch (e: Throwable) {
                        android.util.Log.e("RotaViewModel", "Falha ao importar linha ${i + 1}: ${row.address}", e)
                        failed++
                    }
                }
                _importProgress.value = ImportProgress.Done(added, failed)
                _toast.emit("$added entregas importadas" + if (failed > 0) ", $failed não localizadas" else "")
            } catch (e: Throwable) {
                android.util.Log.e("RotaViewModel", "Erro ao importar planilha", e)
                _toast.emit("Erro ao ler o arquivo: ${e.javaClass.simpleName} - ${e.message}")
            } finally {
                _importProgress.value = ImportProgress.Idle
            }
        }
    }

    fun resetImportProgress() { _importProgress.value = ImportProgress.Idle }
}
