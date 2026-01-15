package com.Arasoftsolutions.tecniapp_ice.update

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.Arasoftsolutions.tecniapp_ice.BuildConfig
import com.Arasoftsolutions.tecniapp_ice.preferences.DataStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class UpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val checker = GithubUpdateChecker(BuildConfig.UPDATE_JSON_URL)
        val dataStore = DataStoreManager.getInstance(applicationContext)

        when (val result = checker.checkForUpdate(BuildConfig.VERSION_CODE)) {
            is UpdateCheckResult.UpdateAvailable -> {
                dataStore.setPendingUpdateInfo(result.info)
                Result.success()
            }
            UpdateCheckResult.UpToDate -> {
                dataStore.setPendingUpdateInfo(null)
                Result.success()
            }
            is UpdateCheckResult.Error -> Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK = "update_check_daily"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
