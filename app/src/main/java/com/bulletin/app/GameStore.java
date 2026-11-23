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
    public Game game;
    public final Synth sounds;
    public long revision;
    public String warning;
    private final SharedPreferences preferences;
    private final SharedPreferences videoCheckpoints;
    private String lastValidSave = "";
    private boolean readOnly;
    private long saveGeneration;
    private final Handler compressionHandler = new Handler(Looper.getMainLooper());
    private java.util.concurrent.Future<?> compressionTask;
    private final java.util.concurrent.ExecutorService compressionWorker = java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(() -> { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); runnable.run(); }, "Bulletin-save-compression");
        thread.setDaemon(true); return thread;
    });
    private BackupArchive archive = new BackupArchive();
    private final Handler backupHandler = new Handler(Looper.getMainLooper());
    private boolean appOpen;
    private final Runnable periodicBackup = new Runnable() {
        @Override public void run() {
            if (!appOpen) return;
            sync(); backupNow("Two-hour backup"); backupHandler.postDelayed(this, 2 * 60 * 60 * 1000L);
        }
    };
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
        videoCheckpoints=context.getSharedPreferences("youtube-checkpoints",Context.MODE_PRIVATE);
        Game loaded;
        try {
            lastValidSave = preferences.getString("state", "");
            if (lastValidSave.isEmpty() && preferences.contains("state")) throw new JSONException("Empty save");
            loaded = GameCodec.decode(lastValidSave, System.currentTimeMillis(), ZoneId.systemDefault());
            if (!lastValidSave.isEmpty() && GameCodec.json(lastValidSave).optInt("version", 1) < 4 && !preferences.contains("before_v4")) {
                preferences.edit().putString("before_v4", lastValidSave).commit();
            }
            if (!lastValidSave.isEmpty() && GameCodec.json(lastValidSave).optInt("version", 1) < 3) {
                if (!preferences.contains("before_v3")) preferences.edit().putString("before_v3", lastValidSave).commit();
                for (String key : new String[]{"before_v2", "backup"}) {
                    String old = preferences.getString(key, "");
                    if (!old.isEmpty()) try { loaded.firstOpenedDay = Math.min(loaded.firstOpenedDay, GameCodec.json(old).getLong("day")); }
                    catch (Exception ignored) { }
                }
            }
            if (!lastValidSave.isEmpty() && GameCodec.json(lastValidSave).optInt("version", 1) == 1
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
        YoutubePlaybackService.restoreCheckpoint(context,game);
        sounds = new Synth(context);
        applySoundVolume();
        try { archive = BackupArchive.decode(preferences.getString("backup_archive", "[]")); }
        catch (Exception ex) {
            preferences.edit().putString("unreadable_backup_archive", preferences.getString("backup_archive", "")).commit();
            warning = "The backup list could not be read. Its original data was preserved.";
        }
        if(!readOnly&&!lastValidSave.isEmpty()&&!preferences.contains("before_v18"))try{
            if(GameCodec.json(lastValidSave).optInt("version",1)<18){
                BackupArchive before=BackupArchive.decode(archive.encode());before.add(System.currentTimeMillis(),"Before history compaction",lastValidSave);
                if(preferences.edit().putString("before_v18",lastValidSave).putString("backup_archive",before.encode()).commit())archive=before;
                else {readOnly=true;warning="Could not back up the old save. Free storage before upgrading history.";}
            }
        }catch(Exception ex){readOnly=true;warning="The old save is preserved; history upgrade needs recovery.";}
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
        playCompletionSounds(events,now,sounds::play);
    }
    static void playCompletionSounds(List<Game.Event> events,long now,java.util.function.IntConsumer play) {
        for (Game.Event event : events) {
            if (event.at > now || now - event.at >= 5000) continue;
            if (event.type.equals("victory")) play.accept(Synth.VICTORY);
            if (event.type.equals("ready")) play.accept(Synth.DING);
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

    public void appOpened() {
        if (appOpen) return;
        appOpen = true; sync(); backupNow("App opened");
        backupHandler.removeCallbacks(periodicBackup); backupHandler.postDelayed(periodicBackup, 2 * 60 * 60 * 1000L);
    }
    public void appClosed() {
        if (!appOpen) return;
        appOpen = false; backupHandler.removeCallbacks(periodicBackup); sync(); backupNow("App closed");
    }
    public java.util.List<BackupArchive.Entry> backups() { return new java.util.ArrayList<>(archive.entries); }
    public boolean backupNow(String reason) {
        if (!readOnly && !save(true)) return false;
        String current = preferences.getString("state", "");
        if (current.isEmpty()) return false;
        try {
            BackupArchive next = BackupArchive.decode(archive.encode());
            next.add(System.currentTimeMillis(), reason, current);
            if (!preferences.edit().putString("backup_archive", next.encode()).commit()) {
                warning = "Backup could not be saved. Please free some device storage."; return false;
            }
            archive = next; return true;
        } catch (JSONException ex) { warning = "Backup could not be saved."; return false; }
    }
    public String exportSave() throws java.io.IOException {
        sync();
        if (!readOnly && !save(true)) throw new java.io.IOException("Current progress could not be saved");
        return preferences.getString("state", "");
    }
    public void restoreSave(String text, String reason) throws Exception {
        Game candidate = SaveImports.decode(text, System.currentTimeMillis(), ZoneId.systemDefault());
        BalanceHistory.prune(candidate.balances, System.currentTimeMillis(), ZoneId.systemDefault());
        HistoryAggregation.aggregate(candidate,ZoneId.systemDefault());candidate.aggregationDay=candidate.day;
        // Validation must finish before creating a backup or touching current progress.
        String encoded = SaveCompression.pack(GameCodec.encode(candidate).toString());
        if(YoutubePlaybackService.active!=null)YoutubePlaybackService.active.stopPlayback();
        sync();
        if (!backupNow(reason)) throw new java.io.IOException("Could not back up current progress. Nothing was restored.");
        String previous = preferences.getString("state", "");
        if (!preferences.edit().putString("backup", previous).putString("state", encoded).commit())
            throw new java.io.IOException("Could not save restored progress");
        notices.clear(); lawWarningStarts.clear();
        saveGeneration++; compressionHandler.removeCallbacksAndMessages(null);
        videoCheckpoints.edit().clear().commit();game = candidate;applySoundVolume();lastValidSave = encoded; readOnly = false; revision++;
    }
    private void refineSave(String json, String saved, long generation) {
        compressionHandler.removeCallbacksAndMessages(null);
        compressionHandler.postDelayed(() -> {
            if (compressionTask != null) compressionTask.cancel(false);
            compressionTask = compressionWorker.submit(() -> {
                try {
                    String shorter = SaveCompression.pack(json);
                    if (shorter.length() >= saved.length()) return;
                    compressionHandler.post(() -> {
                        if (readOnly || saveGeneration != generation || !lastValidSave.equals(saved)) return;
                        if (preferences.edit().putString("state", shorter).commit()) lastValidSave = shorter;
                    });
                } catch (Exception ignored) { /* The already committed fast save remains valid. */ }
            });
        }, 500);
    }
    public boolean save() { return save(false); }
    private void applySoundVolume(){sounds.setVolume(game.soundVolume);if(MeditationService.active!=null)MeditationService.active.refreshVolume();}
    private boolean save(boolean strongest) {
        applySoundVolume();
        revision++;
        if (readOnly) return false;
        long generation = ++saveGeneration; compressionHandler.removeCallbacksAndMessages(null);
        try {
            long now = System.currentTimeMillis(); game.lastSeenAt = now;
            BalanceHistory.prune(game.balances, now, ZoneId.systemDefault());
            if(game.aggregationDay!=game.day){HistoryAggregation.aggregate(game,ZoneId.systemDefault());game.aggregationDay=game.day;}
            game.recordBalance(now);
            String json = GameCodec.encode(game).toString();
            String encoded = strongest ? SaveCompression.pack(json) : SaveCompression.packFast(json);
            String previous = lastValidSave.isEmpty() ? encoded : lastValidSave;
            // Upgrade a legacy recovery copy too; migration snapshots remain untouched.
            if (!SaveCompression.isPacked(previous)) previous = SaveCompression.pack(SaveCompression.unpack(previous));
            if (!preferences.edit().putString("backup", previous).putString("state", encoded).commit()) {
                warning = "Progress could not be saved. Please free some device storage."; return false;
            }
            lastValidSave = encoded; if (!strongest) refineSave(json, encoded, generation); return true;
        } catch (Exception ex) { android.util.Log.e("BulletinSave", "Save encoding failed", ex); warning = "Progress could not be saved. Your previous save is preserved."; return false; }
    }
}
