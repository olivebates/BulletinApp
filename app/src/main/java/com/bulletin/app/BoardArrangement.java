package com.bulletin.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Only arrangement state: returning a drag must not roll back gameplay or timers. */
final class BoardArrangement {
    private final List<Game.Quest> quests;
    private final List<Game.Fine> laws;
    private final List<String> order;
    private final Map<String, String> categories = new HashMap<>();
    private final Map<String, Integer> hues = new HashMap<>();
    BoardArrangement(Game game) {
        quests = new ArrayList<>(game.quests); laws = new ArrayList<>(game.fines); order = new ArrayList<>(game.boardOrder);
        for (Game.Quest q : quests) { categories.put(q.id, q.categoryId); hues.put(q.id, q.hue); }
    }
    void restore(Game game) {
        List<Game.Quest> liveQuests = new ArrayList<>(game.quests); List<Game.Fine> liveLaws = new ArrayList<>(game.fines);
        game.quests.clear(); for (Game.Quest q : quests) if (liveQuests.remove(q)) { game.quests.add(q); q.categoryId = categories.get(q.id); q.hue = hues.get(q.id); }
        game.quests.addAll(liveQuests);
        game.fines.clear(); for (Game.Fine law : laws) if (liveLaws.remove(law)) game.fines.add(law); game.fines.addAll(liveLaws);
        List<String> liveOrder = new ArrayList<>(game.boardOrder);
        game.boardOrder.clear();
        for (String id : order) if (game.category(id) != null || game.quests.stream().anyMatch(q -> q.id.equals(id) && q.categoryId.isEmpty())) game.boardOrder.add(id);
        for (String id : liveOrder) if (!game.boardOrder.contains(id) && (game.category(id) != null || liveQuests.stream().anyMatch(q -> q.id.equals(id)))) game.boardOrder.add(id);
    }
}
