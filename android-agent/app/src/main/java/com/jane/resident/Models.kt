package com.jane.resident

enum class AgentMode {
    SLEEPING,
    WAKING,
    AWAKE,
    PAUSED,
    ERROR,
}

data class AgentSnapshot(
    val identityId: String,
    val mode: AgentMode,
    val currentActivityId: String?,
    val currentActivityType: String?,
    val currentCheckpoint: String?,
    val lastWakeAt: Long?,
    val lastSleepAt: Long?,
    val wakeReason: String?,
    val memoryCount: Int,
    val pendingActivities: Int,
    val journalCount: Int,
)

data class ChatMessage(
    val id: String,
    val role: String,
    val body: String,
    val attachmentUri: String?,
    val createdAt: Long,
)

data class AgentActivity(
    val id: String,
    val type: String,
    val payload: String,
    val status: String,
    val checkpoint: String?,
    val attempts: Int,
    val createdAt: Long,
)

data class WakeEnvironment(
    val batteryPercent: Int,
    val charging: Boolean,
    val thermalStatus: Int,
)

data class WakeDecision(
    val canRun: Boolean,
    val reason: String,
)
