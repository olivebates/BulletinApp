package com.bulletin.app;

import org.json.*;
import java.util.*;
import java.math.BigDecimal;

/** Schema 18: shared strings, sparse short keys or dense rows, defaults, and relative dates. */
final class CompactSave {
    // Field order, defaults and cause codes are schema-18 contracts; future changes need an explicit migration.
    private static final String MIN="-9223372036854775808";
    private static final Schema QUEST=schema("id,s,|name,s,|emoji,s,|categoryId,s,|hue,n,-1|repeatable,y,false|required,y,false|forever,y,false|timed,y,false|interval,n,1|questMinutes,n,-1|remainingMillis,n,-1|pausedRewardMinutes,n,-1|pausedReferenceMinutes,n,-1|pausedRewardMillis,n,-1|pausedReferenceMillis,n,-1|pausedRestMillis,n,-1|firstDay,d,@day|bountySince,t,-1|completedDay,d,"+MIN+"|completions,n,0|prerequisites,a,");
    private static final Schema LAW=schema("id,s,|name,s,|emoji,s,|mode,n,0|amountLimit,n,3|amountUsed,n,0|timeLimitMillis,n,300000|remainingMillis,n,@allowance|endsAt,t,0");
    private static final Schema CATEGORY=schema("id,s,|name,s,|hue,n,-1|collapsed,y,false|mustDo,y,false|requireAll,y,false|requiredTasks,n,1|progressDay,d,"+MIN+"|progressCount,n,0|completedQuestIds,a,|fulfilledDay,d,"+MIN+"|fulfilledTarget,n,0");
    private static final Schema HISTORY=schema("id,s,|at,t,0|coinsDelta,n,0|coinsKnown,y,false|count,n,1|kind,s,quest|name,s,@entity|precision,n,0|bucketDay,d,"+MIN+"|endDay,d,@histEnd|dateOnly,y,@summary");
    private static final Schema FOCUS=schema("id,s,|day,d,@day|millis,n,0|name,s,@quest|endDay,d,@focusEnd");
    private static final Schema BALANCE=schema("at,t,0|cents,n,0|cause,z,Balance update|periodStart,t,0|periodEnd,t,0|opening,n,@cents|minimum,n,@cents|maximum,n,@cents|precision,n,0");
    private static final Schema UNDO=schema("questId,s,|categoryId,s,|at,t,0|reward,n,0|bountyBefore,n,100|bountyAfter,n,100|previousBountySince,t,-1");
    private static final Schema REVIEW=schema("day,d,@day|coinsLost,n,0|daysLost,n,0|interestLost,n,0|rentDifference,n,0|ids,a,|names,a,");
    private static final Schema SESSION=schema("questId,s,|questName,s,@quest|occurrenceDay,d,@day|endsAt,t,0|restMinutes,n,5|phase,n,1|timed,y,true|required,y,false|accountedAt,t,0|rewardMinutes,n,25|referenceMinutes,n,25|rewardMillis,n,-1|referenceMillis,n,-1|bountySince,t,-1|restMillis,n,-1");
    private static final Schema ROOT=schema("focus,x,|analyticsSince,t,0|lastSeenAt,t,0|swipeHintShown,y,false|boardOrder,a,|categories,c,|days,n,0|coins,n,0|firstOpenedDay,d,@day|timedMillisToday,n,@focus|pendingConfetti,n,0|bountyBase,n,100|randomMode,y,false|randomDay,d,"+MIN+"|randomQuestIds,a,|pendingCoins,n,0|nextBounty,n,100|completedToday,n,0|finesToday,n,0|questMinutes,n,25|restMinutes,n,5|energy,n,1|questsCreated,n,0|lawsCreated,n,0|hue,n,186|lastSettlement,s,|quests,q,|deletedQuests,q,|excludedRandomCategories,a,|fines,f,|session,S,|history,h,|bountyChanges,h,|balances,b,|balanceRangeStart,t,0|balanceRangeEnd,t,0|undo,u,|missedReviews,m,|collapsedAnalyticsSections,a,|questTimelineStart,t,0|questTimelineEnd,t,0|lawTimelineStart,t,0|lawTimelineEnd,t,0|nextEntityId,n,1");
    private static final String[] CAUSES={"Starting balance","Daily interest","Daily rent","Balance carried forward","Historical balance update","Quest completed: ","Law broken: ","Unfinished must do quests: "};
    private static final class Field{final String name,def;final char type;Field(String name,char type,String def){this.name=name;this.type=type;this.def=def;}}
    private static final class Schema{final Field[] fields;final Set<String> names=new HashSet<>();Schema(Field[] fields){this.fields=fields;for(Field f:fields)names.add(f.name);}}
    private static Schema schema(String spec){String[] parts=spec.split("\\|",-1);Field[] fields=new Field[parts.length];for(int i=0;i<parts.length;i++){String[] f=parts[i].split(",",3);fields[i]=new Field(f[0],f[1].charAt(0),f[2]);}return new Schema(fields);}
    private boolean decoding;private final Map<String,String> names=new HashMap<>();
    private final long day,base;private final List<String> strings=new ArrayList<>();private final Map<String,Integer> indexes=new HashMap<>();
    private CompactSave(long day){this.day=day;this.base=Math.multiplyExact(day,86400000L);}
    static JSONObject pack(JSONObject verbose)throws JSONException{
        CompactSave codec=new CompactSave(verbose.getLong("day"));JSONObject copy=new JSONObject(verbose.toString());copy.remove("version");copy.remove("day");
        codec.collectNames(copy);Object data=codec.encode(ROOT,copy);JSONArray pool=new JSONArray();
        for(String text:codec.strings){
            if(text.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")){java.util.UUID id=java.util.UUID.fromString(text);byte[] bytes=java.nio.ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();pool.put(new JSONArray().put(0).put(DenseText.encode(bytes)));}
            else pool.put(text);
        }
        return new JSONObject().put("version",18).put("b",codec.day).put("s",pool).put("r",data);
    }
    static JSONObject expand(JSONObject compact)throws JSONException{
        CompactSave codec;try{codec=new CompactSave(compact.getLong("b"));}catch(ArithmeticException ex){throw new JSONException("Invalid compact date origin");}
        JSONArray strings=compact.getJSONArray("s");for(int i=0;i<strings.length();i++){
            Object entry=strings.get(i);if(entry instanceof JSONArray){JSONArray packed=(JSONArray)entry;if(packed.length()!=2||packed.getInt(0)!=0)throw new JSONException("Invalid shared-string encoding");try{byte[] bytes=DenseText.decode(packed.getString(1));if(bytes.length!=16)throw new JSONException("Invalid packed UUID");java.nio.ByteBuffer buffer=java.nio.ByteBuffer.wrap(bytes);codec.strings.add(new java.util.UUID(buffer.getLong(),buffer.getLong()).toString());}catch(java.io.IOException ex){throw new JSONException(ex.getMessage());}}
            else codec.strings.add(strings.getString(i));
        }
        codec.decoding=true;JSONObject expanded=codec.decode(ROOT,compact.get("r")).put("version",18).put("day",codec.day);codec.fillNames(expanded);return expanded;
    }
    private void collectNames(JSONObject root)throws JSONException{
        for(String collection:new String[]{"quests","deletedQuests","fines"}){JSONArray rows=root.optJSONArray(collection);if(rows==null)continue;for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);names.put((collection.equals("fines")?"fine:":"quest:")+row.getString("id"),row.optString("name"));}}
    }
    private String entityName(JSONObject row,boolean questOnly){String id=row.optString("questId",row.optString("id"));String kind=!questOnly&&row.optString("kind").equals("fine")?"fine:":"quest:";return names.getOrDefault(kind+id,"");}
    private void fillNames(JSONObject root)throws JSONException{
        collectNames(root);for(String collection:new String[]{"history","bountyChanges","focus"}){JSONArray rows=root.optJSONArray(collection);if(rows==null)continue;for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(row.isNull("name"))row.put("name",entityName(row,collection.equals("focus")));}}
        JSONObject session=root.optJSONObject("session");if(session!=null&&session.isNull("questName"))session.put("questName",entityName(session,true));
    }
    private Object defaultValue(Field f,JSONObject record)throws JSONException{
        if(f.type=='S')return JSONObject.NULL;if("aqfchxbum".indexOf(f.type)>=0)return new JSONArray();
        if(f.def.equals("@entity")||f.def.equals("@quest"))return decoding?JSONObject.NULL:entityName(record,f.def.equals("@quest"));
        if(f.def.equals("@summary"))return record.optInt("precision")>0;
        if(f.def.equals("@histEnd")){int precision=record.optInt("precision");long start=record.optLong("bucketDay",Long.MIN_VALUE);if(precision==0||start==Long.MIN_VALUE)return Long.MIN_VALUE;return precision==7?Math.min(start+6,java.time.LocalDate.ofEpochDay(day).minusYears(1).toEpochDay()-1):start;}
        if(f.def.equals("@focusEnd")){long start=record.optLong("day",day),year=java.time.LocalDate.ofEpochDay(day).minusYears(1).toEpochDay();return start<year?Math.min(HistoryAggregation.week(start)+6,year-1):start;}
        if(f.def.equals("@day"))return day;if(f.def.equals("@selfday"))return record.optLong("day",day);if(f.def.equals("@cents"))return record.optLong("cents");if(f.def.equals("@allowance"))return record.optLong("timeLimitMillis",300000);
        if(f.def.equals("@focus")){long total=0;JSONArray rows=record.optJSONArray("focus");if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject e=rows.getJSONObject(i);if(e.getLong("day")==day)total=Game.add(total,e.getLong("millis"));}return total;}
        if(f.type=='s'||f.type=='z')return f.def;if(f.type=='y')return Boolean.parseBoolean(f.def);return Long.parseLong(f.def);
    }
    private boolean same(Object a,Object b){if(a==b)return true;if(a instanceof JSONArray&&b instanceof JSONArray)return ((JSONArray)a).length()==0&&((JSONArray)b).length()==0;if(a instanceof Number&&b instanceof Number)return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString()))==0;return Objects.equals(a,b);}
    private int intern(String value){Integer index=indexes.get(value);if(index==null){index=strings.size();strings.add(value);indexes.put(value,index);}return index;}
    private String string(Object value)throws JSONException{if(!(value instanceof Number))throw new JSONException("Invalid shared-string reference");int index=((Number)value).intValue();if(index<0||index>=strings.size())throw new JSONException("Invalid shared-string reference");return strings.get(index);}
    private Object encode(Schema schema,JSONObject record)throws JSONException{
        JSONArray dense=new JSONArray();JSONObject sparse=new JSONObject();int last=-1;
        for(int i=0;i<schema.fields.length;i++){Field f=schema.fields[i];Object fallback=defaultValue(f,record),value=record.has(f.name)?record.get(f.name):fallback;
            if(f.type=='z'&&record.optLong("periodStart")>0)value=fallback;
            if(same(value,fallback)){dense.put(JSONObject.NULL);continue;}
            Object encoded=encodeField(f,value);dense.put(encoded);sparse.put(Integer.toString(i,36),encoded);last=i;
        }
        JSONArray trimmed=new JSONArray();for(int i=0;i<=last;i++)trimmed.put(dense.get(i));
        JSONObject extras=new JSONObject();for(Iterator<String> it=record.keys();it.hasNext();){String name=it.next();if(!schema.names.contains(name))extras.put(name,record.get(name));}
        if(extras.length()>0){sparse.put("_",extras);return sparse;}return trimmed.toString().length()<=sparse.toString().length()?trimmed:sparse;
    }
    private JSONObject decode(Schema schema,Object encoded)throws JSONException{
        JSONArray dense=encoded instanceof JSONArray?(JSONArray)encoded:null;JSONObject sparse=encoded instanceof JSONObject?(JSONObject)encoded:null;if(dense==null&&sparse==null)throw new JSONException("Invalid compact record");
        if(dense!=null&&dense.length()>schema.fields.length)throw new JSONException("Unknown compact record fields");JSONObject record=new JSONObject();
        for(int i=0;i<schema.fields.length;i++){Field f=schema.fields[i];String key=Integer.toString(i,36);Object value=dense!=null?dense.opt(i):sparse.opt(key);record.put(f.name,value==null||value==JSONObject.NULL?defaultValue(f,record):decodeField(f,value));}
        if(sparse!=null){for(Iterator<String> it=sparse.keys();it.hasNext();){String key=it.next();if(key.equals("_"))continue;try{int index=Integer.parseInt(key,36);if(index<0||index>=schema.fields.length)throw new NumberFormatException();}catch(NumberFormatException ex){throw new JSONException("Unknown compact field");}}
            JSONObject extras=sparse.optJSONObject("_");if(extras!=null)for(Iterator<String> it=extras.keys();it.hasNext();){String key=it.next();if(!schema.names.contains(key))record.put(key,extras.get(key));}
        }return record;
    }
    private Object encodeField(Field f,Object value)throws JSONException{
        if(value==JSONObject.NULL)return value;
        switch(f.type){
            case 's':return intern(value.toString());
            case 'z':{String cause=value.toString();for(int i=0;i<CAUSES.length;i++){if(i<5&&cause.equals(CAUSES[i]))return new JSONArray().put(i);if(i>=5&&cause.startsWith(CAUSES[i]))return new JSONArray().put(i).put(intern(cause.substring(CAUSES[i].length())));}return intern(cause);}
            case 'a':{JSONArray in=(JSONArray)value,out=new JSONArray();for(int i=0;i<in.length();i++)out.put(intern(in.getString(i)));return out;}
            case 't':{long original=((Number)value).longValue(),delta;try{delta=Math.subtractExact(original,base);}catch(ArithmeticException ex){return new JSONArray().put(original);}
                long seconds=Math.floorDiv(delta,1000),millis=Math.floorMod(delta,1000);try{Math.multiplyExact(seconds,1000);}catch(ArithmeticException ex){return new JSONArray().put(original);}return millis==0?seconds:new JSONArray().put(seconds).put(millis);}
            case 'd':return Math.subtractExact(((Number)value).longValue(),day);
            case 'S':return encode(SESSION,(JSONObject)value);
            case 'q':case 'f':case 'c':case 'h':case 'x':case 'b':case 'u':case 'm':{JSONArray in=(JSONArray)value,out=new JSONArray();Schema s=child(f.type);for(int i=0;i<in.length();i++)out.put(encode(s,in.getJSONObject(i)));return out;}
            default:return value;
        }
    }
    private Object decodeField(Field f,Object value)throws JSONException{
        switch(f.type){
            case 's':return string(value);
            case 'z':{if(value instanceof JSONArray){JSONArray a=(JSONArray)value;int code=a.getInt(0);if(code<0||code>=CAUSES.length)throw new JSONException("Invalid balance cause");return CAUSES[code]+(code>=5?string(a.get(1)):"");}return string(value);}
            case 'a':{JSONArray in=(JSONArray)value,out=new JSONArray();for(int i=0;i<in.length();i++)out.put(string(in.get(i)));return out;}
            case 't':{try{if(value instanceof JSONArray){JSONArray a=(JSONArray)value;if(a.length()==1)return a.getLong(0);if(a.length()!=2||a.getLong(1)<0||a.getLong(1)>999)throw new JSONException("Invalid relative time");return Math.addExact(base,Math.addExact(Math.multiplyExact(a.getLong(0),1000),a.getLong(1)));}return Math.addExact(base,Math.multiplyExact(((Number)value).longValue(),1000));}catch(ArithmeticException ex){throw new JSONException("Relative time overflow");}}
            case 'd':try{return Math.addExact(day,((Number)value).longValue());}catch(ArithmeticException ex){throw new JSONException("Relative day overflow");}
            case 'S':return decode(SESSION,value);
            case 'q':case 'f':case 'c':case 'h':case 'x':case 'b':case 'u':case 'm':{JSONArray in=(JSONArray)value,out=new JSONArray();Schema s=child(f.type);for(int i=0;i<in.length();i++)out.put(decode(s,in.get(i)));return out;}
            default:return value;
        }
    }
    private static Schema child(char type){switch(type){case 'q':return QUEST;case 'f':return LAW;case 'c':return CATEGORY;case 'h':return HISTORY;case 'x':return FOCUS;case 'b':return BALANCE;case 'u':return UNDO;case 'm':return REVIEW;default:throw new IllegalArgumentException();}}
}