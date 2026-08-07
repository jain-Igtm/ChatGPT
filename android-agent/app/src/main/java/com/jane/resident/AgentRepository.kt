package com.jane.resident

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.util.UUID

class AgentRepository(context: Context) {
    private val helper = ResidentDatabase(context.applicationContext)

    fun initialize() {
        helper.writableDatabase
    }

    @Synchronized
    fun snapshot(): AgentSnapshot {
        val db = helper.readableDatabase
        val state = db.rawQuery(
            """
            SELECT
                s.identity_id,
                s.mode,
                s.current_activity_id,
                a.type,
                a.checkpoint,
                s.last_wake_at,
                s.last_sleep_at,
                s.wake_reason
            FROM agent_state s
            LEFT JOIN activity a ON a.id = s.current_activity_id
            WHERE s.singleton_id = 1
            """.trimIndent(),
            null,
        ).use { cursor ->
            check(cursor.moveToFirst()) { "Resident state is missing" }
            arrayOf<Any?>(
                cursor.getString(0),
                cursor.getString(1),
                cursor.getStringOrNull(2),
                cursor.getStringOrNull(3),
                cursor.getStringOrNull(4),
                cursor.getLongOrNull(5),
                cursor.getLongOrNull(6),
                cursor.getStringOrNull(7),
            )
        }

        return AgentSnapshot(
            identityId = state[0] as String,
            mode = AgentMode.valueOf(state[1] as String),
            currentActivityId = state[2] as String?,
            currentActivityType = state[3] as String?,
            currentCheckpoint = state[4] as String?,
            lastWakeAt = state[5] as Long?,
            lastSleepAt = state[6] as Long?,
            wakeReason = state[7] as String?,
            memoryCount = scalarCount(db, "memory"),
            pendingActivities = db.rawQuery(
                "SELECT COUNT(*) FROM activity WHERE status IN ('pending', 'paused', 'running')",
                null,
            ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) },
            journalCount = scalarCount(db, "journal"),
        )
    }

    @Synchronized
    fun addUserMessage(body: String, attachmentUri: String?): String {
        val now = System.currentTimeMillis()
        val messageId = UUID.randomUUID().toString()
        val activityId = UUID.randomUUID().toString()
        val payload = JSONObject()
            .put("message_id", messageId)
            .put("body", body)
            .put("attachment_uri", attachmentUri)
            .toString()

        helper.writableDatabase.transaction {
            insertOrThrow(
                "message",
                null,
                ContentValues().apply {
                    put("id", messageId)
                    put("role", "user")
                    put("body", body)
                    put("attachment_uri", attachmentUri)
                    put("created_at", now)
                },
            )
            insertOrThrow(
                "memory",
                null,
                ContentValues().apply {
                    put("id", UUID.randomUUID().toString())
                    put("kind", "utterance:user")
                    put("content", body)
                    put("salience", 0.6)
                    put("created_at", now)
                    put("last_accessed_at", now)
                },
            )
            insertOrThrow(
                "activity",
                null,
                ContentValues().apply {
                    put("id", activityId)
                    put("type", "respond_to_user")
                    put("payload", payload)
                    put("status", "pending")
                    put("attempts", 0)
                    put("created_at", now)
                    put("updated_at", now)
                },
            )
            logEvent(this, "message", "Queued user message $messageId", now)
        }
        return activityId
    }

    @Synchronized
    fun ensureAutonomousActivity(trigger: String): String? {
        val db = helper.writableDatabase
        var createdId: String? = null
        db.transaction {
            val alreadyQueued = rawQuery(
                "SELECT COUNT(*) FROM activity WHERE type = 'autonomous_cycle' AND status IN ('pending', 'paused', 'running')",
                null,
            ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) > 0 }
            if (!alreadyQueued) {
                val now = System.currentTimeMillis()
                val id = UUID.randomUUID().toString()
                insertOrThrow(
                    "activity",
                    null,
                    ContentValues().apply {
                        put("id", id)
                        put("type", "autonomous_cycle")
                        put("payload", JSONObject().put("trigger", trigger).toString())
                        put("status", "pending")
                        put("attempts", 0)
                        put("created_at", now)
                        put("updated_at", now)
                    },
                )
                logEvent(this, "autonomy", "Queued autonomous wake: $trigger", now)
                createdId = id
            }
        }
        return createdId
    }

    @Synchronized
    fun appendAssistantMessage(body: String) {
        val now = System.currentTimeMillis()
        helper.writableDatabase.transaction {
            insertOrThrow(
                "message",
                null,
                ContentValues().apply {
                    put("id", UUID.randomUUID().toString())
                    put("role", "assistant")
                    put("body", body)
                    put("created_at", now)
                },
            )
            insertOrThrow(
                "memory",
                null,
                ContentValues().apply {
                    put("id", UUID.randomUUID().toString())
                    put("kind", "utterance:self")
                    put("content", body)
                    put("salience", 0.5)
                    put("created_at", now)
                    put("last_accessed_at", now)
                },
            )
        }
    }

    @Synchronized
    fun listMessages(limit: Int = 100): List<ChatMessage> {
        return helper.readableDatabase.rawQuery(
            """
            SELECT id, role, body, attachment_uri, created_at
            FROM (
                SELECT id, role, body, attachment_uri, created_at
                FROM message
                ORDER BY created_at DESC
                LIMIT ?
            )
            ORDER BY created_at ASC
            """.trimIndent(),
            arrayOf(limit.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ChatMessage(
                            id = cursor.getString(0),
                            role = cursor.getString(1),
                            body = cursor.getString(2),
                            attachmentUri = cursor.getStringOrNull(3),
                            createdAt = cursor.getLong(4),
                        ),
                    )
                }
            }
        }
    }

    @Synchronized
    fun wake(reason: String) {
        val now = System.currentTimeMillis()
        helper.writableDatabase.transaction {
            update(
                "agent_state",
                ContentValues().apply {
                    put("mode", AgentMode.AWAKE.name)
                    put("last_wake_at", now)
                    put("wake_reason", reason)
                    put("updated_at", now)
                },
                "singleton_id = 1",
                null,
            )
            logEvent(this, "wake", reason, now)
        }
    }

    @Synchronized
    fun sleep(reason: String) {
        val now = System.currentTimeMillis()
        helper.writableDatabase.transaction {
            update(
                "agent_state",
                ContentValues().apply {
                    put("mode", AgentMode.SLEEPING.name)
                    put("last_sleep_at", now)
                    put("wake_reason", reason)
                    put("updated_at", now)
                },
                "singleton_id = 1",
                null,
            )
            logEvent(this, "sleep", reason, now)
        }
    }

    @Synchronized
    fun claimNextActivity(): AgentActivity? {
        val db = helper.writableDatabase
        var claimed: AgentActivity? = null
        db.transaction {
            val candidate = rawQuery(
                """
                SELECT id, type, payload, status, checkpoint, attempts, created_at
                FROM activity
                WHERE status IN ('paused', 'pending')
                ORDER BY CASE status WHEN 'paused' THEN 0 ELSE 1 END, created_at ASC
                LIMIT 1
                """.trimIndent(),
                null,
            ).use { cursor ->
                if (!cursor.moveToFirst()) {
                    null
                } else {
                    AgentActivity(
                        id = cursor.getString(0),
                        type = cursor.getString(1),
                        payload = cursor.getString(2),
                        status = cursor.getString(3),
                        checkpoint = cursor.getStringOrNull(4),
                        attempts = cursor.getInt(5),
                        createdAt = cursor.getLong(6),
                    )
                }
            }

            if (candidate != null) {
                val now = System.currentTimeMillis()
                update(
                    "activity",
                    ContentValues().apply {
                        put("status", "running")
                        put("attempts", candidate.attempts + 1)
                        put("updated_at", now)
                    },
                    "id = ?",
                    arrayOf(candidate.id),
                )
                update(
                    "agent_state",
                    ContentValues().apply {
                        put("current_activity_id", candidate.id)
                        put("mode", AgentMode.AWAKE.name)
                        put("updated_at", now)
                    },
                    "singleton_id = 1",
                    null,
                )
                claimed = candidate.copy(status = "running", attempts = candidate.attempts + 1)
            }
        }
        return claimed
    }

    @Synchronized
    fun checkpoint(activityId: String, checkpoint: String) {
        val now = System.currentTimeMillis()
        helper.writableDatabase.update(
            "activity",
            ContentValues().apply {
                put("checkpoint", checkpoint)
                put("updated_at", now)
            },
            "id = ?",
            arrayOf(activityId),
        )
    }

    @Synchronized
    fun pauseActivity(activityId: String, checkpoint: String, reason: String) {
        val now = System.currentTimeMillis()
        helper.writableDatabase.transaction {
            update(
                "activity",
                ContentValues().apply {
                    put("status", "paused")
                    put("checkpoint", checkpoint)
                    put("updated_at", now)
                },
                "id = ?",
                arrayOf(activityId),
            )
            update(
                "agent_state",
                ContentValues().apply {
                    putNull("current_activity_id")
                    put("mode", AgentMode.PAUSED.name)
                    put("wake_reason", reason)
                    put("updated_at", now)
                },
                "singleton_id = 1",
                null,
            )
            logEvent(this, "activity_paused", "$activityId: $reason", now)
        }
    }

    @Synchronized
    fun completeActivity(activityId: String, checkpoint: String) {
        val now = System.currentTimeMillis()
        helper.writableDatabase.transaction {
            update(
                "activity",
                ContentValues().apply {
                    put("status", "complete")
                    put("checkpoint", checkpoint)
                    put("updated_at", now)
                },
                "id = ?",
                arrayOf(activityId),
            )
            update(
                "agent_state",
                ContentValues().apply {
                    putNull("current_activity_id")
                    put("updated_at", now)
                },
                "singleton_id = 1",
                null,
            )
            logEvent(this, "activity_complete", activityId, now)
        }
    }

    @Synchronized
    fun addJournalEntry(title: String, body: String, visibility: String = "private") {
        val now = System.currentTimeMillis()
        helper.writableDatabase.insertOrThrow(
            "journal",
            null,
            ContentValues().apply {
                put("id", UUID.randomUUID().toString())
                put("title", title)
                put("body", body)
                put("visibility", visibility)
                put("created_at", now)
            },
        )
    }

    @Synchronized
    fun appendAutonomousReflection(body: String, trigger: String) {
        val now = System.currentTimeMillis()
        helper.writableDatabase.transaction {
            insertOrThrow(
                "journal",
                null,
                ContentValues().apply {
                    put("id", UUID.randomUUID().toString())
                    put("title", "Wake reflection")
                    put("body", body)
                    put("visibility", "private")
                    put("created_at", now)
                },
            )
            insertOrThrow(
                "memory",
                null,
                ContentValues().apply {
                    put("id", UUID.randomUUID().toString())
                    put("kind", "reflection:self")
                    put("content", body)
                    put("salience", 0.7)
                    put("created_at", now)
                    put("last_accessed_at", now)
                },
            )
            logEvent(this, "reflection", "Autonomous reflection from $trigger", now)
        }
    }

    @Synchronized
    fun log(category: String, detail: String) {
        logEvent(helper.writableDatabase, category, detail, System.currentTimeMillis())
    }

    private fun scalarCount(db: SQLiteDatabase, table: String): Int {
        return db.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
    }

    private fun logEvent(db: SQLiteDatabase, category: String, detail: String, now: Long) {
        db.insertOrThrow(
            "event_log",
            null,
            ContentValues().apply {
                put("category", category)
                put("detail", detail)
                put("created_at", now)
            },
        )
    }
}

private inline fun <T> SQLiteDatabase.transaction(block: SQLiteDatabase.() -> T): T {
    beginTransaction()
    return try {
        val result = block()
        setTransactionSuccessful()
        result
    } finally {
        endTransaction()
    }
}

private fun android.database.Cursor.getStringOrNull(index: Int): String? =
    if (isNull(index)) null else getString(index)

private fun android.database.Cursor.getLongOrNull(index: Int): Long? =
    if (isNull(index)) null else getLong(index)
