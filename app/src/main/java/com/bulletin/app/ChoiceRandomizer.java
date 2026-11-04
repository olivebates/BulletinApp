package com.bulletin.app;

import java.util.*;

/** A session prefers unseen choices, then the choices selected longest ago. */
final class ChoiceRandomizer {
    final ChoiceEditorState editor = new ChoiceEditorState();
    final List<String> items;
    final List<Integer> options = new ArrayList<>();
    private final Map<Integer,Long> selected = new HashMap<>();
    private final Random random;
    private long sequence;
    boolean enabled;
    ChoiceRandomizer(List<String> items) { this(items, new Random()); }
    ChoiceRandomizer(List<String> items, Random random) { this.items=items; this.random=random; }
    void start() { enabled=true; sequence=0; selected.clear(); options.clear(); refresh(); }
    void stop() { enabled=false; selected.clear(); options.clear(); }
    boolean choose(int index) {
        if(!enabled || !options.contains(index)) return false;
        selected.put(index,++sequence); refresh(); return true;
    }
    long[] snapshot() {
        long[] state=new long[items.size()+4];state[0]=enabled?1:0;state[1]=sequence;
        state[2]=options.isEmpty()?-1:options.get(0);state[3]=options.size()<2?-1:options.get(1);
        for(int i=0;i<items.size();i++)state[i+4]=selected.getOrDefault(i,0L);
        return state;
    }
    void restore(long[] state) {
        if(state==null || state.length!=items.size()+4 || state[0]<0 || state[0]>1 || state[1]<0 || state[1]==Long.MAX_VALUE)return;
        int eligible=0;for(String item:items)if(!item.trim().isEmpty())eligible++;
        for(int i=0;i<items.size();i++)if(state[i+4]<0 || state[i+4]>state[1])return;
        List<Integer> restored=new ArrayList<>();
        for(int n=2;n<4;n++) {
            long index=state[n];if(index==-1)continue;
            if(index<0 || index>=items.size() || items.get((int)index).trim().isEmpty() || restored.contains((int)index))return;
            restored.add((int)index);
        }
        if(state[0]==1 && restored.size()!=Math.min(2,eligible))return;
        if(state[0]==0 && !restored.isEmpty())return;
        enabled=state[0]==1;sequence=state[1];options.clear();options.addAll(restored);selected.clear();
        for(int i=0;i<items.size();i++)if(state[i+4]>0)selected.put(i,state[i+4]);
    }
    private void refresh() {
        Set<Integer> previous=new HashSet<>(options);
        List<Integer> pool=new ArrayList<>();
        for(int i=0;i<items.size();i++) if(!items.get(i).trim().isEmpty()) pool.add(i);
        Collections.shuffle(pool,random);
        pool.sort((a,b)->{
            long first=selected.getOrDefault(a,0L),second=selected.getOrDefault(b,0L);
            int order=Long.compare(first,second);
            if(order!=0)return order;
            return first==0?Boolean.compare(previous.contains(a),previous.contains(b)):0;
        });
        options.clear(); options.addAll(pool.subList(0,Math.min(2,pool.size())));
    }
}