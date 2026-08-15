package com.hypershare.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ChatDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE_MESSAGES)
        db.execSQL(CREATE_TABLE_PEERS)
        db.execSQL(CREATE_TABLE_GROUPS)
        db.execSQL(CREATE_TABLE_GROUP_MEMBERS)
        db.execSQL(CREATE_TABLE_GROUP_MESSAGES)
        db.execSQL(CREATE_TABLE_GROUP_RECEIPTS)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE peers ADD COLUMN is_trusted INTEGER DEFAULT 0")
                db.execSQL("ALTER TABLE peers ADD COLUMN public_key TEXT")
            } catch (_: Exception) { }
        }
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE peers ADD COLUMN has_peer_accepted INTEGER DEFAULT 0")
            } catch (_: Exception) { }
        }
        if (oldVersion < 4) {
            try {
                db.execSQL(CREATE_TABLE_GROUPS)
                db.execSQL(CREATE_TABLE_GROUP_MEMBERS)
                db.execSQL(CREATE_TABLE_GROUP_MESSAGES)
            } catch (_: Exception) { }
        }
        if (oldVersion < 5) {
            try {
                db.execSQL("ALTER TABLE groups ADD COLUMN join_method TEXT NOT NULL DEFAULT 'DIRECT'")
            } catch (_: Exception) { }
        }
        if (oldVersion < 6) {
            try {
                db.execSQL(CREATE_TABLE_GROUP_RECEIPTS)
            } catch (_: Exception) { }
        }
    }

    companion object {
        const val DATABASE_NAME = "hypershare_chat.db"
        const val DATABASE_VERSION = 6

        private const val CREATE_TABLE_MESSAGES = """
            CREATE TABLE messages (
                id TEXT PRIMARY KEY,
                peer_id TEXT NOT NULL,
                text TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                is_outgoing INTEGER NOT NULL,
                delivery_status TEXT NOT NULL
            );
        """

        private const val CREATE_TABLE_PEERS = """
            CREATE TABLE peers (
                peer_id TEXT PRIMARY KEY,
                display_name TEXT NOT NULL,
                last_seen INTEGER NOT NULL,
                is_trusted INTEGER DEFAULT 0,
                has_peer_accepted INTEGER DEFAULT 0,
                public_key TEXT
            );
        """

        private const val CREATE_TABLE_GROUPS = """
            CREATE TABLE groups (
                group_id TEXT PRIMARY KEY,
                group_name TEXT NOT NULL,
                admin_user_id TEXT NOT NULL,
                group_type TEXT NOT NULL,
                is_active INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                join_method TEXT NOT NULL DEFAULT 'DIRECT'
            );
        """

        private const val CREATE_TABLE_GROUP_MEMBERS = """
            CREATE TABLE group_members (
                group_id TEXT NOT NULL,
                user_id TEXT NOT NULL,
                display_name TEXT NOT NULL,
                public_key TEXT,
                role TEXT NOT NULL,
                joined_at INTEGER NOT NULL,
                PRIMARY KEY (group_id, user_id)
            );
        """

        private const val CREATE_TABLE_GROUP_MESSAGES = """
            CREATE TABLE group_messages (
                id TEXT PRIMARY KEY,
                group_id TEXT NOT NULL,
                sender_id TEXT NOT NULL,
                sender_display_name TEXT NOT NULL,
                text TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                delivery_status TEXT NOT NULL
            );
        """

        private const val CREATE_TABLE_GROUP_RECEIPTS = """
            CREATE TABLE group_message_receipts (
                message_id TEXT NOT NULL,
                user_id TEXT NOT NULL,
                status TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY (message_id, user_id)
            );
        """
    }
}
