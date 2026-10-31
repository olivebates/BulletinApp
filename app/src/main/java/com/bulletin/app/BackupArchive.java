package com.bulletin.app;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;

/** Dated snapshots are separate from the game, so exports never contain recursive backups. */
final class BackupArchive {
    static final int LIMIT = 30;
    static final class Entry {
        final long at; final String reason, state;
        Entry(long at, String reason, String state) { this.at = at; this.reason = reason; this.state = state; }
    }
    final List<Entry> entries = new ArrayList<>();
    void add(long at, String reason, String state) {
        entries.add(0, new Entry(at, reason, state));
        while (entries.size() > LIMIT) entries.remove(entries.size() - 1);
    }
    String encode() throws JSONException {
        JSONArray rows = new JSONArray();
        for (Entry e : entries) rows.put(new JSONArray().put(e.at).put(e.reason).put(e.state));
        return rows.toString();
    }
    static BackupArchive decode(String text) throws JSONException {
        BackupArchive archive = new BackupArchive(); JSONArray rows = new JSONArray(text);
        for (int i = 0; i < Math.min(rows.length(), LIMIT); i++) {
            JSONArray e = rows.getJSONArray(i); archive.entries.add(new Entry(e.getLong(0), e.getString(1), e.getString(2)));
        }
        return archive;
    }
}