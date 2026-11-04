package com.bulletin.app;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;

/** Pure Java rules. All events are processed chronologically, including offline midnights. */
public final class Game {
    public static final class ChoiceList {
        public String id = UUID.randomUUID().toString(), name = "";
        public final List<String> items = new ArrayList<>();
    }
    public final List<ChoiceList> choiceLists = new ArrayList<>();
    public final List<ChoiceList> briefLists = new ArrayList<>();
    public boolean questAdvancedExpanded, hasCreatedCategory, energyHintShown, questStartHintShown, welcomeShown, questCreationHintShown, timelineHintShown, choiceHintShown, rearrangeVisited, rearrangeExitHintShown;
    public boolean briefShowTitles = true;
    public void addBriefList(ChoiceList list) { if (isUuid(list.id)) list.id = shortId("b"); briefLists.add(list); }
    public void addChoiceList(ChoiceList list) { if (isUuid(list.id)) list.id = shortId("r"); choiceLists.add(list); }
    void addDemoLists() {
        ChoiceList exercises = new ChoiceList(); exercises.name = "Exercises (Demo)";
        java.util.Collections.addAll(exercises.items, "Push-ups", "Squats", "Lunges", "Plank", "Jumping Jacks", "Glute Bridges", "Mountain Climbers"); addChoiceList(exercises);
        ChoiceList recipes = new ChoiceList(); recipes.name = "Recipes (Demo)";
        java.util.Collections.addAll(recipes.items, "Vegetable Stir-fry", "Lentil Soup", "Tomato Pasta", "Chickpea Curry", "Mushroom Risotto"); addChoiceList(recipes);
        ChoiceList landscapes = new ChoiceList(); landscapes.name = "Landscapes (Demo)";
        java.util.Collections.addAll(landscapes.items, "Location", "Time Period", "Architecture"); addBriefList(landscapes);
        ChoiceList people = new ChoiceList(); people.name = "People (Demo)";
        java.util.Collections.addAll(people.items, "Body Type", "Age", "Clothes Style", "Personality"); addBriefList(people);
    }
    public static final long LIMIT = 9_000_000_000_000_000L;
    public static final int QUEST = 1, REST = 2;
    public static final int EXTRA = 0, NORMAL = 1, MINIMAL = 2, DEAD = 3, EXTREME = 4, RELAX = 5;
    public static final int LOW = MINIMAL; // Legacy saved value.
    public static final long NORMAL_DURATION = 25 * 60_000L;
    public static final String QUEST_HISTORY = "quest", FINE_HISTORY = "fine";
    public static final int STRICT = 0, AMOUNT = 1, TIMED = 2;

    public static final class Quest {
        public String id = UUID.randomUUID().toString();
        public String name = "";
        public String emoji = "";
        public String categoryId = "";
        public int hue = -1;
        public boolean repeatable, required, forever, timed;
        public int interval = 1;
        /** Legacy per-quest duration, retained for old paused sessions; new sessions use Settings. */
        public int questMinutes = -1;
        public long remainingMillis = -1;
        public int pausedRewardMinutes = -1, pausedReferenceMinutes = -1;
        public long pausedRewardMillis = -1, pausedReferenceMillis = -1;
        public long pausedRestMillis = -1;
        public long pausedActiveMillis;
        public int pausedEnergy = -1;
        public long firstDay;
        public long bountySince = -1;
        public long completedDay = Long.MIN_VALUE;
        public int completions;
        public final List<String> prerequisites = new ArrayList<>();
        public boolean due(long day) {
            return day >= firstDay && (forever ? (day - firstDay) % interval == 0 : day == firstDay);
        }
        public boolean done(long day) { return completedDay == day && completions > 0; }
    }

