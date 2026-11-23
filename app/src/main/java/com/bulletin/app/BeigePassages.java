package com.bulletin.app;

import java.util.*;
import java.util.regex.*;

/** Formatting and empty lines do not split a continuous run of beige text. */
final class BeigePassages {
    static final class Passage {
        final int firstSpan;
        String text;
        Passage(int firstSpan,String text){this.firstSpan=firstSpan;this.text=text;}
    }
    private static final Pattern SPAN=Pattern.compile("<span color=\"beige\">([\\s\\S]*?)</span>");
    static List<Passage> find(String markdown){
        List<Passage> result=new ArrayList<>();Matcher spans=SPAN.matcher(markdown);int end=-1,index=0;
        while(spans.find()){
            String content=spans.group(1),gap=end<0?"":markdown.substring(end,spans.start());
            boolean inCode=insideFence(markdown,spans.start());
            if(!inCode){
                String plain=plain(content);
                if(!plain.trim().isEmpty()){
                    if(end>=0&&!result.isEmpty()&&formattingOnly(gap)){
                        Passage last=result.get(result.size()-1);last.text+=plain(gap)+plain;
                    }else result.add(new Passage(index,plain));
                }
                index+=1+(int)content.chars().filter(c->c=='\n').count();end=spans.end();
            }
        }
        return result;
    }
    private static boolean insideFence(String markdown,int at){
        boolean code=false;for(String line:markdown.substring(0,at).split("\n"))if(line.startsWith("```"))code=!code;return code;
    }
    private static boolean formattingOnly(String gap){
        if(gap.contains("```"))return false;
        return gap.replaceAll("(?m)^[ \\t]*(?:#{1,6}[ \\t]+|>[ \\t]*|[-+*][ \\t]+|\\d+\\.[ \\t]+)","").replaceAll("[\\s*_~]","").isEmpty();
    }
    private static String plain(String value){
        return value.replaceAll("(?m)^[ \\t]*#{1,6}[ \\t]+","")
            .replaceAll("\\[([^]]+)\\]\\(https?://[^)]+\\)","$1")
            .replaceAll("(?<!\\\\)[*_~]","").replaceAll("\\\\(.)","$1");
    }
}
