package com.bulletin.app;

/** Density-independent edge scrolling speed in dp per second. */
final class DragScroll {
    static float speed(float y, float height) {
        float edge = Math.min(48, height / 2);
        if (edge <= 0) return 0;
        float distance = y < edge ? y - edge : y > height - edge ? y - (height - edge) : 0;
        return Math.signum(distance) * Math.min(1000, Math.abs(distance) * 6.25f);
    }
}