    public static final class Fine {
        public String id = UUID.randomUUID().toString();
        public String name = "";
        public String emoji = "";
        public int mode = STRICT, amountLimit = 3, amountUsed;
        public long timeLimitMillis = 5 * 60_000L, remainingMillis = 5 * 60_000L, endsAt;
        public long remaining(long now) { return endsAt > 0 ? Math.max(0, endsAt - now) : remainingMillis; }
    }
    public boolean useLawAmount(Fine law) {
        if (!fines.contains(law) || law.mode != AMOUNT || law.amountUsed >= law.amountLimit) return false;
        law.amountUsed++; return true;
    }
    public boolean toggleLawTimer(Fine law, long now) {
        if (!fines.contains(law) || law.mode != TIMED) return false;
        long remaining = law.remaining(now);
        if (remaining == 0) return false;
        if (law.endsAt > 0) { law.remainingMillis = remaining; law.endsAt = 0; }
        else law.endsAt = now + remaining;
        return true;
    }
    public void configureLaw(Fine law, int mode, int amount, long duration, long now) {
        long usedTime = law.mode == TIMED ? Math.max(0, law.timeLimitMillis - law.remaining(now)) : 0;
        boolean running = law.mode == TIMED && law.endsAt > 0;
        if (law.mode != mode) law.amountUsed = 0;
        law.mode = mode; law.amountLimit = Math.max(1, amount); law.timeLimitMillis = Math.max(1000, duration);
        law.remainingMillis = mode == TIMED ? Math.max(0, law.timeLimitMillis - usedTime) : law.timeLimitMillis;
        law.endsAt = mode == TIMED && running && law.remainingMillis > 0 ? now + law.remainingMillis : 0;
    }
    public Fine nextRunningLaw() {
        Fine next = null; for (Fine law : fines) if (law.mode == TIMED && law.endsAt > 0 && (next == null || law.endsAt < next.endsAt)) next = law;
        return next;
    }
    public boolean hasRunningTimers() { return session != null && (session.timed || session.phase == REST) || nextRunningLaw() != null; }
    public long lastTimerDeadline() {
        long deadline = session != null && (session.timed || session.phase == REST) ? session.endsAt + (session.phase == QUEST ? restFor(session) : 0) : 0;
        for (Fine law : fines) if (law.mode == TIMED) deadline = Math.max(deadline, law.endsAt);
        return deadline;
    }
    public static final class Category {
        public String id = UUID.randomUUID().toString(), name = "";
        public int hue = -1;
        public boolean collapsed;
        public boolean mustDo, requireAll;
        public int requiredTasks = 1, progressCount, fulfilledTarget;
        public long progressDay = Long.MIN_VALUE, fulfilledDay = Long.MIN_VALUE;
        public final List<String> completedQuestIds = new ArrayList<>();
    }
    public final List<Category> categories = new ArrayList<>();
    /** Top-level order: category IDs and ungrouped quest IDs. Required quests always lead. */
    public final List<String> boardOrder = new ArrayList<>();
    public boolean swipeHintShown;
    public long lastSeenAt;
    public Category category(String id) { for (Category c : categories) if (c.id.equals(id)) return c; return null; }
    public int categoryNeeded(Category c, long onDay) {
        if (!c.mustDo) return 0;
        if (c.fulfilledDay == onDay) return c.fulfilledTarget;
        int count = 0; for (Quest q : quests) if (q.categoryId.equals(c.id) && q.due(onDay)) count++;
        return count == 0 ? 0 : c.requireAll ? count : c.requiredTasks;
    }
    public int categoryDone(Category c, long onDay) {
        if (c.fulfilledDay == onDay) return c.fulfilledTarget;
        if (c.progressDay != onDay) return 0;
        int done = c.progressCount;
        if (c.requireAll) { done = 0; for (Quest q : quests) if (q.categoryId.equals(c.id) && q.due(onDay) && c.completedQuestIds.contains(q.id)) done++; }
        return Math.min(categoryNeeded(c, onDay), done);
    }
    public int categoryRemaining(Category c, long onDay) { return Math.max(0, categoryNeeded(c, onDay) - categoryDone(c, onDay)); }
    public boolean categoryActive(Category c) { return categoryRemaining(c, day) > 0; }
    public String categoryTitle(Category c) { return c.name + (c.mustDo ? " (" + categoryDone(c, day) + "/" + categoryNeeded(c, day) + ")" : ""); }
    public void refreshCategory(Category c) {
        if (c == null || !c.mustDo) return;
        int needed = categoryNeeded(c, day);
        if (needed > 0 && categoryRemaining(c, day) == 0) { c.fulfilledDay = day; c.fulfilledTarget = needed; }
    }
    public boolean standaloneRequired(Quest q) { Category c = category(q.categoryId); return q.required && (c == null || !c.mustDo); }
    public boolean isRequired(Quest q) {
        Category c = category(q.categoryId);
        if (c != null && c.mustDo) return q.due(day) && categoryActive(c) && (!c.requireAll || c.progressDay != day || !c.completedQuestIds.contains(q.id));
        return q.required;
    }
    private void creditCategory(Quest q, long onDay) {
        Category c = category(q.categoryId); if (c == null || onDay != day) return;
        if (c.progressDay != onDay) { c.progressDay = onDay; c.progressCount = 0; c.completedQuestIds.clear(); }
        c.progressCount = Math.min(Integer.MAX_VALUE - 1, c.progressCount + 1); if (!c.completedQuestIds.contains(q.id)) c.completedQuestIds.add(q.id);
        int needed = categoryNeeded(c, onDay);
        if (c.mustDo && needed > 0 && categoryRemaining(c, onDay) == 0) { c.fulfilledDay = onDay; c.fulfilledTarget = needed; }
    }
    public void addCategory(Category c) { hasCreatedCategory = true; if(isUuid(c.id))c.id=shortId("c"); categories.add(c); boardOrder.add(0, c.id); }
    public float questHue(Quest q) {
        Category c = q == null ? null : category(q.categoryId);
        if (c != null && c.hue >= 0) return c.hue;
        return q != null && q.hue >= 0 ? q.hue : q != null && isRequired(q) ? 145 : hue;
    }
    public void assignCategory(Quest q, String id) {
        if (q.categoryId.equals(id)) return;
        Category c = category(id);
        q.categoryId = c == null ? "" : c.id;
        boardOrder.remove(q.id);
        if (c == null) boardOrder.add(q.id);
    }
    public void moveToCategoryEnd(Quest q, String categoryId) {
        if (!quests.contains(q)) return;
        assignCategory(q, categoryId);
        quests.remove(q); quests.add(q);
        if (q.categoryId.isEmpty()) { boardOrder.remove(q.id); boardOrder.add(q.id); }
    }
    public void deleteCategory(Category c) {
        if (!categories.remove(c)) return;
        excludedRandomCategories.remove(c.id);
        int index = boardOrder.indexOf(c.id); if (index < 0) index = boardOrder.size();
        boardOrder.remove(c.id);
        for (Quest q : quests) if (q.categoryId.equals(c.id)) {
            q.categoryId = ""; boardOrder.remove(q.id); boardOrder.add(Math.min(index++, boardOrder.size()), q.id);
        }
    }
    public boolean moveLaw(String sourceId, String targetId) {
        int from = -1, to = -1;
        for (int i = 0; i < fines.size(); i++) { if (fines.get(i).id.equals(sourceId)) from = i; if (fines.get(i).id.equals(targetId)) to = i; }
        if (from < 0 || to < 0 || from == to) return false;
        Fine law = fines.remove(from); fines.add(to, law); return true;
    }
    public boolean moveBoardItem(String source, String target) {
        if (source.equals(target)) return false;
        if (!boardOrder.contains(source) || !boardOrder.contains(target)) return false;
        int index = boardOrder.indexOf(target); boardOrder.remove(source); boardOrder.add(Math.min(index, boardOrder.size()), source); return true;
    }
    public List<String> orderedBoardItems() {
        List<String> result = new ArrayList<>(boardOrder);
        for (Category c : categories) if (!result.contains(c.id)) result.add(c.id);
        for (Quest q : quests) if (q.categoryId.isEmpty() && !result.contains(q.id)) result.add(q.id);
        return result;
    }

    public static final class Session {
        public String questId, questName;
        public long occurrenceDay, endsAt;
        public long accountedAt, activeMillis;
        public boolean timed = true, required;
        public int rewardMinutes, referenceMinutes;
        public long rewardMillis = -1, referenceMillis = -1;
        public long bountySince = -1;
        public int restMinutes, phase = QUEST;
        public int energy = -1;
        public long restMillis = -1;
    }

