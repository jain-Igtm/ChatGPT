package com.jane.resident

import android.content.Context
import org.json.JSONObject
import java.time.Instant

class AgentCycleRunner(context: Context) {
    private val repository = AgentRepository(context)
    private val wakePolicy = WakePolicy(context)

    fun run(wakeReason: String): CycleResult {
        repository.initialize()
        val (environment, decision) = wakePolicy.inspect()

        if (!decision.canRun) {
            val snapshot = repository.snapshot()
            snapshot.currentActivityId?.let { activityId ->
                repository.pauseActivity(
                    activityId = activityId,
                    checkpoint = snapshot.currentCheckpoint ?: "{\"phase\":\"resource_pause\"}",
                    reason = decision.reason,
                )
            }
            repository.sleep(decision.reason)
            return CycleResult.Slept(decision.reason)
        }

        repository.wake(wakeReason)
        repository.log(
            "environment",
            "battery=${environment.batteryPercent}, charging=${environment.charging}, " +
                "thermal=${environment.thermalStatus}",
        )

        val activity = repository.claimNextActivity()
        if (activity == null) {
            repository.sleep("idle_no_pending_activity")
            return CycleResult.Idle
        }

        val claimedCheckpoint = JSONObject()
            .put("phase", "claimed")
            .put("activity_type", activity.type)
            .put("resumed_from", activity.checkpoint)
            .put("at", Instant.now().toString())
            .toString()
        repository.checkpoint(activity.id, claimedCheckpoint)

        return try {
            when (activity.type) {
                "respond_to_user" -> completePlaceholderResponse(activity)
                else -> {
                    repository.pauseActivity(
                        activity.id,
                        JSONObject()
                            .put("phase", "waiting_for_capability")
                            .put("activity_type", activity.type)
                            .toString(),
                        "unsupported_activity_until_model_or_tool_is_connected",
                    )
                    CycleResult.Paused(activity.id)
                }
            }
        } catch (error: Exception) {
            repository.pauseActivity(
                activity.id,
                JSONObject()
                    .put("phase", "error")
                    .put("message", error.message)
                    .toString(),
                "cycle_error",
            )
            repository.sleep("cycle_error")
            CycleResult.Failed(error.message ?: error.javaClass.simpleName)
        }
    }

    private fun completePlaceholderResponse(activity: AgentActivity): CycleResult {
        val payload = JSONObject(activity.payload)
        val userBody = payload.optString("body")
        val snapshot = repository.snapshot()
        val response = buildString {
            append("I preserved your message and resumed this activity from durable storage. ")
            append("My continuity identity is ")
            append(snapshot.identityId.take(8))
            append(". ")
            append("The language model is not connected yet, so this is the continuity core ")
            append("confirming that sleep, wake, memory, and resumption are working. ")
            if (userBody.isNotBlank()) {
                append("Your message remains stored locally.")
            }
        }
        repository.appendAssistantMessage(response)
        repository.completeActivity(
            activity.id,
            JSONObject()
                .put("phase", "complete")
                .put("result", "placeholder_response_persisted")
                .put("at", Instant.now().toString())
                .toString(),
        )
        repository.sleep("cycle_complete")
        return CycleResult.Completed(activity.id)
    }
}

sealed interface CycleResult {
    data class Completed(val activityId: String) : CycleResult
    data class Paused(val activityId: String) : CycleResult
    data class Slept(val reason: String) : CycleResult
    data class Failed(val reason: String) : CycleResult
    data object Idle : CycleResult
}
