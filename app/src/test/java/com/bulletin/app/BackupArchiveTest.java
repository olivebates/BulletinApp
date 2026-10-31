package com.bulletin.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class BackupArchiveTest {
    @Test public void onlyLatestThirtyAreKeptInNewestFirstOrder() throws Exception {
        BackupArchive a=new BackupArchive();for(int i=0;i<45;i++)a.add(i,"App opened","state-"+i);
        assertEquals(30,a.entries.size());assertEquals(44,a.entries.get(0).at);assertEquals(15,a.entries.get(29).at);
        BackupArchive loaded=BackupArchive.decode(a.encode());assertEquals(30,loaded.entries.size());assertEquals("state-44",loaded.entries.get(0).state);
    }
    @Test public void preRestoreSnapshotPreservesCurrentStateAndSelectedOlderSnapshot() throws Exception {
        BackupArchive a=new BackupArchive();a.add(1,"App closed","old");BackupArchive.Entry target=a.entries.get(0);
        BackupArchive next=BackupArchive.decode(a.encode());next.add(2,"Before backup restore","current");
        assertEquals("old",target.state);assertEquals("current",next.entries.get(0).state);assertEquals("old",next.entries.get(1).state);assertEquals(1,a.entries.size());
    }
    @Test public void compressedSnapshotsStayIndependentOfTheBackupArchive() throws Exception {
        BackupArchive a=new BackupArchive();String state=SaveCompression.pack("{\"quests\":[]}");
        a.add(1,"Before import",state);BackupArchive loaded=BackupArchive.decode(a.encode());
        assertEquals(state,loaded.entries.get(0).state);assertEquals("{\"quests\":[]}",SaveCompression.unpack(loaded.entries.get(0).state));
    }
}