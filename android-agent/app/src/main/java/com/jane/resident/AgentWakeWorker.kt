package com.jane.resident

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class AgentWakeWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : Worker(appContext, workerParameters) {

    override fun doWork(): Result {
        val wakeReason = inputData.getString(KEY_WAKE_REASON) ?: "scheduled_wake"
        return when (AgentCycleRunner(applicationContext).run(wakeReason)) {
            is CycleResult.Failed -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        const val KEY_WAKE_REASON = "wake_reason"
    }
}
