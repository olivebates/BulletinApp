package com.bulletin.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class FeedbackTest {
    @Test public void debtRewardStartsAtActualNegativeBalance() {
        assertEquals(-1000, Feedback.interpolate(-1000, -900, 0));
        assertEquals(-950, Feedback.interpolate(-1000, -900, .5f));
        assertEquals(-900, Feedback.interpolate(-1000, -900, 1));
    }
    @Test public void rewardCanCrossZeroWithoutJumping() {
        assertEquals(-25, Feedback.interpolate(-100, 50, .5f));
        assertEquals(50, Feedback.interpolate(-100, 50, 1));
    }
    @Test public void interruptedDeductionContinuesFromDisplayedBalance() {
        long displayed = Feedback.interpolate(1000, 500, .4f);
        assertEquals(800, displayed);
        assertEquals(800, Feedback.interpolate(displayed, -500, 0));
        assertEquals(150, Feedback.interpolate(displayed, -500, .5f));
        assertEquals(-500, Feedback.interpolate(displayed, -500, 1));
    }
    @Test public void largeBalanceLandsOnExactCent() {
        assertEquals(Game.LIMIT - 1, Feedback.interpolate(-Game.LIMIT, Game.LIMIT - 1, 1));
    }
    @Test public void clearingBoardDoesNotHideRequiredMilestone() {
        String message = Feedback.completion("Study", 10, true, true, true);
        assertTrue(message.contains("Study completed"));
        assertTrue(message.contains(Money.format(10)));
        assertTrue(message.contains("All required quests completed!"));
        assertTrue(message.contains("Board cleared!"));
    }
    @Test public void ordinaryCompletionDoesNotClaimMilestone() {
        String message = Feedback.completion("Study", 100, false, false, false);
        assertFalse(message.contains("required"));
        assertFalse(message.contains("cleared"));
    }
}
