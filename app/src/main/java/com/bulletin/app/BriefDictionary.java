package com.bulletin.app;

import java.io.*;
import java.util.*;

/** Authored offline drawing prompts, with strict category sizes and no duplicate entries. */
final class BriefDictionary {
    static final String[] CATEGORIES={"Theme","Location","Architecture","Time Period","Scale","Ambience","Animal","Clothes Style","Body Type","Age","Personality","Emotion","Internal Conflict"};
    private final Map<String,List<String>> entries=new LinkedHashMap<>();
    static boolean category(String name){return Arrays.asList(CATEGORIES).contains(name);}
    static BriefDictionary read(Reader input)throws IOException {
        BriefDictionary result=new BriefDictionary();Map<String,Set<String>> unique=new HashMap<>();
        for(String name:CATEGORIES){result.entries.put(name,new ArrayList<>());unique.put(name,new HashSet<>());}
        BufferedReader reader=new BufferedReader(input);String line;
        while((line=reader.readLine())!=null){if(line.trim().isEmpty()||line.startsWith("#"))continue;String[] parts=line.split("\t",2);
            if(parts.length!=2||!category(parts[0])||parts[1].trim().isEmpty())throw new IOException("Invalid brief dictionary entry");
            String word=parts[1].trim();if(!unique.get(parts[0]).add(word.toLowerCase(Locale.ROOT)))throw new IOException("Duplicate brief entry: "+word);
            result.entries.get(parts[0]).add(word);
        }
        for(String name:CATEGORIES){int expected=name.equals("Theme")||name.equals("Ambience")?500:100;if(result.entries.get(name).size()!=expected)throw new IOException("Wrong dictionary size for "+name);}
        return result;
    }
    List<String> words(String category){List<String> values=entries.get(category);return values==null?Collections.emptyList():Collections.unmodifiableList(values);}
    String pick(String category,Set<String> excluded,Random random){
        List<String> values=words(category);if(values.isEmpty())throw new IllegalArgumentException("Unknown brief category");
        List<String> candidates=new ArrayList<>();for(String word:values)if(!excluded.contains(word))candidates.add(word);
        return candidates.isEmpty()?values.get(random.nextInt(values.size())):candidates.get(random.nextInt(candidates.size()));
    }
}