    public static final class Penalty {
        public final long days;
        public final int percent;
        public final long minimum;
        private final long debtFloor;
        public Penalty(long days, int percent) { this(days, percent, 0); }
        public Penalty(long days, int percent, long minimum) { this(days, percent, minimum, Long.MIN_VALUE); }
        private Penalty(long days, int percent, long minimum, long debtFloor) {
            this.days = days; this.percent = percent; this.minimum = minimum; this.debtFloor = debtFloor;
        }
        public long coins(long balance) {
            if (days == 0 && percent == 0 && minimum == 0) return 0;
            if (balance <= 0) return debtFloor == Long.MIN_VALUE ? 1000 : Math.min(1000, Math.max(0, balance - debtFloor));
            return Math.min(balance, Math.max(minimum, (balance / 100) * percent + ((balance % 100) * percent + 99) / 100));
        }
        public long daysLost(long balance) { return Math.min(balance, days); }
    }

    public static final class Event {
        public final String type;
        public final long at;
        public final String id;
        public Event(String type, long at) { this(type, at, ""); }
        public Event(String type, long at, String id) { this.type = type; this.at = at; this.id = id; }
    }
    public static final class HistoryEntry {
        public String kind, id, name;
        public long at;
        public long count = 1, coinsDelta;
        public boolean coinsKnown;
        public long bountyMillis;
        public int energy = -1;
        public int precision;
        public long bucketDay = Long.MIN_VALUE, endDay = Long.MIN_VALUE;
        public boolean dateOnly;
        public HistoryEntry(String kind, String id, String name, long at) { this.kind = kind; this.id = id; this.name = name; this.at = at; }
    }
    public static final class BalancePoint {
        public final long at, cents;
        public final String cause;
        public final long periodStart, periodEnd, opening, minimum, maximum;
        public final int precision;
        public BalancePoint(long at, long cents) { this(at, cents, "Balance update"); }
        public BalancePoint(long at, long cents, String cause) { this(at,cents,cause,0,0,cents,cents,cents); }
        public BalancePoint(long at,long cents,String cause,long start,long end,long opening,long minimum,long maximum) {
            this(at,cents,cause,start,end,opening,minimum,maximum,start==0?0:end-start>2*86400000L?7:1);
        }
        public BalancePoint(long at,long cents,String cause,long start,long end,long opening,long minimum,long maximum,int precision) {
            this.precision=precision;this.at=at;this.cents=cents;this.periodStart=start;this.periodEnd=end;this.opening=opening;this.minimum=minimum;this.maximum=maximum;
            this.cause=start==0?cause:(precision==7?"Weekly":"Daily")+" summary: open "+Money.format(opening)+", low "+Money.format(minimum)+", high "+Money.format(maximum)+", close "+Money.format(cents);
        }
    }
    public final List<HistoryEntry> history = new ArrayList<>();
    public static final class FocusEntry {
        public String id, name;
        public long day, millis, endDay;
        public FocusEntry(String id, String name, long day, long millis) { this.id = id; this.name = name; this.day = day; this.endDay = day; this.millis = millis; }
    }
    public final List<FocusEntry> focus = new ArrayList<>();
    public long analyticsSince;
    public final List<BalancePoint> balances = new ArrayList<>();
    public long balanceRangeStart, balanceRangeEnd;
    public long questTimelineStart, questTimelineEnd, lawTimelineStart, lawTimelineEnd;
    public long nextEntityId = 1;
    long aggregationDay=Long.MIN_VALUE;
    public static final class UndoEntry {
        public String questId;
        public String categoryId = "";
        public long at, reward, bountyBefore, bountyAfter;
        public long previousBountySince = -1;
    }
    public static final class MissedReview {
        public long day, coinsLost, daysLost, interestLost, rentDifference;
        public final List<String> ids = new ArrayList<>(), names = new ArrayList<>();
    }
    public final List<UndoEntry> undo = new ArrayList<>();
    public final List<HistoryEntry> bountyChanges = new ArrayList<>();
    public long bountyBase = 100;
    public final List<MissedReview> missedReviews = new ArrayList<>();
    /** A durable celebration waits until the bulletin board is visible. */
    public int pendingConfetti;
    public boolean randomMode;
    public long randomModeUsedMillis;
    public long randomDay = Long.MIN_VALUE;
    public final List<String> randomQuestIds = new ArrayList<>();
    /** New categories are selected by default; an empty category ID means uncategorized. */
    public final List<String> excludedRandomCategories = new ArrayList<>();
    public final List<String> collapsedAnalyticsSections = new ArrayList<>();
    public final List<Quest> deletedQuests = new ArrayList<>();
    public long firstOpenedDay;

    public final List<Quest> quests = new ArrayList<>();
    public final List<Fine> fines = new ArrayList<>();
    public long day, days, coins, pendingCoins;
    public long timedMillisToday;
    public long nextBounty = 100;
    public int completedToday, finesToday;
    public int questMinutes = 25, restMinutes = 5; // Legacy save fallback only; new sessions use fixed presets.
    public int energy = NORMAL;
    public int questsCreated, lawsCreated;
    public float hue = 186;
    public Session session;
    public String lastSettlement = "";
    private long observedAt;

