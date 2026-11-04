package com.bulletin.app;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.time.ZoneId;
import java.time.LocalDate;
final class GameCodec {
    static final int VERSION = 18;
    private static int readEnergy(JSONObject o, String key) { int value = o.optInt(key, -1); return value >= Game.EXTRA && value <= Game.RELAX ? value : -1; }
    static final class FutureVersion extends JSONException {
        FutureVersion() { super("This save requires a newer app version"); }
    }
    static JSONObject json(String text) throws JSONException {
        try { text=SaveCompression.unpack(text); } catch(java.io.IOException ex){throw new JSONException(ex.getMessage());}
        JSONObject object=new JSONObject(text);
        if(object.has("r")&&object.has("s")&&object.has("b")){
            int version=Math.max(object.optInt("v"),object.optInt("version"));if(version>VERSION)throw new FutureVersion();if(version!=18)throw new JSONException("Unknown compact save version");
            return CompactSave.expand(object);
        }
        return object;
    }
    static JSONObject encode(Game g) throws JSONException { return CompactSave.pack(encodeLegacy(g)); }
    static JSONObject encodeLegacy(Game g) throws JSONException {
        JSONObject o = new JSONObject();
        if (!g.briefShowTitles) o.put("briefShowTitles", false);
        if (g.timelineHintShown) o.put("timelineHintShown", true);
        if (g.choiceHintShown) o.put("choiceHintShown", true);
        if (g.rearrangeVisited) o.put("rearrangeVisited", true);
        if (g.rearrangeExitHintShown) o.put("rearrangeExitHintShown", true);
        o.put("welcomeShown", g.welcomeShown);
        if (g.randomModeUsedMillis > 0) o.put("randomModeUsedMillis", g.randomModeUsedMillis);
        if (g.questCreationHintShown) o.put("questCreationHintShown", true);
        if (g.questStartHintShown) o.put("questStartHintShown", true);
        if (g.energyHintShown) o.put("energyHintShown", true);
        if (g.hasCreatedCategory) o.put("hasCreatedCategory", true);
        if (g.questAdvancedExpanded) o.put("questAdvancedExpanded", true);
        if (!g.choiceLists.isEmpty()) {
            JSONArray lists = new JSONArray();
            for (Game.ChoiceList list : g.choiceLists) lists.put(new JSONObject().put("id", list.id).put("name", list.name).put("items", new JSONArray(list.items)));
            o.put("choiceLists", lists);
        }
        if (!g.briefLists.isEmpty()) {
            JSONArray lists = new JSONArray();
            for (Game.ChoiceList list : g.briefLists) lists.put(new JSONObject().put("id", list.id).put("name", list.name).put("items", new JSONArray(list.items)));
            o.put("briefLists", lists);
        }
        JSONArray focus = new JSONArray();
        for (Game.FocusEntry e : g.focus) focus.put(new JSONObject().put("id", e.id).put("name", e.name).put("day", e.day).put("millis", e.millis).put("endDay",e.endDay));
        o.put("focus", focus).put("analyticsSince", g.analyticsSince);
        o.put("lastSeenAt", g.lastSeenAt).put("swipeHintShown", g.swipeHintShown).put("boardOrder", new JSONArray(g.boardOrder));
        JSONArray categories = new JSONArray();
        for (Game.Category c : g.categories) categories.put(new JSONObject().put("id", c.id).put("name", c.name).put("hue", c.hue).put("collapsed", c.collapsed).put("mustDo", c.mustDo).put("requireAll", c.requireAll).put("requiredTasks", c.requiredTasks).put("progressDay", c.progressDay).put("progressCount", c.progressCount).put("completedQuestIds", new JSONArray(c.completedQuestIds)).put("fulfilledDay", c.fulfilledDay).put("fulfilledTarget", c.fulfilledTarget));
        o.put("categories", categories).put("collapsedAnalyticsSections", new JSONArray(g.collapsedAnalyticsSections));
        o.put("version", VERSION).put("day", g.day).put("days", g.days).put("coins", g.coins)
            .put("firstOpenedDay", g.firstOpenedDay)
            .put("timedMillisToday", g.timedMillisToday)
            .put("pendingConfetti", g.pendingConfetti)
            .put("bountyBase", g.bountyBase)
            .put("randomMode", g.randomMode).put("randomDay", g.randomDay).put("randomQuestIds", new JSONArray(g.randomQuestIds))
            .put("pendingCoins", g.pendingCoins).put("nextBounty", g.nextBounty)
            .put("completedToday", g.completedToday).put("finesToday", g.finesToday)
            .put("questMinutes", g.questMinutes).put("restMinutes", g.restMinutes)
            .put("energy", g.energy)
            .put("questsCreated", g.questsCreated).put("lawsCreated", g.lawsCreated)
            .put("hue", g.hue).put("lastSettlement", g.lastSettlement);
        JSONArray quests = new JSONArray();
        for (Game.Quest q : g.quests) quests.put(encodeQuest(q));
        o.put("quests", quests);
        JSONArray deleted = new JSONArray(); for (Game.Quest q : g.deletedQuests) deleted.put(encodeQuest(q));
        o.put("deletedQuests", deleted).put("excludedRandomCategories", new JSONArray(g.excludedRandomCategories));
        JSONArray fines = new JSONArray();
        for (Game.Fine fine : g.fines) fines.put(new JSONObject().put("id", fine.id).put("name", fine.name).put("emoji", fine.emoji)
            .put("mode", fine.mode).put("amountLimit", fine.amountLimit).put("amountUsed", fine.amountUsed)
            .put("timeLimitMillis", fine.timeLimitMillis).put("remainingMillis", fine.remainingMillis).put("endsAt", fine.endsAt));
        o.put("fines", fines);
        if (g.session != null) {
            Game.Session s = g.session;
            o.put("session", new JSONObject().put("questId", s.questId).put("questName", s.questName)
                .put("occurrenceDay", s.occurrenceDay).put("endsAt", s.endsAt)
                .put("restMinutes", s.restMinutes).put("phase", s.phase)
                .put("energy", s.energy < 0 ? null : s.energy).put("timed", s.timed).put("required", s.required).put("accountedAt", s.accountedAt));
            o.getJSONObject("session").put("rewardMinutes", s.rewardMinutes).put("referenceMinutes", s.referenceMinutes);
            o.getJSONObject("session").put("rewardMillis", s.rewardMillis).put("referenceMillis", s.referenceMillis);
            o.getJSONObject("session").put("bountySince", s.bountySince);
            o.getJSONObject("session").put("restMillis", s.restMillis).put("activeMillis", s.activeMillis);
        }
        JSONArray history = new JSONArray();
        for (Game.HistoryEntry e : g.history) history.put(new JSONObject().put("kind", e.kind).put("id", e.id)
            .put("name", e.name).put("at", e.at).put("count", e.count).put("dateOnly", e.dateOnly).put("precision",e.precision).put("bucketDay",e.bucketDay).put("endDay",e.endDay).put("coinsDelta",e.coinsDelta).put("coinsKnown",e.coinsKnown).put("energy",e.energy < 0 ? null : e.energy));
        o.put("history", history);
        JSONArray changes = new JSONArray();
        for (Game.HistoryEntry e : g.bountyChanges) changes.put(new JSONObject().put("kind", e.kind).put("id", e.id).put("at", e.at).put("bountyMillis", e.bountyMillis > 0 ? e.bountyMillis : null));
        o.put("bountyChanges", changes);
        JSONArray balances = new JSONArray();
        for (Game.BalancePoint point : g.balances) balances.put(new JSONObject().put("at", point.at).put("cents", point.cents).put("cause", point.cause).put("periodStart",point.periodStart).put("periodEnd",point.periodEnd).put("opening",point.opening).put("minimum",point.minimum).put("maximum",point.maximum).put("precision",point.precision));
        o.put("balances", balances).put("balanceRangeStart", g.balanceRangeStart).put("balanceRangeEnd", g.balanceRangeEnd);
        JSONArray undo = new JSONArray();
        for (Game.UndoEntry e : g.undo) undo.put(new JSONObject().put("questId", e.questId).put("at", e.at)
            .put("reward", e.reward).put("bountyBefore", e.bountyBefore).put("bountyAfter", e.bountyAfter));
        for (int i = 0; i < g.undo.size(); i++) undo.getJSONObject(i).put("previousBountySince", g.undo.get(i).previousBountySince).put("categoryId", g.undo.get(i).categoryId);
        o.put("undo", undo);
        JSONArray reviews = new JSONArray();
        for (Game.MissedReview r : g.missedReviews) reviews.put(new JSONObject().put("day", r.day)
            .put("coinsLost", r.coinsLost).put("daysLost", r.daysLost).put("interestLost", r.interestLost)
            .put("rentDifference", r.rentDifference)
            .put("ids", new JSONArray(r.ids)).put("names", new JSONArray(r.names)));
        o.put("missedReviews", reviews);
        o.put("questTimelineStart",g.questTimelineStart).put("questTimelineEnd",g.questTimelineEnd).put("lawTimelineStart",g.lawTimelineStart).put("lawTimelineEnd",g.lawTimelineEnd).put("nextEntityId",g.nextEntityId);
        return o;
    }

