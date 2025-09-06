package com.Arasoftsolutions.tecniapp_ice.ui.averias

import com.Arasoftsolutions.tecniapp_ice.BuildConfig

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import java.util.concurrent.TimeUnit

// --------- MODELO DEVUELTO POR /AveriasAranda/ ---------
data class IceAveria(
    @Json(name = "region") val region: String?,
    @Json(name = "provincia") val provincia: Int?,
    @Json(name = "agencia") val agencia: String?,
    @Json(name = "nombreAgencia") val nombreAgencia: String?,
    @Json(name = "nise") val nise: String?,
    @Json(name = "noCaso") val noCaso: String?, // PK
    @Json(name = "latitud") val latitud: String?,
    @Json(name = "longitud") val longitud: String?,
    @Json(name = "causa") val causa: String?,
    @Json(name = "observaciones") val observaciones: String?,
    @Json(name = "fechaInicio") val fechaInicio: String?,
    @Json(name = "manualSalidaVehiculo") val manualSalidaVehiculo: String?,
    @Json(name = "horaCierreInterrupcion") val horaCierreInterrupcion: String?,
    @Json(name = "clientesAfectados") val clientesAfectados: String?,
    @Json(name = "idEstadoAve") val idEstadoAve: Int?,
    @Json(name = "idEstadoAranda") val idEstadoAranda: Int?,
    @Json(name = "estado") val estado: String?
)

typealias IceResp = List<IceAveria>

// --------- INTERFAZ RETROFIT ---------
interface IceService {
    @GET("AveriasAranda/")
    suspend fun getAverias(
        @Header("Authorization") bearer: String? = null // "Bearer <token>" o null/"" si es público
    ): IceResp
}

// --------- CLIENTE RETROFIT/OKHTTP ---------
object IceApi {

    // Moshi para JSON
    private val moshi: Moshi = Moshi.Builder().build()

    // Interceptor que elimina Authorization si viene vacío → evita 401 por "Authorization: "
    private val cleanAuth: Interceptor = Interceptor { chain ->
        val req = chain.request()
        val auth = req.header("Authorization")
        val newReq = if (auth.isNullOrBlank()) {
            req.newBuilder().removeHeader("Authorization").build()
        } else req
        chain.proceed(newReq)
    }

    // Logging BASIC (si quieres BODY, cámbialo en debug)
    private val httpLogging: HttpLoggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    // OkHttp con timeouts
    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(cleanAuth)
        .addInterceptor(httpLogging)
        .build()

    // Retrofit compartido
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.ICE_BASE_URL) // definido en build.gradle (defaultConfig)
        .client(okHttp)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    // Servicio listo (evita `by lazy` para no mezclar sobrecargas)
    val service: IceService = retrofit.create(IceService::class.java)
}