    public Game(long now, ZoneId zone) { observedAt = now; analyticsSince = now; day = localDay(now, zone); firstOpenedDay = day; recordBalance(now); }
    public static long localDay(long now, ZoneId zone) {
        return Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay();
    }
    public static long add(long a, long b) { return Math.min(LIMIT, a + b); }
    /** Only completed optional focus time advances the base; legacy raises remain earned. */
    private void rebuildBounty() {
        nextBounty = bountyBase; long remainder = 0;
        for (HistoryEntry change : bountyChanges) {
            if (change.kind.equals("reset")) { nextBounty = 100; remainder = 0; }
            else if (change.kind.equals("raise")) nextBounty = raiseBounty(nextBounty);
            else if (change.kind.equals("time")) {
                long total = add(remainder, change.bountyMillis); remainder = total % NORMAL_DURATION;
                for (long increases = total / NORMAL_DURATION; increases > 0 && nextBounty < LIMIT; increases--) nextBounty = raiseBounty(nextBounty);
            }
        }
    }
    public long bountyProgressMillis() {
        long remainder = 0;
        for (HistoryEntry change : bountyChanges) {
            if (change.kind.equals("reset")) remainder = 0;
            else if (change.kind.equals("time")) remainder = add(remainder, change.bountyMillis) % NORMAL_DURATION;
        }
        return remainder;
    }
    public static long raiseBounty(long amount) { return add(amount, (amount + 9) / 10); }
    public static long dailyInterest(long balance) { return balance <= 0 ? 0 : (balance + 2499) / 2500; }
    public int minutesFor(Quest q) { return q.repeatable || q.timed ? (int) (energyDurationMillis() / 60_000) : 0; }
    public long energyDurationMillis() {
        switch (energy) {
            case EXTREME: return 90 * 60_000L;
            case EXTRA: return 45 * 60_000L;
            case RELAX: return 15 * 60_000L;
            case MINIMAL: return 7 * 60_000L;
            case DEAD: return 120_000;
            default: return NORMAL_DURATION;
        }
    }
    public long energyRestMillis() {
        switch (energy) {
            case EXTREME: return 20 * 60_000L;
            case EXTRA: return 10 * 60_000L;
            case RELAX: return 4 * 60_000L;
            case MINIMAL: return 2 * 60_000L;
            case DEAD: return 40_000;
            default: return 5 * 60_000L;
        }
    }
    public static long restFor(Session session) { return session.restMillis >= 0 ? session.restMillis : session.restMinutes * 60_000L; }
    public long durationFor(Quest q) { return q.repeatable || q.timed ? energyDurationMillis() : 0; }
    public boolean isTimed(Quest q) { return q.remainingMillis > 0 || durationFor(q) > 0; }
    public long remainingFor(Quest q, long now) {
        if (session != null && session.phase == QUEST && session.timed && session.questId.equals(q.id)) return Math.max(0, session.endsAt - now);
        return q.remainingMillis > 0 ? q.remainingMillis : durationFor(q);
    }
    public long bountyFor(Quest q) {
        return bountyFor(q, observedAt);
    }
    public long bountyFor(Quest q, long now) {
        long baseline = baselineBountyFor(q);
        return agedBounty(baseline, q.bountySince, now);
    }
    private long baselineBountyFor(Quest q) {
        boolean required = isRequired(q);
        if (!q.repeatable && !required) return proportionalBounty(nextBounty, false, 3, 10);
        if (q.pausedRewardMillis >= 0) return proportionalBounty(nextBounty, required, q.pausedRewardMillis, q.pausedReferenceMillis);
        if (q.pausedRewardMinutes >= 0) return scaledBounty(nextBounty, required, q.pausedRewardMinutes, q.pausedReferenceMinutes);
        return proportionalBounty(nextBounty, required, energyDurationMillis(), NORMAL_DURATION);
    }
    public long activeBounty() {
        return activeBounty(observedAt);
    }
    public long activeBounty(long now) {
        if (session == null) return previewBounty();
        long baseline = session.rewardMillis >= 0 ? proportionalBounty(nextBounty, session.required, session.rewardMillis, session.referenceMillis)
            : scaledBounty(nextBounty, session.required, session.rewardMinutes, session.referenceMinutes);
        return agedBounty(baseline, session.bountySince, now);
    }
    public static long agedBounty(long baseline, long since, long now) {
        if (since < 0 || now <= since) return baseline;
        long hours = (now - since) / 3_600_000L;
        double multiplier = Math.min(7, Math.pow(1.012, hours));
        return Math.min(LIMIT, (long) Math.ceil(Math.min((double) LIMIT, baseline * multiplier)));
    }
    public long previewBounty() { return proportionalBounty(nextBounty, false, energyDurationMillis(), NORMAL_DURATION); }
    public static long proportionalBounty(long base, boolean required, long duration, long reference) {
        long denominator = Math.max(1, reference) * (required ? 10 : 1);
        return Math.min(LIMIT, (base / denominator) * duration + ((base % denominator) * duration + denominator - 1) / denominator);
    }
    public static long scaledBounty(long base, boolean required, int minutes, int reference) {
        int numerator = 1, denominator = required ? 10 : 1;
        if (minutes > 0 && reference > 0 && minutes < reference) { numerator = minutes; denominator *= reference; }
        return (base * numerator + denominator - 1) / denominator;
    }
    public void recordBalance(long at) {
        recordBalance(at, balances.isEmpty() ? "Starting balance" : "Balance update");
    }
    public void recordBalance(long at, String cause) {
        if (!balances.isEmpty() && balances.get(balances.size() - 1).cents == coins) return;
        if (!balances.isEmpty()) at = Math.max(at, balances.get(balances.size() - 1).at);
        balances.add(new BalancePoint(at, coins, cause));
    }
    public long timelineStart(long today) { return Math.min(firstOpenedDay,today); }
    public Map<String, String> timelineEntities(String kind) {
        Map<String, String> result = new LinkedHashMap<>();
        if (kind.equals(QUEST_HISTORY)) for (Quest q : quests) result.put(q.id, q.name);
        else for (Fine f : fines) result.put(f.id, f.name);
        for (HistoryEntry entry : history) if (kind.equals(entry.kind)) result.putIfAbsent(entry.id, entry.name);
        return result;
    }
    public long focusMillisToday(long now) {
        return timedMillisToday + (session != null && session.phase == QUEST && session.timed
            ? Math.max(0, Math.min(now, session.endsAt) - session.accountedAt) : 0);
    }
    private void accountTime(long until) {
        if (session != null && session.phase == QUEST && session.timed) {
            long end = Math.min(until, session.endsAt);
            long elapsed = Math.max(0, end - session.accountedAt);
            timedMillisToday += elapsed;
            session.activeMillis = add(session.activeMillis, elapsed);
            if (elapsed > 0) {
                FocusEntry entry = null;
                for (int i = focus.size() - 1; i >= 0; i--) { FocusEntry candidate = focus.get(i); if (candidate.day == day && candidate.id.equals(session.questId)) { entry = candidate; break; } }
                if (entry == null) { entry = new FocusEntry(session.questId, session.questName, day, 0); focus.add(entry); }
                entry.millis = add(entry.millis, elapsed);
            }
            session.accountedAt = Math.max(session.accountedAt, end);
        }
    }

