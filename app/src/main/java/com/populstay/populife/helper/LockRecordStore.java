package com.populstay.populife.helper;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** 独立私有数据库，不随退出登录清除，也不改写旧记录的账号或设备 ID。 */
public final class LockRecordStore extends SQLiteOpenHelper implements LockRecordOutbox.Store {
    public LockRecordStore(Context context) { this(context, "lock_record_outbox.db"); }

    public LockRecordStore(Context context, String name) { super(context, name, null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE pending_records (id TEXT PRIMARY KEY, user_id TEXT NOT NULL, "
                + "server TEXT NOT NULL, endpoint TEXT NOT NULL, payload TEXT NOT NULL, "
                + "created_at INTEGER NOT NULL, attempts INTEGER NOT NULL, next_attempt_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX pending_record_scope ON pending_records(user_id, server, next_attempt_at)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Missing outbox migration");
    }

    @Override public void insert(LockRecordOutbox.Entry entry) {
        ContentValues values = new ContentValues();
        values.put("id", entry.id);
        values.put("user_id", entry.userId);
        values.put("server", entry.server);
        values.put("endpoint", entry.endpoint);
        values.put("payload", entry.payload);
        values.put("created_at", entry.createdAt);
        values.put("attempts", entry.attempts);
        values.put("next_attempt_at", entry.nextAttemptAt);
        getWritableDatabase().insertOrThrow("pending_records", null, values);
    }

    @Override public LockRecordOutbox.Entry next(String userId, String server) {
        try (Cursor cursor = getReadableDatabase().query("pending_records", null,
                "user_id=? AND server=?", new String[]{userId, server}, null, null,
                "next_attempt_at ASC, created_at ASC, rowid ASC", "1")) {
            if (!cursor.moveToFirst()) return null;
            LockRecordOutbox.Entry entry = new LockRecordOutbox.Entry();
            entry.id = cursor.getString(cursor.getColumnIndexOrThrow("id"));
            entry.userId = cursor.getString(cursor.getColumnIndexOrThrow("user_id"));
            entry.server = cursor.getString(cursor.getColumnIndexOrThrow("server"));
            entry.endpoint = cursor.getString(cursor.getColumnIndexOrThrow("endpoint"));
            entry.payload = cursor.getString(cursor.getColumnIndexOrThrow("payload"));
            entry.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
            entry.attempts = cursor.getInt(cursor.getColumnIndexOrThrow("attempts"));
            entry.nextAttemptAt = cursor.getLong(cursor.getColumnIndexOrThrow("next_attempt_at"));
            return entry;
        }
    }

    @Override public void delete(String id) {
        getWritableDatabase().delete("pending_records", "id=?", new String[]{id});
    }

    @Override public void defer(String id, int attempts, long nextAttemptAt) {
        ContentValues values = new ContentValues();
        values.put("attempts", attempts);
        values.put("next_attempt_at", nextAttemptAt);
        getWritableDatabase().update("pending_records", values, "id=?", new String[]{id});
    }
}
