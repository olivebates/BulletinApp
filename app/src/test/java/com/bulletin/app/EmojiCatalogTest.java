package com.bulletin.app;

import org.junit.Test;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import static org.junit.Assert.*;

public class EmojiCatalogTest {
    private EmojiCatalog catalog() throws Exception {
        Path assets = Path.of("src/main/assets");
        if (!Files.exists(assets)) assets = Path.of("app/src/main/assets");
        try (Reader emojis = Files.newBufferedReader(assets.resolve("emoji-test.txt"), StandardCharsets.UTF_8);
             Reader words = Files.newBufferedReader(assets.resolve("emoji-keywords.tsv"), StandardCharsets.UTF_8)) {
            return EmojiCatalog.read(emojis, words);
        }
    }
    @Test public void catalogIncludesFullEmojiRepertoireWithoutDuplicateQualificationForms() throws Exception {
        EmojiCatalog c = catalog(); assertTrue(c.entries.size() > 3900);
        HashSet<String> glyphs = new HashSet<>(); for (EmojiCatalog.Entry e : c.entries) glyphs.add(e.emoji);
        assertEquals(c.entries.size(), glyphs.size()); assertTrue(glyphs.contains("🇩🇰"));
        assertTrue(glyphs.contains("👨‍👩‍👧‍👦")); assertTrue(glyphs.contains("👩🏽‍💻"));
        assertTrue(glyphs.contains("❤️")); assertTrue(c.categories.size() >= 9);
    }
    @Test public void searchSupportsNamesClDrKeywordsAndCodepoints() throws Exception {
        EmojiCatalog c = catalog();
        assertTrue(c.search("RED HEART", null).stream().anyMatch(e -> e.emoji.equals("❤️")));
        assertTrue(c.search("happy", null).stream().anyMatch(e -> e.emoji.equals("😀")));
        assertTrue(c.search("love", null).stream().anyMatch(e -> e.emoji.equals("❤️")));
        assertTrue(c.search("U+1F600", null).stream().anyMatch(e -> e.emoji.equals("😀")));
        assertTrue(c.search("woman technologist medium skin tone", null).stream().anyMatch(e -> e.emoji.equals("👩🏽‍💻")));
    }
    @Test public void categoriesFilterAndEmptyQueryReturnsEveryEmoji() throws Exception {
        EmojiCatalog c = catalog(); assertEquals(c.entries.size(), c.search("", null).size());
        List<EmojiCatalog.Entry> flags = c.search("", "Flags"); assertFalse(flags.isEmpty());
        assertTrue(flags.stream().allMatch(e -> e.category.equals("Flags")));
        assertTrue(c.search("this-is-not-an-emoji-xyz", null).isEmpty());
    }
}
