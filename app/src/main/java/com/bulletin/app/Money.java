package com.bulletin.app;

/** Money is stored as integral euro cents; no floating-point rounding in balances. */
public final class Money {
    private Money() { }
    public static String format(long cents) {
        long value = Math.abs(cents);
        return (cents < 0 ? "-" : "") + "€" + value / 100 + "." + (value % 100 < 10 ? "0" : "") + value % 100;
    }
    static long fromOldCoins(long coins) { return Math.max(-Game.LIMIT, Math.min(Game.LIMIT, coins * 10)); }
}
