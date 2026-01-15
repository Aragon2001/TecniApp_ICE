package com.Arasoftsolutions.tecniapp_ice.update

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

sealed class UpdateCheckResult {
    data class UpdateAvailable(val info: UpdateInfo) : UpdateCheckResult()
    object UpToDate : UpdateCheckResult()
    data class Error(val throwable: Throwable?) : UpdateCheckResult()
}

interface UpdateChecker {
    suspend fun checkForUpdate(currentVersionCode: Int): UpdateCheckResult
}

class GithubUpdateChecker(
    private val updateJsonUrl: String,
    private val okHttpClient: OkHttpClient = OkHttpClient(),
    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
) : UpdateChecker {

    private val adapter = moshi.adapter(UpdateInfo::class.java)

    override suspend fun checkForUpdate(currentVersionCode: Int): UpdateCheckResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(updateJsonUrl)
            .get()
            .build()

        val response = runCatching { okHttpClient.newCall(request).execute() }.getOrElse { error ->
            Log.e(TAG, "Error consultando update.json", error)
            return@withContext UpdateCheckResult.Error(error)
        }

        response.use { res ->
            if (!res.isSuccessful) {
                Log.e(TAG, "Respuesta no exitosa: ${res.code}")
                return@withContext UpdateCheckResult.Error(null)
            }

            val body = res.body?.string().orEmpty()
            val info = runCatching { adapter.fromJson(body) }.getOrElse { error ->
                Log.e(TAG, "Error parseando update.json", error)
                null
            } ?: return@withContext UpdateCheckResult.Error(null)

            if (info.isNewerThan(currentVersionCode)) {
                UpdateCheckResult.UpdateAvailable(info)
            } else {
                UpdateCheckResult.UpToDate
            }
        }
    }

    companion object {
        private const val TAG = "GithubUpdateChecker"
    }
}
