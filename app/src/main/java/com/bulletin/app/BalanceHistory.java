package com.bulletin.app;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

final class BalanceHistory {
    static void prune(List<Game.BalancePoint> points, long now, ZoneId zone) {
        long cutoff = Instant.ofEpochMilli(now).atZone(zone).minusYears(5).toInstant().toEpochMilli();
        int first = 0;
        while (first + 1 < points.size() && points.get(first + 1).at <= cutoff) first++;
        if (first > 0) points.subList(0, first).clear();
        if (!points.isEmpty() && points.get(0).at < cutoff) {
            Game.BalancePoint previous = points.get(0);
            points.set(0, new Game.BalancePoint(cutoff, previous.cents, "Balance carried forward"));
        }
    }
    static List<Game.BalancePoint> window(List<Game.BalancePoint> points, long start, long end, long fallback) {
        List<Game.BalancePoint> visible = new ArrayList<>(); long balance = fallback;
        if (!points.isEmpty()) balance = points.get(0).cents;
        for (Game.BalancePoint p : points) if (p.at <= start) balance = p.cents; else break;
        visible.add(new Game.BalancePoint(start, balance, "Balance at range start"));
        for (Game.BalancePoint p : points) if (p.at >= start && p.at <= end) { if (p.at == start && visible.size() == 1) visible.clear(); visible.add(p); balance = p.cents; }
        if (visible.isEmpty() || visible.get(visible.size()-1).at != end) visible.add(new Game.BalancePoint(end, balance, "Balance at range end"));
        return visible;
    }
}