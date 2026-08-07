package com.jane.resident

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class AgentScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun ensureAutonomousWake() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<AgentWakeWorker>(
            15,
            TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .setInputData(
                workDataOf(AgentWakeWorker.KEY_WAKE_REASON to "autonomous_heartbeat"),
            )
            .addTag(TAG_AGENT_WAKE)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_HEARTBEAT,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun wakeNow(reason: String) {
        val request = OneTimeWorkRequestBuilder<AgentWakeWorker>()
            .setInputData(workDataOf(AgentWakeWorker.KEY_WAKE_REASON to reason))
            .addTag(TAG_AGENT_WAKE)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_IMMEDIATE_WAKE,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun scheduleWake(delayMillis: Long, reason: String) {
        val request = OneTimeWorkRequestBuilder<AgentWakeWorker>()
            .setInitialDelay(delayMillis.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(AgentWakeWorker.KEY_WAKE_REASON to reason))
            .addTag(TAG_AGENT_WAKE)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_SELF_SCHEDULED_WAKE,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        private const val UNIQUE_HEARTBEAT = "resident-autonomous-heartbeat"
        private const val UNIQUE_IMMEDIATE_WAKE = "resident-immediate-wake"
        private const val UNIQUE_SELF_SCHEDULED_WAKE = "resident-self-scheduled-wake"
        private const val TAG_AGENT_WAKE = "resident-agent-wake"
    }
}
