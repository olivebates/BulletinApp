package com.bulletin.app;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Lossless lexical JSON packing: shared strings and per-field delta-coded integers. */
final class JsonPacking {
    private static final int STRING=128, REFERENCE=129, INTEGER=130, DELTA=131;
    private static final int MAX_STRINGS=200000;
    // B2 dictionary v1 is frozen: changing its order requires a new transport version.
    private static final String[] SEEDS = {"\"accountedAt\"","\"amountLimit\"","\"amountUsed\"","\"analyticsSince\"","\"at\"","\"balanceRangeEnd\"","\"balanceRangeStart\"","\"balances\"","\"boardOrder\"","\"bountyAfter\"","\"bountyBase\"","\"bountyBefore\"","\"bountyChanges\"","\"bountySince\"","\"categories\"","\"categoryId\"","\"cause\"","\"cents\"","\"coins\"","\"coinsLost\"","\"collapsed\"","\"collapsedAnalyticsSections\"","\"completedDay\"","\"completedQuestIds\"","\"completedToday\"","\"completions\"","\"count\"","\"dateOnly\"","\"day\"","\"days\"","\"daysLost\"","\"deletedQuests\"","\"emoji\"","\"endsAt\"","\"energy\"","\"excludedRandomCategories\"","\"fines\"","\"finesToday\"","\"firstDay\"","\"firstOpenedDay\"","\"focus\"","\"forever\"","\"fulfilledDay\"","\"fulfilledTarget\"","\"history\"","\"hue\"","\"id\"","\"ids\"","\"interestLost\"","\"interval\"","\"kind\"","\"lastSeenAt\"","\"lastSettlement\"","\"lawsCreated\"","\"millis\"","\"missedReviews\"","\"mode\"","\"mustDo\"","\"name\"","\"names\"","\"nextBounty\"","\"occurrenceDay\"","\"pausedReferenceMillis\"","\"pausedReferenceMinutes\"","\"pausedRestMillis\"","\"pausedRewardMillis\"","\"pausedRewardMinutes\"","\"pendingCoins\"","\"pendingConfetti\"","\"phase\"","\"prerequisites\"","\"previousBountySince\"","\"progressCount\"","\"progressDay\"","\"questId\"","\"questMinutes\"","\"questName\"","\"quests\"","\"questsCreated\"","\"randomDay\"","\"randomMode\"","\"randomQuestIds\"","\"referenceMillis\"","\"referenceMinutes\"","\"remainingMillis\"","\"rentDifference\"","\"repeatable\"","\"requireAll\"","\"required\"","\"requiredTasks\"","\"restMillis\"","\"restMinutes\"","\"reward\"","\"rewardMillis\"","\"rewardMinutes\"","\"session\"","\"swipeHintShown\"","\"timed\"","\"timedMillisToday\"","\"timeLimitMillis\"","\"undo\"","\"version\""};

    static byte[] encode(byte[] source, int limit) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();Map<String,Integer> strings=new HashMap<>();Map<String,Long> numbers=new HashMap<>();String context="";
        for(String seed:SEEDS)strings.put(seed,strings.size());
        for(int i=0;i<source.length;) {
            int b=source[i]&255;
            if(b=='"') {
                int start=i++;boolean closed=false;
                while(i<source.length){int c=source[i++]&255;if(c=='\\'){if(i>=source.length)throw new IOException("Incomplete JSON string");i++;}else if(c=='"'){closed=true;break;}}
                if(!closed)throw new IOException("Incomplete JSON string");
                String s=new String(source,start,i-start,StandardCharsets.UTF_8);Integer id=strings.get(s);
                if(id==null){if(strings.size()>=MAX_STRINGS)throw new IOException("Too many unique strings");strings.put(s,strings.size());out.write(STRING);writeVar(out,i-start);out.write(source,start,i-start);}else{out.write(REFERENCE);writeVar(out,id);}
                context=s;
            } else if(b=='-'||b>='0'&&b<='9') {
                int start=i++;while(i<source.length&&isNumber(source[i]&255))i++;
                String s=new String(source,start,i-start,StandardCharsets.US_ASCII);
                try {
                    long value=Long.parseLong(s);if(!Long.toString(value).equals(s))throw new NumberFormatException();
                    long absolute=zigzag(value),delta=zigzag(value-numbers.getOrDefault(context,0L));boolean useDelta=varSize(delta)<varSize(absolute);
                    out.write(useDelta?DELTA:INTEGER);writeVar(out,useDelta?delta:absolute);numbers.put(context,value);
                } catch(NumberFormatException ex){out.write(source,start,i-start);}
            } else {if(b>=128)throw new IOException("Non-JSON byte outside string");out.write(b);i++;}
            if(out.size()>limit)throw new IOException("Packed JSON is too large");
        }
        return out.toByteArray();
    }
    static byte[] decode(byte[] source, int limit) throws IOException {
        ByteArrayInputStream in=new ByteArrayInputStream(source);ByteArrayOutputStream out=new ByteArrayOutputStream();List<byte[]> strings=new ArrayList<>();Map<String,Long> numbers=new HashMap<>();String context="";
        for(String seed:SEEDS)strings.add(seed.getBytes(StandardCharsets.UTF_8));
        while(in.available()>0) {
            int b=in.read();
            if(b==STRING||b==REFERENCE) {
                long n=readVar(in);byte[] s;
                if(b==STRING){if(n<2||n>in.available()||n>limit||strings.size()>=MAX_STRINGS)throw new IOException("Invalid string length");s=new byte[(int)n];if(in.read(s)!=s.length)throw new IOException("Incomplete string");if(s[0]!='"'||s[s.length-1]!='"')throw new IOException("Invalid packed string");strings.add(s);}
                else {if(n<0||n>=strings.size())throw new IOException("Invalid string reference");s=strings.get((int)n);}
                checkSize(out.size(),s.length,limit);out.write(s,0,s.length);context=new String(s,StandardCharsets.UTF_8);
            } else if(b==INTEGER||b==DELTA) {
                long n=readVar(in),value=(n>>>1)^-(n&1);if(b==DELTA)value+=numbers.getOrDefault(context,0L);numbers.put(context,value);
                byte[] s=Long.toString(value).getBytes(StandardCharsets.US_ASCII);checkSize(out.size(),s.length,limit);out.write(s,0,s.length);
            } else {if(b>=128)throw new IOException("Unknown JSON packing token");checkSize(out.size(),1,limit);out.write(b);}
        }
        return out.toByteArray();
    }
    private static boolean isNumber(int b){return b>='0'&&b<='9'||b=='-'||b=='+'||b=='.'||b=='e'||b=='E';}
    private static long zigzag(long n){return(n<<1)^(n>>63);}
    static int varSize(long n){int size=1;while((n>>>=7)!=0)size++;return size;}
    static void writeVar(ByteArrayOutputStream out,long n){while((n&~127L)!=0){out.write((int)n&127|128);n>>>=7;}out.write((int)n);}
    static long readVar(ByteArrayInputStream in)throws IOException{
        long value=0;for(int shift=0;shift<70;shift+=7){int b=in.read();if(b<0||shift==63&&(b&254)!=0)throw new IOException("Invalid variable integer");value|=(long)(b&127)<<shift;if((b&128)==0)return value;}throw new IOException("Invalid variable integer");
    }
    private static void checkSize(int have,int add,int limit)throws IOException{if((long)have+add>limit)throw new IOException("Packed save expands beyond its limit");}
}