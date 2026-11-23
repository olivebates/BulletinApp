package com.bulletin.app;

import java.time.ZoneId;
import java.util.*;
import org.json.*;

/** Acknowledging a completion is presentation only; money and rest are settled by Game. */
final class CompletionLog {
    static final class Entry {
        final String id;
        final long at, day, earned;
        final boolean grand;
        Entry(String id, long at, long day, long earned, boolean grand) {
            this.id=id; this.at=at; this.day=day; this.earned=earned; this.grand=grand;
        }
    }
    private final Map<String,SortedSet<Long>> taskDays = new LinkedHashMap<>();
    final List<Entry> pending = new ArrayList<>();
    void record(String id, long at, long day, long earned, boolean grand) {
        pending.add(new Entry(id,at,day,earned,grand));
    }
    SortedSet<Long> days(String id) { return Collections.unmodifiableSortedSet(taskDays.getOrDefault(id,new TreeSet<>())); }
    SortedSet<Long> completedDays(String id) {SortedSet<Long> dates=new TreeSet<>(days(id));for(Entry e:pending)if(e.id.equals(id))dates.add(e.day);return dates;}
    long latestDay(String id) {long latest=Long.MIN_VALUE;for(Entry e:pending)if(e.id.equals(id))latest=Math.max(latest,e.day);return latest;}
    long earned(String id) { long total=0; for(Entry e:pending) if(e.id.equals(id)) total=Game.add(total,e.earned); return total; }
    boolean grand(String id) { for(Entry e:pending) if(e.id.equals(id)&&e.grand) return true; return false; }
    void acknowledge(String id) { for(Entry e:pending) if(e.id.equals(id)) taskDays.computeIfAbsent(id,key->new TreeSet<>()).add(e.day); pending.removeIf(e->e.id.equals(id)); }
    void delete(String id) {taskDays.remove(id);pending.removeIf(e->e.id.equals(id));}
    void undo(String id,long at,List<Game.HistoryEntry> history,ZoneId zone) {
        pending.removeIf(e->e.id.equals(id)&&e.at==at);
        long day=Game.localDay(at,zone);
        for(Game.HistoryEntry e:history) if(Game.QUEST_HISTORY.equals(e.kind)&&e.id.equals(id)
            && (e.bucketDay==Long.MIN_VALUE?Game.localDay(e.at,zone):e.bucketDay)==day) return;
        SortedSet<Long> dates=taskDays.get(id);if(dates!=null)dates.remove(day);
    }
    JSONObject encode() throws JSONException {
        JSONObject dates=new JSONObject();JSONArray entries=new JSONArray();
        for(Map.Entry<String,SortedSet<Long>> task:taskDays.entrySet()) {JSONArray values=new JSONArray();for(long day:task.getValue())values.put(day);dates.put(task.getKey(),values);}
        for(Entry e:pending) entries.put(new JSONArray().put(e.id).put(e.at).put(e.day).put(e.earned).put(e.grand));
        return new JSONObject().put("tasks",dates).put("pending",entries);
    }
    void decode(JSONObject json) throws JSONException {
        JSONObject dates=json.getJSONObject("tasks");JSONArray entries=json.getJSONArray("pending");
        for(Iterator<String> ids=dates.keys();ids.hasNext();) {String id=ids.next();JSONArray values=dates.getJSONArray(id);SortedSet<Long> task=new TreeSet<>();for(int i=0;i<values.length();i++)task.add(values.getLong(i));taskDays.put(id,task);}
        for(int i=0;i<entries.length();i++) {JSONArray e=entries.getJSONArray(i);record(e.getString(0),e.getLong(1),e.getLong(2),Math.max(0,Math.min(Game.LIMIT,e.getLong(3))),e.getBoolean(4));}
        // Older versions queued every repeat. Keep only the first unacknowledged completion per day.
        Set<String> seen=new HashSet<>();
        pending.removeIf(e->days(e.id).contains(e.day)||!seen.add(e.id+"\0"+e.day));
    }
    void importHistory(List<Game.HistoryEntry> history,ZoneId zone) {
        // Weekly summaries no longer identify individual completion dates.
        for(Game.HistoryEntry e:history) if(Game.QUEST_HISTORY.equals(e.kind)&&e.precision!=7)
            taskDays.computeIfAbsent(e.id,key->new TreeSet<>()).add(e.bucketDay==Long.MIN_VALUE?Game.localDay(e.at,zone):e.bucketDay);
    }
}
