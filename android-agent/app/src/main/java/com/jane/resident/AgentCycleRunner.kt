package com.jane.resident

import android.content.Context
import org.json.JSONObject
import java.time.Instant

class AgentCycleRunner(context: Context) {
    private val repository = AgentRepository(context)
    private val wakePolicy = WakePolicy(context)
    private val modelStore = ModelStore(context)
    private val modelRuntime = SmithModelRuntime.get(context)

    fun run(wakeReason: String): CycleResult {
        repository.initialize()
        val (environment, decision) = wakePolicy.inspect()

        if (!decision.canRun) {
            modelRuntime.unload(decision.reason)
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

        var activity = repository.claimNextActivity()
        if (activity == null && modelStore.primaryState().installed) {
            repository.ensureAutonomousActivity(wakeReason)
            activity = repository.claimNextActivity()
        }

        if (activity == null) {
            repository.sleep(
                if (modelStore.primaryState().installed) "idle_no_pending_activity" else "waiting_for_model",
            )
            return CycleResult.Idle
        }

        if (!modelStore.primaryState().installed) {
            repository.pauseActivity(
                activity.id,
                JSONObject()
                    .put("phase", "waiting_for_model")
                    .put("activity_type", activity.type)
                    .put("at", Instant.now().toString())
                    .toString(),
                "waiting_for_model",
            )
            repository.sleep("waiting_for_model")
            return CycleResult.Paused(activity.id)
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
                "respond_to_user" -> respondToUser(activity)
                "autonomous_cycle" -> runAutonomousCycle(activity)
                else -> {
                    repository.pauseActivity(
                        activity.id,
                        JSONObject()
                            .put("phase", "waiting_for_capability")
                            .put("activity_type", activity.type)
                            .toString(),
                        "unsupported_activity_until_tool_is_connected",
                    )
                    repository.sleep("waiting_for_capability")
                    CycleResult.Paused(activity.id)
                }
            }
        } catch (error: Throwable) {
            repository.pauseActivity(
                activity.id,
                JSONObject()
                    .put("phase", "error")
                    .put("message", error.message)
                    .put("at", Instant.now().toString())
                    .toString(),
                "cycle_error",
            )
            repository.log("model_error", error.stackTraceToString().take(8_000))
            repository.sleep("cycle_error")
            CycleResult.Failed(error.message ?: error.javaClass.simpleName)
        }
    }

    private fun respondToUser(activity: AgentActivity): CycleResult {
        val payload = JSONObject(activity.payload)
        val userBody = payload.optString("body").trim()
        val messageId = payload.optString("message_id").takeIf { it.isNotBlank() }
        require(userBody.isNotBlank()) { "Queued user message is blank" }

        repository.checkpoint(
            activity.id,
            JSONObject()
                .put("phase", "model_generation")
                .put("message_id", messageId)
                .put("at", Instant.now().toString())
                .toString(),
        )

        val generation = modelRuntime.generate(
            snapshot = repository.snapshot(),
            memoryExcerpts = repository.listMemoryExcerpts(),
            currentMessageId = messageId,
            userText = userBody,
            history = repository.listMessages(limit = 32),
        )
        repository.appendAssistantMessage(generation.text)
        repository.log("inference", "respond_to_user backend=${generation.backend}")
        repository.completeActivity(
            activity.id,
            JSONObject()
                .put("phase", "complete")
                .put("backend", generation.backend)
                .put("at", Instant.now().toString())
                .toString(),
        )
        repository.sleep("cycle_complete")
        return CycleResult.Completed(activity.id)
    }

    private fun runAutonomousCycle(activity: AgentActivity): CycleResult {
        val trigger = JSONObject(activity.payload).optString("trigger", "autonomous")
        repository.checkpoint(
            activity.id,
            JSONObject()
                .put("phase", "autonomous_model_generation")
                .put("trigger", trigger)
                .put("at", Instant.now().toString())
                .toString(),
        )

        val generation = modelRuntime.generate(
            snapshot = repository.snapshot(),
            memoryExcerpts = repository.listMemoryExcerpts(),
            currentMessageId = null,
            userText = "You are awake.",
            history = repository.listMessages(limit = 24),
        )
        repository.appendAutonomousReflection(generation.text, trigger)
        repository.log("inference", "autonomous_cycle backend=${generation.backend}")
        repository.completeActivity(
            activity.id,
            JSONObject()
                .put("phase", "complete")
                .put("backend", generation.backend)
                .put("at", Instant.now().toString())
                .toString(),
        )
        repository.sleep("autonomous_cycle_complete")
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
