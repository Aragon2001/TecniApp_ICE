package com.Arasoftsolutions.tecniapp_ice.pm.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

object PmSyncScheduler {
    fun enqueue(context: Context, regionKey: String, subregionKey: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<PmSyncWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    PmSyncWorker.KEY_REGION to regionKey,
                    PmSyncWorker.KEY_SUBREGION to subregionKey
                )
            )
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    private const val WORK_NAME = "pm_sync_work"
}
