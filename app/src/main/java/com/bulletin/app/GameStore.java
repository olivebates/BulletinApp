package com.bulletin.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONException;
import org.json.JSONObject;
import java.time.ZoneId;
import java.util.List;

/** One owner for durable state. Activity, service and receiver all call on the main thread. */
public final class GameStore {
    public final Game game;
    public final Synth sounds;
    public long revision;
    public String warning;
    private final SharedPreferences preferences;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String lastValidSave = "";
    private boolean readOnly;
    public static final class Notice {
        public final String message;
        public final long earned;
        public Notice(String message, long earned) { this.message = message; this.earned = earned; }
    }
    public final java.util.ArrayDeque<Notice> notices = new java.util.ArrayDeque<>();
    public final java.util.Map<String, Long> lawWarningStarts = new java.util.HashMap<>();
    public Runnable lawWarningListener;

    private void completionNotice(Game.Session session, long bounty, long previousBounty, boolean requiredWasOutstanding, List<Game.Event> events, long now) {
        if (session == null || events.stream().noneMatch(e -> e.type.equals("victory") && now - e.at < 5000)) return;
        boolean allRequired = session.required && requiredWasOutstanding && game.requiredPenalty().days == 0;
        String message = Feedback.completion(session.questName, bounty, session.required, allRequired, game.availableQuests().isEmpty());
        message = message.split("\n", 2)[0];
        notices.add(new Notice(message, bounty));
    }

    @android.annotation.SuppressLint("ApplySharedPref") // Preserve the original migration snapshot before writing a new schema.
    public GameStore(Context context) {
        preferences = context.getSharedPreferences("bulletin", Context.MODE_PRIVATE);
        Game loaded;
        try {
            lastValidSave = preferences.getString("state", "");
            if (lastValidSave.isEmpty() && preferences.contains("state")) throw new JSONException("Empty save");
            loaded = GameCodec.decode(lastValidSave, System.currentTimeMillis(), ZoneId.systemDefault());
            if (!lastValidSave.isEmpty() && new JSONObject(lastValidSave).optInt("version", 1) < 4 && !preferences.contains("before_v4")) {
                preferences.edit().putString("before_v4", lastValidSave).commit();
            }
            if (!lastValidSave.isEmpty() && new JSONObject(lastValidSave).optInt("version", 1) < 3) {
                if (!preferences.contains("before_v3")) preferences.edit().putString("before_v3", lastValidSave).commit();
                for (String key : new String[]{"before_v2", "backup"}) {
                    String old = preferences.getString(key, "");
                    if (!old.isEmpty()) try { loaded.firstOpenedDay = Math.min(loaded.firstOpenedDay, new JSONObject(old).getLong("day")); }
                    catch (JSONException ignored) { }
                }
            }
            if (!lastValidSave.isEmpty() && new JSONObject(lastValidSave).optInt("version", 1) == 1
                    && !preferences.contains("before_v2")) {
                preferences.edit().putString("before_v2", lastValidSave).commit();
            }
        } catch (GameCodec.FutureVersion ex) {
            loaded = new Game(System.currentTimeMillis(), ZoneId.systemDefault());
            readOnly = true;
            warning = "Your progress belongs to a newer app. Install that version to continue. Your save has been kept unchanged.";
        } catch (Exception ex) {
            preferences.edit().putString("unreadable_state", preferences.getString("state", "")).apply();
            try {
                lastValidSave = preferences.getString("backup", "");
                if (lastValidSave.isEmpty()) throw new JSONException("No recovery save");
                loaded = GameCodec.decode(lastValidSave, System.currentTimeMillis(), ZoneId.systemDefault());
                warning = "The previous saved state was restored. The unreadable save was preserved.";
            } catch (Exception ignored) {
                loaded = new Game(System.currentTimeMillis(), ZoneId.systemDefault());
                readOnly = true;
                warning = "Your save needs recovery. The original data is preserved and will not be overwritten.";
            }
        }
        game = loaded;
        sounds = new Synth(context);
        // Persist the first-open date immediately, even if the user creates nothing yet.
        // This also commits a successful migration or backup recovery before normal use.
        if (!readOnly) save();
    }

