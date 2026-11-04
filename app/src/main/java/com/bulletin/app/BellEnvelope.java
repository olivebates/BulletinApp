package com.bulletin.app;
final class BellEnvelope {
    static double attack(double seconds){double t=Math.max(0,Math.min(1,seconds/1.5));return t*t*(3-2*t);}
}