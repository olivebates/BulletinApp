package com.bulletin.app;

import java.time.*;
import java.util.*;
import org.json.*;

/** Daily milestones are earned by Game; checking a square only acknowledges them. */
final class MustDoLog {
    private final SortedSet<Long> days = new TreeSet<>();
    private final Set<Long> pending = new HashSet<>();
    private long startedDay = Long.MIN_VALUE;
    void record(long day) {if(days.add(day))pending.add(day);if(startedDay==Long.MIN_VALUE)startedDay=day;}
    boolean completed(long day) {return days.contains(day);}
    boolean hasPending(long today) {return pending.contains(today);}
    void acknowledge(long day) {pending.remove(day);}
    void undo(long day) {days.remove(day);pending.remove(day);}
    YearMonth firstMonth(long today) {
        YearMonth current=YearMonth.from(LocalDate.ofEpochDay(today));
        YearMonth start=startedDay==Long.MIN_VALUE?current:YearMonth.from(LocalDate.ofEpochDay(startedDay));
        return start.isBefore(current.minusMonths(2))?current.minusMonths(2):start.isAfter(current)?current:start;
    }
    JSONObject encode() throws JSONException {
        JSONArray dates=new JSONArray(),waiting=new JSONArray();for(long day:days)dates.put(day);for(long day:new TreeSet<>(pending))waiting.put(day);
        return new JSONObject().put("days",dates).put("pending",waiting).put("started",startedDay);
    }
    void decode(JSONObject json) throws JSONException {
        JSONArray dates=json.getJSONArray("days"),waiting=json.getJSONArray("pending");
        for(int i=0;i<dates.length();i++){long day=dates.getLong(i);LocalDate.ofEpochDay(day);days.add(day);}
        for(int i=0;i<waiting.length();i++){long day=waiting.getLong(i);if(days.contains(day))pending.add(day);}
        startedDay=json.optLong("started",days.isEmpty()?Long.MIN_VALUE:days.first());
        if(startedDay!=Long.MIN_VALUE)LocalDate.ofEpochDay(startedDay);
    }
}
