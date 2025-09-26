package com.Arasoftsolutions.tecniapp_ice.ui.averias

import com.Arasoftsolutions.tecniapp_ice.BuildConfig  // 👈 usa el BuildConfig del app
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import java.util.concurrent.TimeUnit

// ---------- MODELOS ----------
@JsonClass(generateAdapter = true)
data class IceAveria(
  @Json(name="region") val region: String?,
  @Json(name="provincia") val provincia: Int?,
  @Json(name="agencia") val agencia: String?,
  @Json(name="nombreAgencia") val nombreAgencia: String?,
  @Json(name="nise") val nise: String?,
  @Json(name="noCaso") val noCaso: String?,
  @Json(name="latitud") val latitud: String?,
  @Json(name="longitud") val longitud: String?,
  @Json(name="causa") val causa: String?,
  @Json(name="observaciones") val observaciones: String?,
  @Json(name="fechaInicio") val fechaInicio: String?,
  @Json(name="manualSalidaVehiculo") val manualSalidaVehiculo: String?,
  @Json(name="horaCierreInterrupcion") val horaCierreInterrupcion: String?,
  @Json(name="clientesAfectados") val clientesAfectados: String?,
  @Json(name="idEstadoAve") val idEstadoAve: Int?,
  @Json(name="idEstadoAranda") val idEstadoAranda: Int?,
  @Json(name="estado") val estado: String?
)

@JsonClass(generateAdapter = true)
data class IceEnvelope(
  @Json(name = "data") val data: List<IceAveria>?,
  @Json(name = "items") val items: List<IceAveria>?,
  @Json(name = "averias") val averiasLower: List<IceAveria>?,
  @Json(name = "Averias") val averiasUpper: List<IceAveria>?,
  @Json(name = "respuesta") val respuesta: List<IceAveria>?,   // 👈 NUEVO
) {
  fun payload(): List<IceAveria> =
    data ?: items ?: averiasLower ?: averiasUpper ?: respuesta ?: emptyList()
}

// ---------- RETROFIT ----------
interface IceService {
  @GET("AveriasAranda/")
  suspend fun getAverias(@Header("Authorization") bearer: String? = null): IceEnvelope
}

object IceApi {
  // Con codegen NO necesitas KotlinJsonAdapterFactory
  private val moshi: Moshi = Moshi.Builder().build()

  private val authStripper = Interceptor { ch ->
    val req = ch.request()
    val clean = if (req.header("Authorization").isNullOrBlank())
      req.newBuilder().removeHeader("Authorization").build()
    else req
    ch.proceed(clean)
  }

 private val logging = HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BODY
}


  private val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .writeTimeout(20, TimeUnit.SECONDS)
    .addInterceptor(authStripper)
    .addInterceptor(logging)
    .build()

  val service: IceService by lazy {
    Retrofit.Builder()
      .baseUrl(BuildConfig.ICE_BASE_URL)   // tu base URL definida en build.gradle.kts
      .client(client)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
      .create(IceService::class.java)
  }
}
