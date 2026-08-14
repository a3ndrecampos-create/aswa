package com.rotacerta.entregador.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rotacerta.entregador.RotaCertaApp
import com.rotacerta.entregador.data.*
import com.rotacerta.entregador.domain.BackupManager
import com.rotacerta.entregador.domain.BackupPayload
import com.rotacerta.entregador.domain.GeocodingService
import com.rotacerta.entregador.domain.LatLng
import com.rotacerta.entregador.domain.RouteOptimizer
import com.rotacerta.entregador.domain.XlsxImporter
import com.rotacerta.entregador.network.CepResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class BackupState {
    object Idle : BackupState()
    object Working : BackupState()
    data class RestoreSuccess(val deliveriesCount: Int, val historyCount: Int) : BackupState()
    data class Error(val message: String) : BackupState()
}

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

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState

    fun resetBackupState() { _backupState.value = BackupState.Idle }

    val isOnline: StateFlow<Boolean> = com.rotacerta.entregador.domain.NetworkMonitor.observe(getApplication())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.rotacerta.entregador.domain.NetworkMonitor.isOnline(getApplication()))

    /**
     * Endereços novos (adicionar manualmente, importar planilha, escanear etiqueta, salvar
     * destino) sempre exigem localizar o endereço num servidor externo — sem internet, a
     * chamada ia falhar com um erro de timeout genérico e confuso. Checar antes permite dar
     * uma mensagem clara e não gastar 10-15s esperando o timeout de rede à toa.
     */
    private suspend fun ensureOnlineOrToast(): Boolean {
        if (com.rotacerta.entregador.domain.NetworkMonitor.isOnline(getApplication())) return true
        _toast.emit("Sem internet. Pra localizar um endereço novo, conecte-se e tente de novo — suas entregas e o histórico continuam disponíveis normalmente.")
        return false
    }

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
            if (!ensureOnlineOrToast()) return@launch
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
            val returnPoint = cfg.homeLat?.let { lat -> cfg.homeLng?.let { lng -> LatLng(lat, lng) } } ?: origin
            val optimized = RouteOptimizer.optimize(pending, origin, cfg.sortDirection, cfg.roundTrip, returnPoint)
            deliveryDao.updateAll(optimized)
            _toast.emit("Rota otimizada! ${optimized.size} paradas reordenadas.")
        }
    }

    /**
     * Aplica uma nova ordem de paradas definida manualmente pelo entregador (arrastar
     * na aba Mapa). Cada grupo em [newStopOrder] é uma parada (podem ser várias entregas
     * no mesmo endereço); a posição na lista vira o novo número da parada (`order`).
     */
    fun reorderStops(newStopOrder: List<List<Delivery>>) {
        viewModelScope.launch {
            val updated = newStopOrder.flatMapIndexed { index, group ->
                group.map { it.copy(order = index + 1) }
            }
            if (updated.isNotEmpty()) {
                deliveryDao.updateAll(updated)
                _toast.emit("Sequência da rota atualizada.")
            }
        }
    }

    /** Exporta todas as entregas + histórico de ganhos pro arquivo que o usuário escolheu salvar. */
    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _backupState.value = BackupState.Working
            try {
                val json = BackupManager.serialize(deliveries.value, history.value)
                withContext(Dispatchers.IO) {
                    getApplication<android.app.Application>().contentResolver.openOutputStream(uri)?.use {
                        it.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Não consegui abrir o arquivo pra escrita")
                }
                _toast.emit("Backup salvo com sucesso.")
                _backupState.value = BackupState.Idle
            } catch (e: Exception) {
                _backupState.value = BackupState.Error(e.message ?: "Não foi possível salvar o backup")
            }
        }
    }

    /**
     * Restaura um backup escolhido pelo usuário. SUBSTITUI todas as entregas e todo o
     * histórico atuais pelo conteúdo do arquivo — por isso a UI deve confirmar com o
     * usuário antes de chamar isso (ação destrutiva e irreversível sobre os dados atuais).
     */
    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            _backupState.value = BackupState.Working
            try {
                val json = withContext(Dispatchers.IO) {
                    getApplication<android.app.Application>().contentResolver.openInputStream(uri)?.use {
                        it.bufferedReader(Charsets.UTF_8).readText()
                    } ?: throw IllegalStateException("Não consegui abrir o arquivo escolhido")
                }
                val payload: BackupPayload = BackupManager.deserialize(json)
                deliveryDao.clearAll()
                historyDao.clearAll()
                if (payload.deliveries.isNotEmpty()) deliveryDao.insertAll(payload.deliveries)
                if (payload.history.isNotEmpty()) historyDao.insertAll(payload.history)
                _backupState.value = BackupState.RestoreSuccess(payload.deliveries.size, payload.history.size)
            } catch (e: Exception) {
                _backupState.value = BackupState.Error(
                    when (e) {
                        is com.google.gson.JsonSyntaxException -> "Esse arquivo não é um backup válido do RotaCerta"
                        else -> e.message ?: "Não foi possível restaurar o backup"
                    }
                )
            }
        }
    }

    fun routeStats(): RouteOptimizer.RouteStats {
        val cfg = config.value
        val origin = cfg.originLat?.let { lat -> cfg.originLng?.let { lng -> LatLng(lat, lng) } }
        val returnPoint = cfg.homeLat?.let { lat -> cfg.homeLng?.let { lng -> LatLng(lat, lng) } } ?: origin
        val pending = deliveries.value.filter { it.status == DeliveryStatus.PENDENTE }
        return RouteOptimizer.computeStats(pending, origin, cfg.vehicle.avgSpeedKmh, cfg.roundTrip, returnPoint)
    }

    // ---------------- Config ----------------

    fun updateConfig(update: (AppConfig) -> AppConfig) {
        viewModelScope.launch { configRepo.update(update) }
    }

    fun setOrigin(address: String) {
        viewModelScope.launch {
            if (address.isBlank()) {
                updateConfig { it.copy(originAddress = "", originLat = null, originLng = null) }
                return@launch
            }
            if (!ensureOnlineOrToast()) return@launch
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

    fun setHomeFromGps() {
        viewModelScope.launch {
            try {
                val location = withContext(Dispatchers.IO) {
                    com.rotacerta.entregador.domain.GpsLocationProvider.getCurrentLocation(getApplication())
                }
                updateConfig {
                    it.copy(
                        homeAddress = "Minha localização atual (GPS)",
                        homeLat = location.latitude,
                        homeLng = location.longitude
                    )
                }
                _toast.emit("Destino final definido pelo GPS")
            } catch (e: SecurityException) {
                _toast.emit("Permita o acesso à localização para usar o GPS")
            } catch (e: Exception) {
                _toast.emit(e.message ?: "Não foi possível obter sua localização")
            }
        }
    }

    // ---------------- Destinos salvos (até 3, buscados por CEP + número) ----------------

    fun addSavedDestination(label: String, cepData: CepResponse, numero: String) {
        viewModelScope.launch {
            val current = config.value.savedDestinations
            if (current.size >= AppConfig.MAX_SAVED_DESTINATIONS) {
                _toast.emit("Você já tem ${AppConfig.MAX_SAVED_DESTINATIONS} destinos salvos. Remova um antes de adicionar outro.")
                return@launch
            }
            val nomeLabel = label.ifBlank { "Destino ${current.size + 1}" }
            val street = cepData.logradouro.orEmpty() + if (numero.isNotBlank()) ", $numero" else ""
            val enderecoCompleto = listOfNotNull(street, cepData.bairro, cepData.localidade?.let { "$it - ${cepData.uf}" }, cepData.cep)
                .filter { it.isNotBlank() }.joinToString(", ")
            if (!ensureOnlineOrToast()) return@launch
            try {
                val geo = GeocodingService.geocode(enderecoCompleto, cepData, numero)
                val novo = SavedDestination(nomeLabel, enderecoCompleto, geo.lat, geo.lng)
                // Tudo numa escrita só (adicionar à lista + selecionar se for o primeiro),
                // pra evitar duas gravações concorrentes se sobrescreverem uma à outra.
                updateConfig { cfg ->
                    val ficaSelecionado = cfg.savedDestinations.isEmpty()
                    cfg.copy(
                        savedDestinations = cfg.savedDestinations + novo,
                        homeAddress = if (ficaSelecionado) novo.address else cfg.homeAddress,
                        homeLat = if (ficaSelecionado) novo.lat else cfg.homeLat,
                        homeLng = if (ficaSelecionado) novo.lng else cfg.homeLng
                    )
                }
                _toast.emit("Destino \"$nomeLabel\" salvo")
            } catch (e: Exception) {
                _toast.emit(e.message ?: "Não foi possível localizar esse endereço")
            }
        }
    }

    fun selectSavedDestination(dest: SavedDestination) {
        updateConfig { it.copy(homeAddress = dest.address, homeLat = dest.lat, homeLng = dest.lng) }
    }

    fun removeSavedDestination(dest: SavedDestination) {
        updateConfig { cfg ->
            val restante = cfg.savedDestinations.filter { it != dest }
            // Se o destino removido era o que estava ativo, limpa a seleção
            if (cfg.homeAddress == dest.address && cfg.homeLat == dest.lat && cfg.homeLng == dest.lng) {
                cfg.copy(savedDestinations = restante, homeAddress = "", homeLat = null, homeLng = null)
            } else {
                cfg.copy(savedDestinations = restante)
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
                val precisaRede = rows.any { it.lat == null || it.lng == null }
                if (precisaRede && !com.rotacerta.entregador.domain.NetworkMonitor.isOnline(getApplication())) {
                    _toast.emit("Sem internet: linhas sem coordenadas na planilha não serão localizadas agora.")
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
