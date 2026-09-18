// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 souffly007 (Franck R.-F.)
package fr.bonobo.stopdemarchage;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

final class History extends SQLiteOpenHelper {
    static final int LIMIT = 1000;
    static final String CHANGED = "fr.bonobo.stopdemarchage.HISTORY_CHANGED";
    History(Context context) { super(context, "blocked_calls_v1.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE calls (_id INTEGER PRIMARY KEY AUTOINCREMENT, number TEXT NOT NULL, reason TEXT NOT NULL, time INTEGER NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }
    void add(String number, String reason) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("number", number); values.put("reason", reason);
            values.put("time", System.currentTimeMillis());
            db.insertOrThrow("calls", null, values);
            db.execSQL("DELETE FROM calls WHERE _id NOT IN (SELECT _id FROM calls ORDER BY _id DESC LIMIT " + LIMIT + ")");
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    List<Entry> all() {
        List<Entry> rows = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT _id, number, reason, time FROM calls ORDER BY _id DESC LIMIT " + LIMIT, null)) {
            while (c.moveToNext()) rows.add(new Entry(c.getLong(0), FilterEngine.migrateStoredNumber(c.getString(1)), c.getString(2), c.getLong(3)));
        }
        return rows;
    }
    void clear() { getWritableDatabase().delete("calls", null, null); }
    static final class Entry {
        final long id, time; final String number, reason;
        Entry(long id, String number, String reason, long time) {
            this.id = id; this.number = number; this.reason = reason; this.time = time;
        }
    }
}
