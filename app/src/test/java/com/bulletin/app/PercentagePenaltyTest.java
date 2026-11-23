package com.bulletin.app;

import org.junit.Test;
import java.time.Instant;
import java.time.ZoneId;
import static org.junit.Assert.*;

public class PercentagePenaltyTest {
    private final ZoneId zone = ZoneId.of("UTC");
    private final long now = Instant.parse("2026-11-06T12:00:00Z").toEpochMilli();
    private final long midnight = Instant.parse("2026-11-07T00:00:00Z").toEpochMilli();

    @Test public void lawsEscalateIndependentlyByActualPreviousChargeAndSurviveRestart() throws Exception {
        Game g = new Game(now, zone); g.coins = 10000;
        Game.Fine a = new Game.Fine(), b = new Game.Fine(); g.addLaw(a); g.addLaw(b);
        assertEquals(1000, g.nextFine(a).coins(g.coins));
        g.incurFine(a, now); assertEquals(9000, g.coins);
        assertEquals(900, g.nextFine(b).coins(g.coins));
        assertEquals(1100, g.nextFine(a).coins(g.coins));
        g.incurFine(b, now + 1); assertEquals(8100, g.coins);
        assertEquals(990, g.nextFine(b).coins(g.coins));
        assertEquals(990, g.nextLawFine().coins(g.coins));
        Game loaded = GameCodec.decode(SaveCompression.pack(GameCodec.encode(g).toString()), now + 2, zone);
        assertEquals(1100, loaded.nextFine(loaded.fines.get(0)).coins(loaded.coins));
        assertEquals(990, loaded.nextFine(loaded.fines.get(1)).coins(loaded.coins));
        loaded.incurFine(loaded.fines.get(0), now + 3); assertEquals(7000, loaded.coins);
        assertEquals(1210, loaded.nextFine(loaded.fines.get(0)).coins(loaded.coins));
        assertEquals(-1100, loaded.history.get(2).coinsDelta);
    }
    @Test public void midnightResetsAllLawChargesIncludingAfterOfflineRestart() throws Exception {
        Game g = new Game(now, zone); g.coins = 10000;
        Game.Fine law = new Game.Fine(); g.addLaw(law); g.incurFine(law, now); g.incurFine(law, now + 1);
        Game loaded = GameCodec.decode(GameCodec.encode(g).toString(), midnight + 1, zone);
        loaded.advance(midnight + 1, zone);
        assertEquals(0, loaded.finesToday); assertEquals(-1, loaded.fines.get(0).previousPenalty);
        assertEquals((loaded.coins + 9) / 10, loaded.nextFine(loaded.fines.get(0)).coins(loaded.coins));
    }
    @Test public void repeatedChargesStartAtTheMinimumAndContinueIntoDebt() {
        Game g = new Game(now, zone); g.coins = 101;
        Game.Fine law = new Game.Fine(); g.addLaw(law);
        g.incurFine(law, now); assertEquals(-199, g.coins);
        assertEquals(330, g.nextFine(law).coins(g.coins));
        g.coins = 5; g.incurFine(law, now + 1); assertEquals(-325, g.coins);
        g.incurFine(law, now + 2); assertEquals(-688, g.coins);
    }
    @Test public void olderSavesWithoutNewFieldsStartAtTenPercent() throws Exception {
        Game g = new Game(now, zone); g.coins = 10000;
        Game.Fine law = new Game.Fine(); g.addLaw(law);
        org.json.JSONObject old = GameCodec.encodeLegacy(g);
        old.remove("previousFinePenalty"); old.getJSONArray("fines").getJSONObject(0).remove("previousPenalty");
        Game loaded = GameCodec.decode(old.toString(), now, zone);
        assertEquals(1000, loaded.nextFine(loaded.fines.get(0)).coins(loaded.coins));
    }
}
