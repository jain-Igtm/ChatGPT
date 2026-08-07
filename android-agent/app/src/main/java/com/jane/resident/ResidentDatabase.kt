package com.jane.resident

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

class ResidentDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE agent_state (
                singleton_id INTEGER PRIMARY KEY CHECK (singleton_id = 1),
                identity_id TEXT NOT NULL,
                mode TEXT NOT NULL,
                current_activity_id TEXT,
                last_wake_at INTEGER,
                last_sleep_at INTEGER,
                wake_reason TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE activity (
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL,
                payload TEXT NOT NULL,
                status TEXT NOT NULL,
                checkpoint TEXT,
                attempts INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE memory (
                id TEXT PRIMARY KEY,
                kind TEXT NOT NULL,
                content TEXT NOT NULL,
                salience REAL NOT NULL DEFAULT 0.5,
                created_at INTEGER NOT NULL,
                last_accessed_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE journal (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                body TEXT NOT NULL,
                visibility TEXT NOT NULL DEFAULT 'private',
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE message (
                id TEXT PRIMARY KEY,
                role TEXT NOT NULL,
                body TEXT NOT NULL,
                attachment_uri TEXT,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE event_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                category TEXT NOT NULL,
                detail TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX activity_status_created_idx ON activity(status, created_at)")
        db.execSQL("CREATE INDEX message_created_idx ON message(created_at)")
        db.execSQL("CREATE INDEX memory_created_idx ON memory(created_at)")

        val now = System.currentTimeMillis()
        db.execSQL(
            """
            INSERT INTO agent_state (
                singleton_id, identity_id, mode, created_at, updated_at
            ) VALUES (1, ?, 'SLEEPING', ?, ?)
            """.trimIndent(),
            arrayOf<Any>(UUID.randomUUID().toString(), now, now),
        )
        db.execSQL(
            "INSERT INTO event_log(category, detail, created_at) VALUES('birth', 'Continuity store created', ?)",
            arrayOf<Any>(now),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw IllegalStateException(
            "No destructive migration is allowed for the resident identity. " +
                "Add an explicit migration from $oldVersion to $newVersion.",
        )
    }

    companion object {
        private const val DATABASE_NAME = "resident.db"
        private const val DATABASE_VERSION = 1
    }
}
