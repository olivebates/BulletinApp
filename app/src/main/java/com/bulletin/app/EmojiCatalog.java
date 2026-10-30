package com.bulletin.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Offline Unicode keyboard repertoire with CLDR English keyword search. */
final class EmojiCatalog {
    static final class Entry {
        final String emoji, name, category, searchable;
        Entry(String emoji, String name, String category, String subgroup, String codes, String keywords) {
            this.emoji = emoji; this.name = name; this.category = category;
            searchable = normalize(emoji + " " + name + " " + category + " " + subgroup + " " + codes + " " + keywords);
        }
    }
    final List<Entry> entries = new ArrayList<>();
    final List<String> categories = new ArrayList<>();
    static EmojiCatalog read(Reader repertoire, Reader annotations) throws IOException {
        Map<String, String> keywords = new HashMap<>();
        BufferedReader words = new BufferedReader(annotations);
        String line;
        while ((line = words.readLine()) != null) {
            int tab = line.indexOf('\t');
            if (!line.startsWith("#") && tab > 0) keywords.put(line.substring(0, tab), line.substring(tab + 1));
        }
        EmojiCatalog result = new EmojiCatalog();
        Set<String> groups = new LinkedHashSet<>(), seen = new HashSet<>();
        String category = "Other", subgroup = "";
        BufferedReader reader = new BufferedReader(repertoire);
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("# group: ")) { category = line.substring(9).trim(); continue; }
            if (line.startsWith("# subgroup: ")) { subgroup = line.substring(12).trim(); continue; }
            int semicolon = line.indexOf(';'), comment = line.indexOf('#', Math.max(0, semicolon));
            if (semicolon < 0 || comment < 0) continue;
            String status = line.substring(semicolon + 1, comment).trim();
            if (!status.equals("fully-qualified") && !status.equals("component")) continue;
            String codes = line.substring(0, semicolon).trim();
            StringBuilder glyph = new StringBuilder();
            for (String code : codes.split("\\s+")) glyph.appendCodePoint(Integer.parseInt(code, 16));
            String metadata = line.substring(comment + 1).trim();
            int version = metadata.indexOf(" E"), nameAt = metadata.indexOf(' ', version + 2);
            if (version < 0 || nameAt < 0) continue;
            String emoji = glyph.toString();
            if (!seen.add(emoji)) continue;
            String key = emoji.replace("\uFE0F", "");
            result.entries.add(new Entry(emoji, metadata.substring(nameAt + 1).trim(), category, subgroup, codes, keywords.getOrDefault(key, "")));
            groups.add(category);
        }
        result.categories.addAll(groups); return result;
    }
    List<Entry> search(String query, String category) {
        String normalized = normalize(query).trim();
        String[] terms = normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
        List<Entry> matches = new ArrayList<>();
        for (Entry entry : entries) {
            if (category != null && !category.equals(entry.category)) continue;
            boolean match = true;
            for (String term : terms) if (!entry.searchable.contains(term)) { match = false; break; }
            if (match) matches.add(entry);
        }
        return matches;
    }
    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
            .replace("u+", "").replace('-', ' ').replace('_', ' ').replace(':', ' ').replace(',', ' ');
    }
}