    public Penalty requiredPenalty() { return requiredPenalty(day); }
    private Penalty requiredPenalty(long onDay) {
        long n = 0;
        for (Quest q : quests) { Category c = category(q.categoryId); if (q.required && (c == null || !c.mustDo) && q.due(onDay) && !q.done(onDay)) n++; }
        for (Category c : categories) n += categoryRemaining(c, onDay);
        n = Math.min(100000000L, n);
        long lostDays = Math.min(LIMIT, n * (n + 3) / 2);
        return new Penalty(lostDays, (int) Math.min(100, n * (n + 4)), Math.min(LIMIT, lostDays * 1000), -15000);
    }
    public Penalty nextFine() {
        long factor = 1L << Math.min(finesToday, 52);
        return new Penalty(Math.min(LIMIT, 2 * factor), finesToday >= 4 ? 100 : (int) (10 * factor), factor > LIMIT / 2000 ? LIMIT : factor * 2000);
    }
    public void incurFine() {
        if (bountyChanges.isEmpty()) bountyBase = nextBounty;
        bountyChanges.add(new HistoryEntry("reset", "", "", 0));
        apply(nextFine());
        nextBounty = 100;
        finesToday = Math.min(1000, finesToday + 1);
    }
    public void incurFine(Fine fine, long now) {
        if (!fines.contains(fine)) return;
        if (fine.mode == AMOUNT) fine.amountUsed = Math.min(Integer.MAX_VALUE - 1, fine.amountUsed + 1);
        long before=coins; incurFine(); HistoryEntry event=new HistoryEntry(FINE_HISTORY,fine.id,fine.name,now);event.coinsDelta=coins-before;event.coinsKnown=true;history.add(event);recordBalance(now,"Law broken: "+fine.name);
    }
    private void apply(Penalty p) {
        days = Math.max(0, days - p.days);
        coins = Math.max(-LIMIT, coins - p.coins(coins));
        pendingCoins = Math.min(pendingCoins, Math.max(0, coins));
    }
    public boolean canStart(Quest q) {
        return session == null && quests.contains(q) && q.due(day) && (q.repeatable || !q.done(day));
    }
    public boolean start(Quest q, long now) {
        observedAt = now;
        if (!canStart(q)) return false;
        session = new Session();
        session.questId = q.id;
        session.questName = q.name;
        session.activeMillis = q.remainingMillis > 0 ? q.pausedActiveMillis : 0;
        session.energy = q.remainingMillis > 0 ? q.pausedEnergy : energy;
        session.occurrenceDay = day;
        session.required = isRequired(q);
        session.bountySince = q.bountySince;
        session.timed = isTimed(q);
        session.rewardMinutes = q.pausedRewardMinutes >= 0 ? q.pausedRewardMinutes : minutesFor(q);
        session.referenceMinutes = q.pausedReferenceMinutes >= 0 ? q.pausedReferenceMinutes : 25;
        if (q.remainingMillis > 0 && q.pausedRewardMillis < 0) {
            session.rewardMillis = -1; session.referenceMillis = -1;
        } else {
            session.rewardMillis = q.pausedRewardMillis >= 0 ? q.pausedRewardMillis : energyDurationMillis();
            session.referenceMillis = q.pausedReferenceMillis >= 0 ? q.pausedReferenceMillis : NORMAL_DURATION;
        }
        session.accountedAt = now;
        session.endsAt = session.timed ? now + (q.remainingMillis > 0 ? q.remainingMillis : durationFor(q)) : Long.MAX_VALUE;
        session.restMillis = session.timed ? q.pausedRestMillis >= 0 ? q.pausedRestMillis : energyRestMillis() : 0;
        session.restMinutes = (int) (session.restMillis / 60_000);
        if (!q.repeatable && !session.required) { session.rewardMillis = 3; session.referenceMillis = 10; }
        return true;
    }
    public List<Event> completeInstant(Quest q, long now) {
        if (isTimed(q) || !start(q, now)) return new ArrayList<>();
        return completeManual(now);
    }

