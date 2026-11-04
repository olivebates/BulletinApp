package com.bulletin.app;

import org.json.JSONObject;
import org.json.JSONException;
import java.time.ZoneId;

final class SaveImports {
    static Game decode(String text, long now, ZoneId zone) throws Exception {
        String json = SaveCompression.unpack(text);
        if (json.isEmpty()) throw new JSONException("Paste a save string first");
        JSONObject object = GameCodec.json(json);
        if (!object.has("day") || !object.has("quests")) throw new JSONException("This is not a Bulletin save");
        object.getLong("day"); object.getJSONArray("quests");
        return GameCodec.decode(json, now, zone);
    }
}