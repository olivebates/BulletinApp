package com.bulletin.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.Set;

/** Original, synthesized PCM effects. No recordings or network access required. */
public final class Synth {
    public static final int VICTORY = 0, DING = 1, COIN = 2, FAILURE = 3, LOSS = 4, WARNING = 5;
    public static final int WARNING_MILLIS = 1200;
    private final SoundPool pool;
    private final int[] ids = new int[6];
    private final Set<Integer> loaded = new HashSet<>();
    private final Set<Integer> queued = new HashSet<>();
    private long lastPlayedAt;
    private int activeStream;
    private long warningUntil;
    private Runnable warningStarted;

    public Synth(Context context) {
        pool = new SoundPool.Builder().setMaxStreams(8).setAudioAttributes(new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        pool.setOnLoadCompleteListener((p, id, status) -> {
            if (status == 0) {
                loaded.add(id);
                if (queued.remove(id)) {
                    playLoaded(id);
                }
            }
        });
        for (int effect = 0; effect < ids.length; effect++) {
            File file = new File(context.getCacheDir(), (effect == WARNING ? "synth-swell-v1-" : "synth-v2-") + effect + ".wav");
            try {
                if (!file.exists()) try (FileOutputStream out = new FileOutputStream(file)) { out.write(wav(effect)); }
                ids[effect] = pool.load(file.getAbsolutePath(), 1);
            } catch (IOException ignored) { /* A sound failure must not interrupt progress. */ }
        }
    }
    public void play(int effect) {
        int id = ids[effect];
        if (id == 0) return;
        if (loaded.contains(id)) playLoaded(id);
        else if (effect == WARNING || !queued.contains(ids[WARNING])) { queued.clear(); queued.add(id); }
    }
    public void playWarning(Runnable started) { warningStarted = started; play(WARNING); }
    private void playLoaded(int id) {
        long now = android.os.SystemClock.uptimeMillis();
        if (id != ids[WARNING] && (now - lastPlayedAt < 220 || now < warningUntil)) return;
        if (id == ids[WARNING]) warningUntil = now + WARNING_MILLIS;
        lastPlayedAt = now;
        if (activeStream != 0) pool.stop(activeStream);
        float volume = volume(id); activeStream = pool.play(id, volume, volume, 1, 0, 1);
        if (id == ids[WARNING] && warningStarted != null) { Runnable started = warningStarted; warningStarted = null; started.run(); }
    }
    private float volume(int id) { return id == ids[WARNING] ? .75f : id == ids[DING] ? .25f : id == ids[COIN] ? .2f : id == ids[FAILURE] ? .3f : .45f; }
    static byte[] wav(int kind) {
        int rate = 22050;
        double duration = kind == WARNING ? WARNING_MILLIS / 1000d : kind == VICTORY ? 1.15 : kind == DING ? .85 : kind == FAILURE ? .8 : .18;
        int samples = (int) (rate * duration);
        ByteBuffer b = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN);
        b.put(new byte[]{'R','I','F','F'}).putInt(36 + samples * 2).put(new byte[]{'W','A','V','E','f','m','t',' '})
            .putInt(16).putShort((short) 1).putShort((short) 1).putInt(rate).putInt(rate * 2)
            .putShort((short) 2).putShort((short) 16).put(new byte[]{'d','a','t','a'}).putInt(samples * 2);
        for (int i = 0; i < samples; i++) {
            double t = (double) i / rate, value = 0;
            if (kind == VICTORY) {
                double[] notes = {523.25, 659.25, 783.99, 1046.5};
                for (int n = 0; n < notes.length; n++) value += note(t - n * .14, notes[n], .65) * .28;
            } else if (kind == DING) {
                value = (note(t, 523.25, duration) * .45 + note(t - .06, 659.25, .75) * .2) * Math.min(1, t / .04);
            } else if (kind == COIN) {
                value = (note(t, 783.99, duration) * .35 + note(t - .025, 987.77, .15) * .12) * Math.min(1, t / .025);
            } else if (kind == FAILURE) {
                for (int n = 0; n < 3; n++) value += note(t - .16 * n, 349.23 / Math.pow(1.26, n), .45) * .45;
            } else if (kind == WARNING) {
                value = warningEnvelope(t * 1000) * (.8 * Math.sin(2 * Math.PI * 880 * t) + .15 * Math.sin(2 * Math.PI * 1760 * t));
            } else value = note(t, 220, duration) * .65 + note(t, 164.81, duration) * .2;
            b.putShort((short) (Math.max(-1, Math.min(1, value)) * 26000));
        }
        return b.array();
    }
    static float warningEnvelope(double elapsedMillis) {
        if (elapsedMillis <= 0 || elapsedMillis >= WARNING_MILLIS) return 0;
        return (float) Math.pow(Math.sin(Math.PI * elapsedMillis / WARNING_MILLIS), 1.2);
    }
    private static double note(double t, double hz, double length) {
        if (t < 0 || t >= length) return 0;
        double envelope = Math.min(1, t / .008) * Math.pow(1 - t / length, 2);
        return (Math.sin(2 * Math.PI * hz * t) + .16 * Math.sin(4 * Math.PI * hz * t)) * envelope;
    }
}
