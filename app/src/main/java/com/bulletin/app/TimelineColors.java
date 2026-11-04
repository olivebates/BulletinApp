package com.bulletin.app;

import java.time.ZoneId;
import java.util.*;

/** Session colors follow accepted energy; summaries follow their recorded focus totals. */
final class TimelineColors {
    static final int MAROON = 0xff800020, YELLOW = 0xfff4d17e;
    static int energy(int energy) {
        switch (energy) {
            case Game.DEAD: return intensity(0);
            case Game.MINIMAL: return intensity(.2);
            case Game.RELAX: return intensity(.4);
            case Game.NORMAL: return intensity(.6);
            case Game.EXTRA: return intensity(.8);
            case Game.EXTREME: return YELLOW;
            default: return intensity(.5); // Unknown historical energy uses the midpoint.
        }
    }
    static int intensity(double amount) {
        amount = Math.max(0, Math.min(1, amount));
        return 0xff000000 | ((int) Math.round(128 + 116 * amount) << 16)
            | ((int) Math.round(209 * amount) << 8) | (int) Math.round(32 + 94 * amount);
    }
    static Map<Game.HistoryEntry,Integer> summaries(Game game) {
        return summaries(game, ZoneId.systemDefault());
    }
    static Map<Game.HistoryEntry,Integer> summaries(Game game, ZoneId zone) {
        // Compare all quests, independently of the selected range and random-board mode.
        Map<String,Long> totals = new HashMap<>();
        for (Game.FocusEntry f : game.focus) {
            String key = f.id + "\0" + f.day + "\0" + f.endDay;
            totals.put(key, Game.add(totals.getOrDefault(key, 0L), f.millis));
        }
        long dailyMaximum = 0, weeklyMaximum = 0;
        for (Game.FocusEntry f : game.focus) {
            long total = totals.get(f.id + "\0" + f.day + "\0" + f.endDay);
            if (f.endDay > f.day) weeklyMaximum = Math.max(weeklyMaximum, total);
            else dailyMaximum = Math.max(dailyMaximum, total);
        }
        Map<Game.HistoryEntry,Integer> result = new IdentityHashMap<>();
        for (Game.HistoryEntry e : game.history) {
            if (!Game.QUEST_HISTORY.equals(e.kind) || (e.precision == 0 && !e.dateOnly)) continue;
            long first = e.bucketDay == Long.MIN_VALUE ? Game.localDay(e.at, zone) : e.bucketDay;
            long last = e.endDay == Long.MIN_VALUE ? first : e.endDay;
            Long time = totals.get(e.id + "\0" + first + "\0" + last);
            long maximum = e.precision == 7 ? weeklyMaximum : dailyMaximum;
            if (time != null && time > 0 && maximum > 0) result.put(e, intensity((double) time / maximum));
        }
        return result;
    }
}