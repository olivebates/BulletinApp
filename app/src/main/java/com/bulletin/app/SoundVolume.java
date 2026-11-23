package com.bulletin.app;

/** The existing mix is the 55% reference point; device volume stays independent. */
final class SoundVolume {
    static final int DEFAULT = 55;
    static int percent(int value) {return Math.max(0,Math.min(100,value));}
    static float scale(float original,int percent) {
        return Math.max(0,Math.min(1,original*percent(percent)/(float)DEFAULT));
    }
}
