package com.hypershare.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ChatDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE_MESSAGES)
        db.execSQL(CREATE_TABLE_PEERS)
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
    }

    companion object {
        const val DATABASE_NAME = "hypershare_chat.db"
        const val DATABASE_VERSION = 3

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
    }
}
