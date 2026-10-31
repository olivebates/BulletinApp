package com.bulletin.app;

import org.junit.Test;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class BalanceHistoryTest {
    @Test public void fiveYearRetentionKeepsTheOpeningBalanceAndAllNewerChanges() {
        ZoneId zone=ZoneId.of("Europe/Copenhagen");long now=Instant.parse("2026-10-31T12:00:00Z").toEpochMilli();long cutoff=Instant.ofEpochMilli(now).atZone(zone).minusYears(5).toInstant().toEpochMilli();
        List<Game.BalancePoint> p=new ArrayList<>();p.add(new Game.BalancePoint(cutoff-10000,10,"old"));p.add(new Game.BalancePoint(cutoff-1,20,"latest old"));p.add(new Game.BalancePoint(cutoff+1,30,"new"));p.add(new Game.BalancePoint(now,40,"today"));
        BalanceHistory.prune(p,now,zone);assertEquals(3,p.size());assertEquals(cutoff,p.get(0).at);assertEquals(20,p.get(0).cents);assertEquals("new",p.get(1).cause);
        BalanceHistory.prune(p,now,zone);assertEquals(3,p.size());
    }
    @Test public void rangeStartsWithCarriedBalanceAndNeverUsesFutureValues() {
        List<Game.BalancePoint> p=new ArrayList<>();p.add(new Game.BalancePoint(10,100,"one"));p.add(new Game.BalancePoint(20,200,"two"));p.add(new Game.BalancePoint(40,900,"future"));
        List<Game.BalancePoint> w=BalanceHistory.window(p,15,30,900);assertEquals(3,w.size());assertEquals(100,w.get(0).cents);assertEquals("two",w.get(1).cause);assertEquals(200,w.get(2).cents);assertEquals(30,w.get(2).at);
    }
    @Test public void exactEndpointRetainsItsActualCauseAndEmptyDataStillPlots() {
        List<Game.BalancePoint> p=new ArrayList<>();p.add(new Game.BalancePoint(10,100,"Quest completed"));
        List<Game.BalancePoint> w=BalanceHistory.window(p,10,10,100);assertEquals(1,w.size());assertEquals("Quest completed",w.get(0).cause);
        w=BalanceHistory.window(new ArrayList<>(),10,20,-500);assertEquals(2,w.size());assertEquals(-500,w.get(1).cents);
    }
}