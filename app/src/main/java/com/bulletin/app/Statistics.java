package com.bulletin.app;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/** Aggregation shared by the pie, bars and yearly heatmaps. Quest values are milliseconds. */
final class Statistics {
    static final class Series {
        final String id;
        String name;
        int hue;
        long value, heatMaximum;
        final Map<Long, Long> days = new LinkedHashMap<>();
        Series(String id, String name, int hue) { this.id = id; this.name = name; this.hue = hue; }
        int heatAlpha(long value) { return value <= 0 ? 24 : Math.max(35, (int) Math.min(255, 255d * value / Math.max(1, heatMaximum))); }
        void add(long day, long amount) { value = Game.add(value, amount); days.put(day, Game.add(days.getOrDefault(day, 0L), amount)); }
    }
    final List<Series> series = new ArrayList<>(), slices = new ArrayList<>(), heatSeries = new ArrayList<>();
    long total, maximum;
    final long firstDay, lastDay;
    final boolean laws;
    static long rangeStart(long day, int range) {
        LocalDate today = LocalDate.ofEpochDay(day);
        switch (range) {
            case 0: return day;
            case 1: return day - 6;
            case 2: return today.minusMonths(1).plusDays(1).toEpochDay();
            case 3: return today.minusMonths(3).plusDays(1).toEpochDay();
            default: return Long.MIN_VALUE;
        }
    }
    Statistics(Game game, boolean laws, long now, ZoneId zone) {
        this(game, laws, now, zone, Long.MIN_VALUE);
    }
    Statistics(Game game, boolean laws, long now, ZoneId zone, long sinceDay) {
        this.laws = laws; lastDay = game.day; firstDay = LocalDate.ofEpochDay(lastDay).minusYears(1).plusDays(1).toEpochDay();
        Map<String, Series> items = new LinkedHashMap<>(); Set<String> completed = new HashSet<>();
        if (laws) for (Game.Fine f : game.fines) items.put(f.id, new Series(f.id, f.name, 0));
        else for (Game.Quest q : game.quests) items.put(q.id, new Series(q.id, q.name, Math.round(game.questHue(q))));
        for (Game.HistoryEntry e : game.history) if (e.kind.equals(laws ? Game.FINE_HISTORY : Game.QUEST_HISTORY)) {
            completed.add(e.id); Series s = items.computeIfAbsent(e.id, id -> new Series(id, e.name, laws ? 0 : Math.round(game.hue)));
            long day = Game.localDay(e.at, zone);
            if (laws && day >= sinceDay && day <= lastDay) s.add(day, e.count);
        }
        if (!laws) {
            for (Game.FocusEntry e : game.focus) if (e.day >= sinceDay && e.day <= lastDay) items.computeIfAbsent(e.id, id -> new Series(id, e.name, Math.round(game.hue))).add(e.day, e.millis);
            Game.Session active = game.session;
            if (active != null && active.timed && active.phase == Game.QUEST && game.day >= sinceDay) {
                long elapsed = Math.max(0, Math.min(now, active.endsAt) - active.accountedAt);
                if (elapsed > 0) items.computeIfAbsent(active.questId, id -> new Series(id, active.questName, Math.round(game.hue))).add(game.day, elapsed);
            }
        }
        series.addAll(items.values());
        Set<String> active = new HashSet<>();
        if (laws) for (Game.Fine law : game.fines) active.add(law.id);
        else for (Game.Quest q : game.activeRepeatableQuests()) active.add(q.id);
        for (Series s : series) {
            if (active.contains(s.id)) {
                heatSeries.add(s);
                for (Map.Entry<Long, Long> entry : s.days.entrySet()) if (entry.getKey() >= firstDay && entry.getKey() <= lastDay) s.heatMaximum = Math.max(s.heatMaximum, entry.getValue());
                maximum = Math.max(maximum, s.heatMaximum);
            }
            if (active.contains(s.id) && completed.contains(s.id) && s.value > 0) total = Game.add(total, s.value);
        }
        Series other = new Series("other", "Other", laws ? 0 : Math.round(game.hue));
        for (Series s : series) if (active.contains(s.id) && completed.contains(s.id) && s.value > 0) {
            if ((double) s.value / Math.max(1, total) < .01) other.value = Game.add(other.value, s.value);
            else slices.add(s);
        }
        slices.sort(Comparator.comparingLong((Series s) -> s.value).reversed());
        if (other.value > 0) slices.add(other);
    }
    static String amount(long value, boolean laws) {
        if (laws) return value + (value == 1 ? " report" : " reports");
        long seconds = value / 1000, hours = seconds / 3600, minutes = seconds / 60 % 60;
        return hours > 0 ? hours + "h " + minutes + "m" + (seconds % 60 > 0 ? " " + seconds % 60 + "s" : "") : minutes > 0 ? minutes + "m " + seconds % 60 + "s" : seconds + "s";
    }
}
