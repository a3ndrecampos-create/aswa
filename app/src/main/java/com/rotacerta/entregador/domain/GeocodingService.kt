package com.rotacerta.entregador.domain

import com.rotacerta.entregador.network.CepResponse
import com.rotacerta.entregador.network.NetworkModule
import kotlinx.coroutines.delay

data class GeoResult(val lat: Double, val lng: Double, val approx: Boolean = false)

/**
 * Reproduz a estratégia em camadas do app original:
 * 1) busca estruturada usando os dados do CEP (mais confiável)
 * 2) texto completo do endereço digitado
 * 3) rua + cidade/UF do CEP (sem bairro)
 * 4) rua (+número) + cidade/UF extraída do texto
 * 5) apenas cidade/UF (localização aproximada)
 */
object GeocodingService {

    suspend fun lookupCep(cep: String): CepResponse {
        val digits = cep.filter { it.isDigit() }
        require(digits.length == 8) { "CEP inválido" }
        val result = NetworkModule.viaCep.lookup(digits)
        if (result.erro == true) throw IllegalStateException("CEP não encontrado")
        return result
    }

    suspend fun geocode(address: String, cepData: CepResponse? = null, numero: String = ""): GeoResult {
        // O Nominatim pede no máximo ~1 req/segundo. Isso já era respeitado *entre*
        // endereços (via politeDelay() no import em lote), mas não *dentro* da
        // resolução de um único endereço problemático: as camadas 1-5 abaixo podiam
        // disparar várias chamadas em sequência sem pausa, arriscando bloqueio
        // temporário em lotes com muitos endereços malformatados. `nominatimCall`
        // aplica o intervalo mínimo antes de toda chamada, exceto a primeira.
        var madeFirstCall = false
        suspend fun <T> nominatimCall(block: suspend () -> T): T {
            if (madeFirstCall) delay(1000) else madeFirstCall = true
            return block()
        }

        // 1) estruturado via CEP
        if (cepData != null) {
            runCatching {
                val street = cepData.logradouro?.let { if (numero.isNotBlank()) "$it, $numero" else it }
                val res = nominatimCall {
                    NetworkModule.nominatim.searchStructured(
                        street = street,
                        city = cepData.localidade,
                        state = cepData.uf,
                        postalCode = cepData.cep?.filter { it.isDigit() }
                    )
                }
                if (res.isNotEmpty()) return GeoResult(res[0].lat.toDouble(), res[0].lon.toDouble())
            }
        }

        // 2) texto completo
        runCatching {
            val res = nominatimCall { NetworkModule.nominatim.search("$address, Brasil") }
            if (res.isNotEmpty()) return GeoResult(res[0].lat.toDouble(), res[0].lon.toDouble())
        }

        // 3) rua + cidade/UF do CEP (sem bairro)
        if (cepData != null) {
            runCatching {
                val q = listOfNotNull(cepData.logradouro, cepData.localidade?.let { "$it - ${cepData.uf}" }, "Brasil")
                    .joinToString(", ")
                val res = nominatimCall { NetworkModule.nominatim.search(q) }
                if (res.isNotEmpty()) return GeoResult(res[0].lat.toDouble(), res[0].lon.toDouble())
            }
        }

        // 4) rua (+número) + cidade/UF extraída do texto livre
        val parts = address.replace(", Brasil", "").split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size > 2) {
            runCatching {
                var streetPart = parts[0]
                if (parts.size > 1 && parts[1].matches(Regex("^\\d+[a-zA-Z]?$"))) {
                    streetPart += ", ${parts[1]}"
                }
                val cityState = normalizeCityState(parts.last())
                val res = nominatimCall { NetworkModule.nominatim.search("$streetPart, $cityState, Brasil") }
                if (res.isNotEmpty()) return GeoResult(res[0].lat.toDouble(), res[0].lon.toDouble())
            }
        }

        // 5) último recurso: cidade/UF aproximada
        val cityState = cepData?.let { "${it.localidade} - ${it.uf}" }
            ?: parts.lastOrNull()?.let { normalizeCityState(it) }
        if (cityState != null) {
            runCatching {
                val res = nominatimCall { NetworkModule.nominatim.search("$cityState, Brasil") }
                if (res.isNotEmpty()) return GeoResult(res[0].lat.toDouble(), res[0].lon.toDouble(), approx = true)
            }
        }

        throw IllegalStateException("Endereço não encontrado: $address")
    }

    private fun normalizeCityState(s: String): String =
        s.replaceFirst(Regex("/(?=[A-Za-z]{2}\\b)"), " - ")

    /** Respeita o limite de uso do Nominatim (~1 requisição/segundo). */
    suspend fun politeDelay() = delay(1000)
}
