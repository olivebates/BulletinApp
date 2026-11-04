package com.bulletin.app;
import java.util.Random;
final class BellSequence {
    private int previous=-1;
    int next(Random random) { int next=previous<0?random.nextInt(3):(previous+1+random.nextInt(2))%3; previous=next;return next; }
    static long interval(String minutes,String seconds) {
        try { long m=minutes.trim().isEmpty()?0:Long.parseLong(minutes.trim()),s=seconds.trim().isEmpty()?0:Long.parseLong(seconds.trim());
            if(m<0||m>10080||s<0||s>59)return -1;long total=m*60000+s*1000;return total>=1000?total:-1;
        } catch(NumberFormatException e){return -1;}
    }
}