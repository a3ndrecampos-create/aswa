package com.rotacerta.entregador.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

data class CepResponse(
    val cep: String? = null,
    val logradouro: String? = null,
    val bairro: String? = null,
    val localidade: String? = null,
    val uf: String? = null,
    val erro: Boolean? = null
)

interface ViaCepApi {
    @GET("ws/{cep}/json/")
    suspend fun lookup(@Path("cep") cep: String): CepResponse
}

data class NominatimResult(val lat: String, val lon: String)

interface NominatimApi {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 1,
        @Query("countrycodes") countryCodes: String = "br",
        @Header("Accept-Language") lang: String = "pt-BR"
    ): List<NominatimResult>

    @GET("search")
    suspend fun searchStructured(
        @Query("street") street: String? = null,
        @Query("city") city: String? = null,
        @Query("state") state: String? = null,
        @Query("postalcode") postalCode: String? = null,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 1,
        @Query("countrycodes") countryCodes: String = "br",
        @Header("Accept-Language") lang: String = "pt-BR"
    ): List<NominatimResult>
}

object NetworkModule {
    // ViaCEP: consulta gratuita de endereço a partir do CEP (sem necessidade de chave)
    val viaCep: ViaCepApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://viacep.com.br/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ViaCepApi::class.java)
    }

    // Nominatim (OpenStreetMap): geocodificação gratuita.
    // Importante: respeitar o limite de uso (~1 req/s) e enviar um User-Agent identificável,
    // conforme a política de uso do serviço.
    val nominatim: NominatimApi by lazy {
        val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "RotaCerta-Android/1.0 (contato@rotacerta.app)")
                    .build()
                chain.proceed(req)
            }
            .build()
        Retrofit.Builder()
            .baseUrl("https://nominatim.openstreetmap.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NominatimApi::class.java)
    }
}