    static Game decode(String text, long now, ZoneId zone) throws JSONException {
        Game g = new Game(now, zone);
        try { text = SaveCompression.unpack(text); } catch (java.io.IOException ex) { throw new JSONException(ex.getMessage()); }
        if (text.isEmpty()) { g.addDemoLists(); return g; }
        JSONObject o = json(text);
        int version = o.optInt("version", 1);
        if (version > VERSION) throw new FutureVersion();
        if (version < 1) throw new JSONException("Invalid save version");
        JSONArray choiceLists = o.optJSONArray("choiceLists");
        if (choiceLists != null) for (int i = 0; i < choiceLists.length(); i++) {
            JSONObject value = choiceLists.getJSONObject(i); Game.ChoiceList list = new Game.ChoiceList();
            list.id = value.getString("id"); list.name = value.getString("name");
            JSONArray items = value.getJSONArray("items"); for (int n = 0; n < items.length(); n++) list.items.add(items.getString(n));
            g.choiceLists.add(list);
        }
        JSONArray briefLists = o.optJSONArray("briefLists");
        if (briefLists != null) for (int i = 0; i < briefLists.length(); i++) {
            JSONObject value = briefLists.getJSONObject(i); Game.ChoiceList list = new Game.ChoiceList();
            list.id = value.getString("id"); list.name = value.getString("name");
            JSONArray items = value.getJSONArray("items");
            for (int n = 0; n < items.length(); n++) { String category = items.getString(n); if (!BriefDictionary.category(category)) throw new JSONException("Unknown brief category"); list.items.add(category); }
            g.briefLists.add(list);
        }
        g.questCreationHintShown = o.optBoolean("questCreationHintShown");
        g.timelineHintShown = o.optBoolean("timelineHintShown");
        g.choiceHintShown = o.optBoolean("choiceHintShown");
        g.rearrangeVisited = o.optBoolean("rearrangeVisited");
        g.rearrangeExitHintShown = o.optBoolean("rearrangeExitHintShown");
        g.welcomeShown = o.optBoolean("welcomeShown", true);
        g.questStartHintShown = o.optBoolean("questStartHintShown");
        g.energyHintShown = o.optBoolean("energyHintShown");
        g.questAdvancedExpanded = o.optBoolean("questAdvancedExpanded");
        g.briefShowTitles = o.optBoolean("briefShowTitles", true);
        g.analyticsSince = o.optLong("analyticsSince", now);
        JSONArray focus = o.optJSONArray("focus");
        if (focus != null) for (int i = 0; i < focus.length(); i++) {
            JSONObject e = focus.getJSONObject(i); Game.FocusEntry entry=new Game.FocusEntry(e.getString("id"),e.getString("name"),e.getLong("day"),bound(e.getLong("millis")));entry.endDay=Math.max(entry.day,e.optLong("endDay",entry.day));g.focus.add(entry);
        }
        g.lastSeenAt = o.optLong("lastSeenAt", now);
        g.swipeHintShown = o.optBoolean("swipeHintShown", o.optInt("questsCreated") > 0);
        JSONArray collapsedAnalytics = o.optJSONArray("collapsedAnalyticsSections");
        if (collapsedAnalytics != null) for (int i = 0; i < collapsedAnalytics.length(); i++) g.collapsedAnalyticsSections.add(collapsedAnalytics.getString(i));
        JSONArray order = o.optJSONArray("boardOrder");
        if (order != null) for (int i = 0; i < order.length(); i++) g.boardOrder.add(order.getString(i));
        JSONArray categories = o.optJSONArray("categories");
        g.hasCreatedCategory = o.optBoolean("hasCreatedCategory") || categories != null && categories.length() > 0;
        if (categories != null) for (int i = 0; i < categories.length(); i++) {
            JSONObject v = categories.getJSONObject(i); Game.Category c = new Game.Category();
            c.id = v.getString("id"); c.name = v.getString("name"); c.hue = Math.max(-1, Math.min(359, v.optInt("hue", -1))); c.collapsed = v.optBoolean("collapsed");
            c.mustDo = v.optBoolean("mustDo"); c.requireAll = v.optBoolean("requireAll"); c.requiredTasks = Math.max(1, Math.min(999999, v.optInt("requiredTasks", 1)));
            c.progressDay = v.optLong("progressDay", Long.MIN_VALUE); c.progressCount = Math.max(0, v.optInt("progressCount"));
            c.fulfilledDay = v.optLong("fulfilledDay", Long.MIN_VALUE); c.fulfilledTarget = Math.max(0, v.optInt("fulfilledTarget"));
            JSONArray doneIds = v.optJSONArray("completedQuestIds"); if (doneIds != null) for (int j = 0; j < doneIds.length(); j++) c.completedQuestIds.add(doneIds.getString(j));
            g.categories.add(c);
        }
        g.day = o.getLong("day");
        g.firstOpenedDay = Math.min(g.day, o.optLong("firstOpenedDay", g.day));
        g.days = bound(o.getLong("days"));
        g.coins = Math.max(-Game.LIMIT, Math.min(Game.LIMIT, o.getLong("coins")));
        g.pendingCoins = Math.min(Math.max(0, g.coins), bound(o.optLong("pendingCoins")));
        g.timedMillisToday = bound(o.optLong("timedMillisToday"));
        g.pendingConfetti = Math.max(0, Math.min(2, o.optInt("pendingConfetti")));
        g.randomModeUsedMillis = Math.max(0, Math.min(10001, o.optLong("randomModeUsedMillis")));
        g.randomMode = o.optBoolean("randomMode"); g.randomDay = o.optLong("randomDay", Long.MIN_VALUE);
        JSONArray selection = o.optJSONArray("randomQuestIds");
        if (selection != null) for (int i = 0; i < Math.min(2, selection.length()); i++) g.randomQuestIds.add(selection.getString(i));
        g.nextBounty = Math.max(version < 3 ? 10 : 100, bound(o.optLong("nextBounty", version < 3 ? 10 : 100)));
        if (version < 3) {
            g.coins = Money.fromOldCoins(g.coins); g.pendingCoins = Money.fromOldCoins(g.pendingCoins);
            g.nextBounty = Money.fromOldCoins(g.nextBounty);
            g.lastSettlement = "";
        }
        g.bountyBase = o.optLong("bountyBase", g.nextBounty);
        JSONArray changes = o.optJSONArray("bountyChanges");
        if (changes != null) for (int i = 0; i < changes.length(); i++) {
            JSONObject e = changes.getJSONObject(i);
            Game.HistoryEntry change = new Game.HistoryEntry(e.getString("kind"), e.getString("id"), "", e.getLong("at"));
            change.bountyMillis = bound(e.optLong("bountyMillis")); g.bountyChanges.add(change);
        }
        g.completedToday = Math.max(0, o.optInt("completedToday"));
        g.finesToday = Math.max(0, Math.min(1000, o.optInt("finesToday")));
        g.questMinutes = Math.max(1, Math.min(240, o.optInt("questMinutes", 15)));
        g.energy = Math.max(Game.EXTRA, Math.min(Game.RELAX, o.optInt("energy", Game.NORMAL)));
        g.restMinutes = Math.max(0, Math.min(120, o.optInt("restMinutes", 4)));
        g.hue = (float) o.optDouble("hue", 186);
        if (version >= 3) g.lastSettlement = o.optString("lastSettlement");
        JSONArray quests = o.getJSONArray("quests");
        for (int i = 0; i < quests.length(); i++) {
            JSONObject v = quests.getJSONObject(i);
            Game.Quest q = decodeQuest(v, g, now);
            g.quests.add(q);
            if (version < 3 && q.completions > 0 && q.completedDay != Long.MIN_VALUE && q.completedDay <= g.day) {
                long at = LocalDate.ofEpochDay(q.completedDay).atTime(12, 0).atZone(zone).toInstant().toEpochMilli();
                Game.HistoryEntry e = new Game.HistoryEntry(Game.QUEST_HISTORY, q.id, q.name, at);
                e.count = q.completions; e.dateOnly = true; g.history.add(e);
                g.firstOpenedDay = Math.min(g.firstOpenedDay, q.completedDay);
            }
        }
        JSONArray deleted = o.optJSONArray("deletedQuests");
        if (deleted != null) for (int i = 0; i < deleted.length(); i++) g.deletedQuests.add(decodeQuest(deleted.getJSONObject(i), g, now));
        JSONArray excluded = o.optJSONArray("excludedRandomCategories");
        if (excluded != null) for (int i = 0; i < excluded.length(); i++) g.excludedRandomCategories.add(excluded.getString(i));
        JSONArray fines = o.getJSONArray("fines");
        for (int i = 0; i < fines.length(); i++) {
            JSONObject v = fines.getJSONObject(i);
            Game.Fine f = new Game.Fine(); f.id = v.getString("id"); f.name = v.getString("name");
            f.emoji = v.optString("emoji");
            f.mode = Math.max(Game.STRICT, Math.min(Game.TIMED, v.optInt("mode", Game.STRICT)));
            f.amountLimit = Math.max(1, Math.min(999999, v.optInt("amountLimit", 3)));
            f.amountUsed = Math.max(0, Math.min(Integer.MAX_VALUE - 1, v.optInt("amountUsed")));
            f.timeLimitMillis = Math.max(1000, Math.min(86400000L, v.optLong("timeLimitMillis", 300000)));
            f.remainingMillis = Math.max(0, Math.min(f.timeLimitMillis, v.optLong("remainingMillis", f.timeLimitMillis)));
            f.endsAt = f.mode == Game.TIMED ? Math.max(0, v.optLong("endsAt")) : 0;
            g.fines.add(f);
        }
        JSONObject s = o.optJSONObject("session");
        if (s != null) {
            g.session = new Game.Session();
            g.session.questId = s.getString("questId"); g.session.questName = s.getString("questName");
            g.session.energy = readEnergy(s, "energy");
            g.session.bountySince = s.optLong("bountySince", now);
            g.session.occurrenceDay = s.getLong("occurrenceDay"); g.session.endsAt = s.getLong("endsAt");
            g.session.restMinutes = Math.max(0, Math.min(120, s.getInt("restMinutes")));
            g.session.restMillis = s.optLong("restMillis", -1);
            g.session.phase = s.getInt("phase") == Game.REST ? Game.REST : Game.QUEST;
            g.session.timed = s.optBoolean("timed", true);
            boolean wasRequired = g.quests.stream().anyMatch(q -> q.id.equals(g.session.questId) && q.required);
            g.session.required = s.optBoolean("required", wasRequired);
            long dayStart = LocalDate.ofEpochDay(g.day).atStartOfDay(zone).toInstant().toEpochMilli();
            g.session.accountedAt = s.optLong("accountedAt", Math.max(dayStart, g.session.endsAt - g.questMinutes * 60_000L));
            g.session.referenceMinutes = Math.max(0, Math.min(240, s.optInt("referenceMinutes", g.questMinutes)));
            int legacyDuration = g.questMinutes;
            for (Game.Quest q : g.quests) if (q.id.equals(g.session.questId) && q.questMinutes >= 0) legacyDuration = q.questMinutes;
            g.session.rewardMinutes = Math.max(0, Math.min(240, s.optInt("rewardMinutes", g.session.timed ? legacyDuration : 0)));
            g.session.rewardMillis = s.optLong("rewardMillis", -1);
            g.session.referenceMillis = s.optLong("referenceMillis", -1);
            g.session.activeMillis = bound(s.optLong("activeMillis", g.session.timed && g.session.phase == Game.QUEST ? legacyActiveMillis(Math.max(0, g.session.endsAt - g.session.accountedAt), g.session.rewardMillis, g.session.rewardMinutes, g.session.restMillis) : 0));
        }
        JSONArray events = o.optJSONArray("history");
        if (version >= 3 && events != null) for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.getJSONObject(i);
            Game.HistoryEntry entry = new Game.HistoryEntry(e.getString("kind"), e.getString("id"), e.getString("name"), e.getLong("at"));
            entry.energy = readEnergy(e, "energy");
            entry.count = Math.max(1,bound(e.optLong("count",1))); entry.dateOnly = e.optBoolean("dateOnly");entry.precision=e.optInt("precision");entry.bucketDay=e.optLong("bucketDay",Long.MIN_VALUE);entry.endDay=e.optLong("endDay",Long.MIN_VALUE);entry.coinsDelta=Math.max(-Game.LIMIT,Math.min(Game.LIMIT,e.optLong("coinsDelta")));entry.coinsKnown=e.optBoolean("coinsKnown");g.history.add(entry);
        }
        g.balanceRangeStart = o.optLong("balanceRangeStart", 0); g.balanceRangeEnd = o.optLong("balanceRangeEnd", 0);
        g.balances.clear();
        JSONArray points = o.optJSONArray("balances");
        if (version >= 3 && points != null) for (int i = 0; i < points.length(); i++) {
            JSONObject p = points.getJSONObject(i); g.balances.add(new Game.BalancePoint(p.getLong("at"),p.getLong("cents"),p.optString("cause","Historical balance update"),p.optLong("periodStart"),p.optLong("periodEnd"),p.optLong("opening",p.getLong("cents")),p.optLong("minimum",p.getLong("cents")),p.optLong("maximum",p.getLong("cents")),p.optInt("precision")));
        }
        if (g.balances.isEmpty()) {
            long lastSavedDayEnd = LocalDate.ofEpochDay(g.day + 1).atStartOfDay(zone).toInstant().toEpochMilli() - 1;
            g.recordBalance(Math.min(now, lastSavedDayEnd));
        }
        JSONArray undo = o.optJSONArray("undo");
        if (undo != null) for (int i = 0; i < undo.length(); i++) {
            JSONObject v = undo.getJSONObject(i); Game.UndoEntry e = new Game.UndoEntry(); e.categoryId = v.optString("categoryId");
            e.questId = v.getString("questId"); e.at = v.getLong("at"); e.reward = v.getLong("reward");
            e.bountyBefore = v.getLong("bountyBefore"); e.bountyAfter = v.getLong("bountyAfter"); g.undo.add(e);
            e.previousBountySince = v.optLong("previousBountySince", -1);
        }
        JSONArray reviews = o.optJSONArray("missedReviews");
        if (reviews != null) for (int i = 0; i < reviews.length(); i++) {
            JSONObject v = reviews.getJSONObject(i); Game.MissedReview r = new Game.MissedReview();
            r.day = v.getLong("day"); r.coinsLost = v.getLong("coinsLost"); r.daysLost = v.getLong("daysLost");
            r.interestLost = v.optLong("interestLost");
            r.rentDifference = v.optLong("rentDifference");
            JSONArray ids = v.getJSONArray("ids"), names = v.getJSONArray("names");
            for (int j = 0; j < ids.length(); j++) { r.ids.add(ids.getString(j)); r.names.add(names.getString(j)); }
            g.missedReviews.add(r);
        }
        if (version < 6) recoverTodaysRestoreHistory(g, zone);
        java.util.Set<String> knownQuests = new java.util.HashSet<>(), knownLaws = new java.util.HashSet<>();
        for (Game.Quest q : g.quests) knownQuests.add(q.id);
        for (Game.Quest q : g.deletedQuests) knownQuests.add(q.id);
        for (Game.Fine f : g.fines) knownLaws.add(f.id);
        for (Game.HistoryEntry e : g.history) (e.kind.equals(Game.QUEST_HISTORY) ? knownQuests : knownLaws).add(e.id);
        g.questsCreated = Math.max(knownQuests.size(), o.optInt("questsCreated", knownQuests.size()));
        g.lawsCreated = Math.max(knownLaws.size(), o.optInt("lawsCreated", knownLaws.size()));
        g.questTimelineStart=o.optLong("questTimelineStart");g.questTimelineEnd=o.optLong("questTimelineEnd");g.lawTimelineStart=o.optLong("lawTimelineStart");g.lawTimelineEnd=o.optLong("lawTimelineEnd");g.nextEntityId=Math.max(1,o.optLong("nextEntityId",1));
        return g;
    }
    private static void recoverTodaysRestoreHistory(Game g, ZoneId zone) {
        long base = 100;
        java.util.List<Game.HistoryEntry> changes = new java.util.ArrayList<>();
        for (Game.HistoryEntry e : g.history) {
            if (Game.localDay(e.at, zone) != g.day) continue;
            if (e.kind.equals(Game.FINE_HISTORY)) { base = 100; changes.add(new Game.HistoryEntry("reset", "", "", e.at)); continue; }
            if (!e.kind.equals(Game.QUEST_HISTORY)) continue;
            Game.Quest quest = null;
            for (Game.Quest q : g.quests) if (q.id.equals(e.id)) { quest = q; break; }
            boolean required = quest != null && quest.required;
            long before = base;
            if (!required) { base = Game.raiseBounty(base); changes.add(new Game.HistoryEntry("raise", e.id, "", e.at)); }
            if (quest != null && !quest.repeatable && quest.done(g.day)) {
                boolean recorded = false;
                for (Game.UndoEntry old : g.undo) if (old.questId.equals(quest.id)) { recorded = true; break; }
                if (!recorded) {
                    Game.UndoEntry restored = new Game.UndoEntry(); restored.questId = quest.id; restored.at = e.at;
                    restored.reward = Game.scaledBounty(before, required, 0, g.questMinutes);
                    restored.bountyBefore = before; restored.bountyAfter = base; g.undo.add(restored);
                }
            }
        }
        // Unknown deleted legacy quests may have been required; only use a reconstruction matching the saved bounty.
        if (base == g.nextBounty) { g.bountyBase = 100; g.bountyChanges.clear(); g.bountyChanges.addAll(changes); }
    }
    private static JSONObject encodeQuest(Game.Quest q) throws JSONException {
        return new JSONObject().put("id", q.id).put("name", q.name)
                .put("bountySince", q.bountySince)
                .put("hue", q.hue).put("emoji", q.emoji).put("categoryId", q.categoryId)
                .put("repeatable", q.repeatable).put("required", q.required).put("forever", q.forever).put("timed", q.timed)
                .put("interval", q.interval).put("firstDay", q.firstDay).put("questMinutes", q.questMinutes)
                .put("remainingMillis", q.remainingMillis).put("pausedRewardMinutes", q.pausedRewardMinutes).put("pausedReferenceMinutes", q.pausedReferenceMinutes)
                .put("pausedRewardMillis", q.pausedRewardMillis).put("pausedReferenceMillis", q.pausedReferenceMillis)
                .put("pausedRestMillis", q.pausedRestMillis).put("pausedActiveMillis", q.remainingMillis > 0 ? q.pausedActiveMillis : null).put("pausedEnergy", q.pausedEnergy < 0 ? null : q.pausedEnergy)
                .put("completedDay", q.completedDay).put("completions", q.completions)
                .put("prerequisites", new JSONArray(q.prerequisites));
    }
    private static Game.Quest decodeQuest(JSONObject v, Game g, long now) throws JSONException {
            Game.Quest q = new Game.Quest();
            q.id = v.getString("id"); q.name = v.getString("name");
            q.categoryId = v.optString("categoryId"); if (g.category(q.categoryId) == null) q.categoryId = "";
            q.bountySince = v.optLong("bountySince", now);
            q.hue = Math.max(-1, Math.min(359, v.optInt("hue", -1))); q.emoji = v.optString("emoji");
            q.repeatable = v.getBoolean("repeatable"); q.required = v.getBoolean("required"); q.timed = v.optBoolean("timed");
            q.forever = v.getBoolean("forever"); q.interval = Math.max(1, v.getInt("interval"));
            q.questMinutes = Math.max(-1, Math.min(240, v.optInt("questMinutes", -1)));
            q.remainingMillis = Math.max(-1, Math.min(480 * 60_000L, v.optLong("remainingMillis", -1)));
            q.pausedRewardMillis = v.optLong("pausedRewardMillis", -1);
            q.pausedReferenceMillis = v.optLong("pausedReferenceMillis", -1);
            q.pausedEnergy = readEnergy(v, "pausedEnergy");
            q.pausedRestMillis = v.optLong("pausedRestMillis", q.remainingMillis > 0 ? g.restMinutes * 60_000L : -1);
            if (q.remainingMillis == 0) q.remainingMillis = -1;
            if (q.remainingMillis > 0) {
                q.pausedRewardMinutes = Math.max(0, Math.min(240, v.optInt("pausedRewardMinutes", q.questMinutes < 0 ? g.questMinutes : q.questMinutes)));
                q.pausedReferenceMinutes = Math.max(0, Math.min(240, v.optInt("pausedReferenceMinutes", g.questMinutes)));
            }
            q.pausedActiveMillis = q.remainingMillis > 0 ? bound(v.optLong("pausedActiveMillis", legacyActiveMillis(q.remainingMillis, q.pausedRewardMillis, q.pausedRewardMinutes, q.pausedRestMillis))) : 0;
            q.firstDay = v.getLong("firstDay"); q.completedDay = v.getLong("completedDay");
            q.completions = v.getInt("completions");
            JSONArray lines = v.getJSONArray("prerequisites");
            for (int j = 0; j < lines.length(); j++) q.prerequisites.add(lines.getString(j));

        return q;
    }
    private static long legacyActiveMillis(long remaining, long rewardMillis, int rewardMinutes, long restMillis) {
        long duration = rewardMillis >= 1000 ? rewardMillis : rewardMinutes > 0 ? rewardMinutes * 60000L : restMillis == 10000 ? 20000 : 0;
        return Math.max(0, duration - remaining);
    }
    private static long bound(long n) { return Math.max(0, Math.min(Game.LIMIT, n)); }
}
