package com.bulletin.app;

/** Timing and calendar bounds shared by the completion visuals. */
final class CompletionVisuals {
    static final int CHECK_MILLIS = 200, HOLD_MILLIS = 1000;
    static final int COLLAPSE_MILLIS = 200, SPRING_MILLIS = 300;
    static final int REWARD_MILLIS = 2000, FADE_MILLIS = 200;
    static float dailyWaveRadius(float density,float width) {return .6f*Math.min(220*density,width*.65f);}
    static float milestoneWaveRadius(float density,float width) {return .5f*dailyWaveRadius(density,width);}
    static long firstDay(long today, long taskFirstDay) {
        return Math.max(today - 13, Math.min(today, taskFirstDay));
    }
    static int visibleHistoryRows(int historyRows, int height, int rowHeight, int todayHeight) {
        return Math.min(historyRows, Math.max(0, (height - todayHeight) / rowHeight));
    }
    static float checkProgress(long elapsed) {
        float t = Math.max(0, Math.min(1, elapsed / (float) CHECK_MILLIS));
        // Smoothstep interpolates the drawn path with a gentle start and finish.
        return t * t * (3 - 2 * t);
    }
    static float rewardAlpha(long elapsed) {
        return Math.max(0, Math.min(1, Math.min(elapsed / (float) FADE_MILLIS,
            (REWARD_MILLIS - elapsed) / (float) FADE_MILLIS)));
    }
    static int completedOutline(int base, int count) {
        return dailyColor(base,0xffffff00,count);
    }
    static int completedBackground(int base,int count) {return dailyColor(base,0xff4d4500,count);}
    private static int dailyColor(int base,int target,int count) {
        float t=Math.max(0,Math.min(5,count))/5f;
        int r=Math.round(((base>>16)&255)*(1-t)+((target>>16)&255)*t);
        int g=Math.round(((base>>8)&255)*(1-t)+((target>>8)&255)*t);
        int b=Math.round((base&255)*(1-t)+(target&255)*t);
        return 0xff000000|(r<<16)|(g<<8)|b;
    }
    static float waveGrowth(long elapsed) {return (float)(1-Math.exp(-Math.max(0,elapsed)/1600d));}
    static float collapseProgress(long elapsed) {return checkProgress(elapsed);}
    static float pressAmount(long elapsed) {return Math.max(0,Math.min(1,elapsed/80f));}
    static float springAmount(long elapsed) {
        if(elapsed>=SPRING_MILLIS)return 0;
        double t=Math.max(0,elapsed)/(double)SPRING_MILLIS;
        return (float)(Math.exp(-6*t)*Math.cos(11*t));
    }
}