    public void sync() {
        long now = System.currentTimeMillis();
        Game.Session before = game.session;
        boolean requiredWasOutstanding = game.requiredPenalty().days > 0;
        long rewardAt = before != null && before.timed && before.phase == Game.QUEST ? Math.min(now, before.endsAt) : now;
        long bounty = Math.min(game.activeBounty(rewardAt), Game.LIMIT - game.coins), base = game.nextBounty;
        List<Game.Event> events = game.advance(now, ZoneId.systemDefault());
        if (events.isEmpty()) return;
        save();
        completionNotice(before, bounty, base, requiredWasOutstanding, events, now);
        if (before != null && before.phase == Game.REST
                && events.stream().noneMatch(e -> e.type.equals("victory"))
                && events.stream().anyMatch(e -> e.type.equals("ready") && now - e.at < 5000) && game.session == null)
            notices.add(new Notice("Rest finished. Ready for your next quest.", 0));
        playEvents(events, now);
    }
    public void completeManual() {
        sync();
        long now = System.currentTimeMillis();
        Game.Session before = game.session;
        boolean requiredWasOutstanding = game.requiredPenalty().days > 0;
        long bounty = Math.min(game.activeBounty(now), Game.LIMIT - game.coins), base = game.nextBounty;
        List<Game.Event> events = game.completeManual(now);
        if (!events.isEmpty()) { save(); completionNotice(before, bounty, base, requiredWasOutstanding, events, now); playEvents(events, now); }
    }
    private void playEvents(List<Game.Event> events, long now) {
        java.util.List<String> warnings = new java.util.ArrayList<>();
        for (Game.Event event : events) if (event.type.equals("lawLimit") && now - event.at < 5000) warnings.add(event.id);
        if (!warnings.isEmpty()) sounds.playWarning(() -> startLawWarning(warnings));
        boolean victory = events.stream().anyMatch(e -> e.type.equals("victory") && now - e.at < 5000);
        if (victory) handler.removeCallbacksAndMessages(null);
        for (Game.Event event : events) {
            if (now - event.at > 5000) continue;
            if (event.type.equals("victory")) sounds.play(Synth.VICTORY);
            if (event.type.equals("ready")) {
                handler.postDelayed(() -> sounds.play(Synth.DING), victory ? 1200 : 0);
            }
        }
    }
    public boolean incrementLawAmount(Game.Fine law) {
        sync(); if (!game.useLawAmount(law)) return false;
        save(); if (law.amountUsed == law.amountLimit) sounds.playWarning(() -> startLawWarning(java.util.Collections.singletonList(law.id)));
        return true;
    }
    private void startLawWarning(java.util.List<String> ids) {
        long start = android.os.SystemClock.uptimeMillis(); for (String id : ids) lawWarningStarts.put(id, start);
        if (lawWarningListener != null) lawWarningListener.run();
    }

    public boolean completeInstant(Game.Quest quest) {
        sync(); long now = System.currentTimeMillis();
        boolean requiredWasOutstanding = game.requiredPenalty().days > 0;
        long bounty = Math.min(game.bountyFor(quest, now), Game.LIMIT - game.coins), base = game.nextBounty;
        Game.Session before = new Game.Session(); before.questName = quest.name; before.required = game.isRequired(quest);
        List<Game.Event> events = game.completeInstant(quest, now);
        if (events.isEmpty()) return false;
        save(); completionNotice(before, bounty, base, requiredWasOutstanding, events, now); playEvents(events, now); return true;
    }

    public void save() {
        revision++;
        if (readOnly) return;
        try {
            game.lastSeenAt = System.currentTimeMillis();
            game.recordBalance(System.currentTimeMillis());
            String encoded = GameCodec.encode(game).toString();
            if (!preferences.edit().putString("backup", lastValidSave.isEmpty() ? encoded : lastValidSave).putString("state", encoded).commit()) {
                warning = "Progress could not be saved. Please free some device storage.";
            } else lastValidSave = encoded;
        } catch (JSONException ex) { throw new IllegalStateException("Cannot serialize game", ex); }
    }

}