    public List<Event> advance(long now, ZoneId zone) {
        observedAt = now;
        List<Event> events = new ArrayList<>();
        while (true) {
            long midnight = LocalDate.ofEpochDay(day + 1).atStartOfDay(zone).toInstant().toEpochMilli();
            long timer = session == null ? Long.MAX_VALUE : session.endsAt;
            Fine timedLaw = nextRunningLaw(); long lawEnd = timedLaw == null ? Long.MAX_VALUE : timedLaw.endsAt;
            long next = Math.min(lawEnd, Math.min(midnight, timer));
            if (next > now) break;
            accountTime(next);
            if (lawEnd <= midnight && lawEnd <= timer) {
                timedLaw.remainingMillis = 0; timedLaw.endsAt = 0; events.add(new Event("lawLimit", lawEnd, timedLaw.id)); continue;
            }
            // A completion exactly at midnight belongs to the new day.
            if (midnight <= timer) {
                Penalty penalty = requiredPenalty(day);
                if (penalty.days == 0 && completedToday > 0) {
                    days = add(days, 1);
                    lastSettlement = "All required quests completed. +1 day.";
                } else if (penalty.days > 0) {
                    long lostDays = penalty.daysLost(days), lostCoins = penalty.coins(coins);
                    MissedReview review = new MissedReview(); review.day = day;
                    review.daysLost = lostDays; review.coinsLost = lostCoins;
                    review.interestLost = dailyInterest(coins) - dailyInterest(coins - lostCoins);
                    review.rentDifference = rentFor(add(coins, dailyInterest(coins))) - rentFor(add(coins - lostCoins, dailyInterest(coins - lostCoins)));
                    for (Quest q : quests) if (q.required && (category(q.categoryId) == null || !category(q.categoryId).mustDo) && q.due(day) && !q.done(day)) {
                        review.ids.add(q.id); review.names.add(q.name);
                    }
                    for (Category c : categories) if (categoryRemaining(c, day) > 0) { review.ids.add("category:" + c.id); review.names.add(c.name + " (" + categoryDone(c, day) + "/" + categoryNeeded(c, day) + ")"); }
                    missedReviews.add(review);
                    apply(penalty);
                    recordBalance(midnight, "Unfinished must do quests: " + String.join(", ", review.names));
                    lastSettlement = "Unfinished required quests: -" + lostDays + " days, " + Money.format(-lostCoins) + ".";
                } else lastSettlement = "No quests completed. No day earned.";
                long interest = dailyInterest(coins);
                coins = add(coins, interest);
                recordBalance(midnight, "Daily interest");
                coins -= rentFor(coins);
                pendingCoins = Math.min(pendingCoins, Math.max(0, coins));
                recordBalance(midnight, "Daily rent");
                day++;
                for (Category c : categories) { c.progressDay = day; c.progressCount = 0; c.completedQuestIds.clear(); c.fulfilledDay = Long.MIN_VALUE; c.fulfilledTarget = 0; }
                timedMillisToday = 0;
                completedToday = 0;
                nextBounty = 100;
                finesToday = 0;
                for (Fine law : fines) {
                    law.amountUsed = 0; law.remainingMillis = law.timeLimitMillis;
                    if (law.endsAt > 0) law.endsAt = midnight + law.timeLimitMillis;
                }
                for (Quest q : quests) if (isPaused(q)) resetQuestTimer(q);
                undo.clear();
                bountyChanges.clear(); bountyBase = 100;
                quests.removeIf(q -> !q.forever && q.firstDay < day);
                events.add(new Event("day", midnight));
            } else if (session.phase == QUEST) {
                finish(timer, events);
            } else {
                session = null;
                events.add(new Event("ready", timer));
            }
        }
        return events;
    }
    public List<Event> completeManual(long now) {
        observedAt = now;
        List<Event> events = new ArrayList<>();
        if (session != null && session.phase == QUEST && !session.timed) finish(now, events);
        return events;
    }
    public boolean pauseQuest(long now) {
        if (session == null || session.phase != QUEST) return false;
        if (session.timed && now >= session.endsAt) return false;
        accountTime(now);
        if (session.timed) for (Quest q : quests) if (q.id.equals(session.questId)) {
            q.remainingMillis = session.endsAt - now;
            q.pausedRewardMinutes = session.rewardMinutes; q.pausedReferenceMinutes = session.referenceMinutes;
            q.pausedRewardMillis = session.rewardMillis; q.pausedReferenceMillis = session.referenceMillis;
            q.pausedRestMillis = restFor(session);
            q.pausedEnergy = session.energy;
            q.pausedActiveMillis = session.activeMillis;
            break;
        }
        session = null; return true;
    }
    public boolean isPaused(Quest q) {
        return quests.contains(q) && q.remainingMillis > 0
            && (session == null || session.phase != QUEST || !session.questId.equals(q.id));
    }
    private void resetQuestTimer(Quest q) {
        q.remainingMillis = -1; q.pausedRewardMinutes = -1; q.pausedReferenceMinutes = -1;
        q.pausedRewardMillis = -1; q.pausedReferenceMillis = -1; q.pausedRestMillis = -1; q.pausedEnergy = -1; q.pausedActiveMillis = 0;
    }
    public boolean cancelQuest(Quest q, long now) {
        if (!quests.contains(q)) return false;
        boolean active = session != null && session.phase == QUEST && session.questId.equals(q.id);
        if (!active && !isPaused(q)) return false;
        if (active) { accountTime(now); session = null; }
        resetQuestTimer(q); return true;
    }
    private void finish(long at, List<Event> events) {
        boolean requiredWasOutstanding = requiredPenalty().days > 0;
        Quest completed = null;
        for (Quest q : quests) {
            if (q.id.equals(session.questId)) {
                completed = q;
                q.completions = q.completedDay == session.occurrenceDay ? q.completions + 1 : 1;
                q.completedDay = session.occurrenceDay;
                q.bountySince = at;
                q.remainingMillis = -1; q.pausedRewardMinutes = -1; q.pausedReferenceMinutes = -1;
                q.pausedRewardMillis = -1; q.pausedReferenceMillis = -1;
                q.pausedRestMillis = -1; q.pausedEnergy = -1; q.pausedActiveMillis = 0;
                break;
            }
        }
        history.add(new HistoryEntry(QUEST_HISTORY, session.questId, session.questName, at));
        if (completed != null) creditCategory(completed, session.occurrenceDay);
        long bounty = activeBounty(at);
        long earned = Math.min(bounty, LIMIT - coins);
        HistoryEntry recorded=history.get(history.size()-1);recorded.coinsDelta=earned;recorded.coinsKnown=true;recorded.energy=session.energy;
        long oldVisible = coins - pendingCoins;
        coins += earned;
        recordBalance(at, "Quest completed: " + session.questName);
        pendingCoins = Math.max(0, coins - Math.max(0, oldVisible));
        completedToday++;
        long bountyBefore = nextBounty;
        if (!session.required && session.timed && session.activeMillis > 0) {
            if (bountyChanges.isEmpty()) bountyBase = nextBounty;
            HistoryEntry credit = new HistoryEntry("time", session.questId, session.questName, at);
            credit.bountyMillis = session.activeMillis; bountyChanges.add(credit); rebuildBounty();
        }
        if (completed != null && completed.repeatable) pendingConfetti = Math.max(pendingConfetti, 1);
        if (session.required && requiredWasOutstanding && requiredPenalty().days == 0)
            pendingConfetti = 2;
        if (completed != null && !completed.repeatable && session.occurrenceDay == day) {
            UndoEntry entry = new UndoEntry(); entry.questId = completed.id; entry.at = at;
            entry.categoryId = completed.categoryId;
            entry.reward = earned; entry.bountyBefore = bountyBefore; entry.bountyAfter = nextBounty;
            entry.previousBountySince = session.bountySince;
            undo.add(entry);
        }
        if (randomMode) refreshRandomQuests(new java.util.Random());
        events.add(new Event("victory", at));
        if (session.timed && restFor(session) > 0) {
            session.phase = REST;
            session.endsAt = at + restFor(session);
        } else {
            boolean wasTimed = session.timed;
            session = null;
            if (wasTimed) events.add(new Event("ready", at));
        }
    }
    public long takePendingCoins() {
        long value = Math.min(pendingCoins, Math.max(0, coins));
        pendingCoins = 0;
        return value;
    }
    public List<Quest> availableQuests() {
        List<Quest> result = new ArrayList<>();
        for (Quest q : quests) if (q.due(day) && (q.repeatable || !q.done(day))) result.add(q);
        return result;
    }
    public List<Quest> boardQuests() {
        List<Quest> available = randomMode ? randomCandidates() : availableQuests();
        if (!randomMode) return available;
        if (randomDay != day || randomQuestIds.size() > 2) refreshRandomQuests(new java.util.Random());
        List<Quest> selected = new ArrayList<>();
        for (String id : randomQuestIds) for (Quest q : available) if (q.id.equals(id)) { selected.add(q); break; }
        boolean needsRequired = available.stream().anyMatch(this::isRequired) && (selected.isEmpty() || !isRequired(selected.get(0)));
        boolean needsSingle = available.size() > 1 && available.stream().anyMatch(q -> !isRequired(q) && !q.repeatable)
            && (selected.size() < 2 || isRequired(selected.get(1)) || selected.get(1).repeatable);
        if (needsRequired || needsSingle || selected.size() != Math.min(2, available.size())) {
            refreshRandomQuests(new java.util.Random());
            selected.clear();
            for (String id : randomQuestIds) for (Quest q : available) if (q.id.equals(id)) { selected.add(q); break; }
        }
        return selected;
    }
    public void refreshRandomQuests(java.util.Random random) {
        List<Quest> fresh = new ArrayList<>(), previous = new ArrayList<>();
        for (Quest q : randomCandidates()) (randomQuestIds.contains(q.id) ? previous : fresh).add(q);
        java.util.Collections.shuffle(fresh, random); java.util.Collections.shuffle(previous, random);
        fresh.addAll(previous); randomQuestIds.clear();
        Quest required = null;
        for (Quest q : fresh) if (isRequired(q)) { required = q; break; }
        if (required != null) { randomQuestIds.add(required.id); fresh.remove(required); }
        Quest single = null;
        for (Quest q : fresh) if (!isRequired(q) && !q.repeatable) { single = q; break; }
        if (single != null) {
            fresh.remove(single);
            if (randomQuestIds.isEmpty() && !fresh.isEmpty()) randomQuestIds.add(fresh.remove(0).id);
            randomQuestIds.add(single.id);
        }
        for (Quest q : fresh) { if (randomQuestIds.size() == 2) break; randomQuestIds.add(q.id); }
        randomDay = day;
    }
    public List<Quest> randomCandidates() {
        List<Quest> result = new ArrayList<>();
        for (Quest q : availableQuests()) if (!excludedRandomCategories.contains(q.categoryId)) result.add(q);
        return result;
    }
    public void setRandomCategoryEnabled(String id, boolean enabled) {
        excludedRandomCategories.remove(id); if (!enabled) excludedRandomCategories.add(id);
        refreshRandomQuests(new java.util.Random());
    }
    public List<Quest> activeRepeatableQuests() {
        List<Quest> result = new ArrayList<>();
        for (Quest q : availableQuests()) if (q.repeatable) result.add(q);
        return result;
    }
    public Map<String, List<String>> analyticsGroups(List<String> ids, boolean laws) {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        if (laws) {
            for (Fine law : fines) if (ids.contains(law.id)) groups.computeIfAbsent("", key -> new ArrayList<>()).add(law.id);
            return groups;
        }
        for (String id : orderedBoardItems()) if (category(id) != null) groups.put(id, new ArrayList<>());
        for (Quest q : quests) if (ids.contains(q.id)) groups.computeIfAbsent(category(q.categoryId) == null ? "" : q.categoryId, key -> new ArrayList<>()).add(q.id);
        groups.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        return groups;
    }
    public boolean restoreDeletedQuest(String id, long now) {
        Quest q = null; for (Quest candidate : deletedQuests) if (candidate.id.equals(id)) { q = candidate; break; }
        if (q == null || quests.stream().anyMatch(candidate -> candidate.id.equals(id))) return false;
        deletedQuests.remove(q);
        if (category(q.categoryId) == null) q.categoryId = "";
        if (!q.forever && q.firstDay < day) q.firstDay = day;
        q.completedDay = Long.MIN_VALUE; q.completions = 0; q.bountySince = now;
        q.remainingMillis = -1; q.pausedRewardMinutes = -1; q.pausedReferenceMinutes = -1;
        q.pausedRewardMillis = -1; q.pausedReferenceMillis = -1; q.pausedRestMillis = -1; q.pausedEnergy = -1; q.pausedActiveMillis = 0;
        quests.add(q.required ? 0 : quests.size(), q); if (q.categoryId.isEmpty()) boardOrder.add(q.id);
        if (randomMode) refreshRandomQuests(new java.util.Random()); return true;
    }
    public boolean moveQuest(String sourceId, String targetId) {
        int from = -1, to = -1;
        for (int i = 0; i < quests.size(); i++) {
            if (quests.get(i).id.equals(sourceId)) from = i;
            if (quests.get(i).id.equals(targetId)) to = i;
        }
        if (from < 0 || to < 0 || from == to) return false;
        Quest source = quests.remove(from); quests.add(to, source);
        if (randomMode && randomQuestIds.contains(sourceId) && randomQuestIds.contains(targetId)) {
            int target = randomQuestIds.indexOf(targetId); randomQuestIds.remove(sourceId); randomQuestIds.add(target, sourceId);
            List<Quest> selected = new ArrayList<>();
            for (String id : randomQuestIds) for (Quest q : quests) if (q.id.equals(id)) { selected.add(q); break; }
            Quest required = null, single = null;
            for (Quest q : selected) { if (isRequired(q) && required == null) required = q; if (!isRequired(q) && !q.repeatable && single == null) single = q; }
            if (required != null) { selected.remove(required); selected.add(0, required); }
            if (single != null) { selected.remove(single); selected.add(Math.min(1, selected.size()), single); }
            randomQuestIds.clear(); for (Quest q : selected) randomQuestIds.add(q.id);
        }
        return true;
    }
    public void addQuest(Quest quest) { if(isUuid(quest.id))quest.id=shortId("q"); quests.add(quest.required ? 0 : quests.size(), quest); boardOrder.add(quest.id); questsCreated++; }
    public void addLaw(Fine law) { if(isUuid(law.id))law.id=shortId("l"); fines.add(law); lawsCreated++; }
    private static boolean isUuid(String id){return id!=null&&id.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");}
    private String shortId(String prefix){
        java.util.Set<String> used=new java.util.HashSet<>();
        for(Quest q:quests){used.add(q.id);used.add(q.categoryId);}for(Quest q:deletedQuests){used.add(q.id);used.add(q.categoryId);}for(Fine l:fines)used.add(l.id);for(Category c:categories)used.add(c.id);
        for(ChoiceList list:choiceLists)used.add(list.id);for(ChoiceList list:briefLists)used.add(list.id);
        for(HistoryEntry e:history)used.add(e.id);for(FocusEntry e:focus)used.add(e.id);for(HistoryEntry e:bountyChanges)used.add(e.id);
        for(String key:collapsedAnalyticsSections)used.add(key.substring(key.lastIndexOf(":")+1));
        for(MissedReview review:missedReviews)for(String key:review.ids)used.add(key.startsWith("category:")?key.substring(9):key);
        String id;do{id=prefix+Long.toString(nextEntityId++,36);}while(used.contains(id));return id;
    }
    public boolean questControlsUnlocked() { return questsCreated >= 3; }
    public boolean lawControlsUnlocked() { return lawsCreated >= 1; }
    public boolean questRearrangeUnlocked() { return questsCreated >= 2; }
    public boolean lawRearrangeUnlocked() { return lawsCreated >= 2; }
    public boolean questActiveToday(Quest q) { return q.due(day) && (q.repeatable || !q.done(day)); }
    public boolean hasActiveRepeatableQuests() {
        return availableQuests().stream().anyMatch(q -> q.repeatable || q.timed)
            || session != null && session.phase == QUEST && session.timed;
    }
    public boolean undoOneOff(long now) {
        if (undo.isEmpty()) return false;
        return restoreOneOff(undo.get(undo.size() - 1).questId, now);
    }
    public List<Quest> completedOneOffs() {
        List<Quest> result = new ArrayList<>();
        for (Quest q : quests) if (!q.repeatable && q.done(day)) result.add(q);
        return result;
    }
    public static long rentFor(long balance) { return Math.min(100, Math.max(0, balance + 10000)); }
    public boolean restoreOneOff(String id, long now) {
        Quest quest = null;
        for (Quest q : completedOneOffs()) if (q.id.equals(id)) { quest = q; break; }
        if (quest == null) return false;
        UndoEntry entry = null;
        for (UndoEntry candidate : undo) if (candidate.questId.equals(id)) { entry = candidate; break; }
        if (entry == null) {
            // Older saves only retained ten restore records. Reconstruct the earlier single's quote from today's events.
            entry = new UndoEntry(); entry.questId = id;
            long base = 100;
            for (HistoryEntry event : history) {
                if (localDay(event.at, ZoneId.systemDefault()) != day) continue;
                if (event.kind.equals(FINE_HISTORY)) { base = 100; continue; }
                if (!event.kind.equals(QUEST_HISTORY)) continue;
                boolean required = false;
                for (Quest q : quests) if (q.id.equals(event.id)) { required = q.required; break; }
                if (event.id.equals(id)) { entry.at = event.at; entry.reward = scaledBounty(base, required, 0, questMinutes); entry.bountyBefore = base; entry.bountyAfter = required ? base : raiseBounty(base); break; }
                if (!required) base = raiseBounty(base);
            }
        }
        undo.remove(entry);
        Category restoredCategory = category(entry.categoryId);
        if (restoredCategory != null && restoredCategory.progressDay == day) {
            restoredCategory.progressCount = Math.max(0, restoredCategory.progressCount - 1); restoredCategory.completedQuestIds.remove(id);
            restoredCategory.fulfilledDay = Long.MIN_VALUE; restoredCategory.fulfilledTarget = 0;
            int needed = categoryNeeded(restoredCategory, day);
            if (needed > 0 && categoryRemaining(restoredCategory, day) == 0) { restoredCategory.fulfilledDay = day; restoredCategory.fulfilledTarget = needed; }
        }
        quest.bountySince = entry.previousBountySince;
        if ((quest.required || restoredCategory != null && categoryActive(restoredCategory)) && pendingConfetti == 2) pendingConfetti = 0;
        for (Quest q : quests) if (q.id.equals(entry.questId)) {
            q.completedDay = Long.MIN_VALUE; q.completions = 0;
        }
        final long completedAt = entry.at;
        history.removeIf(e -> e.kind.equals(QUEST_HISTORY) && e.id.equals(id) && e.at == completedAt);
        coins = Math.max(-LIMIT, coins - entry.reward);
        pendingCoins = Math.min(pendingCoins, Math.max(0, coins));
        if (bountyChanges.removeIf(e -> (e.kind.equals("raise") || e.kind.equals("time")) && e.id.equals(id) && e.at == completedAt)) {
            rebuildBounty();
        } else if (nextBounty == entry.bountyAfter) nextBounty = entry.bountyBefore;
        completedToday = Math.max(0, completedToday - 1); recordBalance(now, "Restored quest: " + quest.name); return true;
    }
    public void deleteQuest(Quest quest) {
        if (!quests.contains(quest)) return;
        deletedQuests.removeIf(q -> q.id.equals(quest.id)); deletedQuests.add(quest);
        if (session != null && session.questId.equals(quest.id)) session = null;
        focus.removeIf(e -> e.id.equals(quest.id));
        quests.remove(quest); undo.removeIf(e -> e.questId.equals(quest.id)); randomQuestIds.remove(quest.id);
        refreshCategory(category(quest.categoryId));
        boardOrder.remove(quest.id);
        history.removeIf(e -> e.kind.equals(QUEST_HISTORY) && e.id.equals(quest.id));
        for (HistoryEntry change : bountyChanges) if (change.id.equals(quest.id)) { change.id = ""; change.name = ""; }
        for (MissedReview review : missedReviews) for (int i = review.ids.size() - 1; i >= 0; i--)
            if (review.ids.get(i).equals(quest.id)) { review.ids.remove(i); review.names.remove(i); }
    }
    public void deleteLaw(Fine law) {
        fines.remove(law); history.removeIf(e -> e.kind.equals(FINE_HISTORY) && e.id.equals(law.id));
    }
    public void resolveMissed(boolean forgotToLog, long now, ZoneId zone) {
        if (missedReviews.isEmpty()) return;
        MissedReview review = missedReviews.remove(0);
        if (!forgotToLog) return;
        coins = add(coins, review.coinsLost + review.interestLost - review.rentDifference);
        days = add(days, review.daysLost + 1);
        long at = LocalDate.ofEpochDay(review.day).atTime(23, 59).atZone(zone).toInstant().toEpochMilli();
        for (int i = 0; i < review.ids.size(); i++) if (!review.ids.get(i).startsWith("category:")) history.add(new HistoryEntry(QUEST_HISTORY, review.ids.get(i), review.names.get(i), at));
        recordBalance(now, "Corrected missed quest logging");
    }
}
