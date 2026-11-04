package com.bulletin.app;

import java.util.*;

/** Per-list editing and generated-brief session. Stored field categories live in Game. */
final class CreativeBrief {
    final List<String> fields;
    final List<String> words=new ArrayList<>();
    boolean generated, showTitles = true;
    int editScroll,briefScroll;
    private final Random random;
    CreativeBrief(List<String> fields){this(fields,new Random());}
    CreativeBrief(List<String> fields,Random random){this.fields=fields;this.random=random;}
    void generate(BriefDictionary dictionary){
        List<String> previous=new ArrayList<>(words);words.clear();Map<String,Set<String>> used=new HashMap<>();
        for(int i=0;i<fields.size();i++){
            Set<String> excluded=new HashSet<>(used.getOrDefault(fields.get(i),Collections.emptySet()));
            if(i<previous.size())excluded.add(previous.get(i));
            if(excluded.containsAll(dictionary.words(fields.get(i)))) excluded=new HashSet<>(used.getOrDefault(fields.get(i),Collections.emptySet()));
            String word=dictionary.pick(fields.get(i),excluded,random);words.add(word);used.computeIfAbsent(fields.get(i),key->new HashSet<>()).add(word);
        }
        generated=true;briefScroll=0;
    }
    String[] snapshot(){String[] state=new String[fields.size()+4];state[0]=generated?"1":"0";state[1]=Integer.toString(editScroll);state[2]=Integer.toString(briefScroll);state[3]=showTitles?"1":"0";for(int i=0;i<fields.size();i++)state[i+4]=i<words.size()?words.get(i):"";return state;}
    void restore(String[] state,BriefDictionary dictionary){
        if(state==null || (state.length!=fields.size()+3 && state.length!=fields.size()+4) || !("1".equals(state[0])||"0".equals(state[0])))return;
        int offset=state.length==fields.size()+4?4:3;
        if(offset==4 && !("1".equals(state[3])||"0".equals(state[3])))return;
        int edit,brief;try{edit=Math.max(0,Integer.parseInt(state[1]));brief=Math.max(0,Integer.parseInt(state[2]));}catch(NumberFormatException ex){return;}
        if("1".equals(state[0]))for(int i=0;i<fields.size();i++)if(!dictionary.words(fields.get(i)).contains(state[i+offset]))return;
        generated="1".equals(state[0]);showTitles=offset==3||"1".equals(state[3]);editScroll=edit;briefScroll=brief;words.clear();for(int i=offset;i<state.length;i++)words.add(state[i]);
    }
}