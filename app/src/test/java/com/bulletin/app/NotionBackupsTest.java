package com.bulletin.app;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class NotionBackupsTest {
    @Test public void fourHoursIncludesTimeOutsideApp(){long last=100000;assertFalse(NotionBackups.due(last+NotionBackups.INTERVAL-1,last));assertTrue(NotionBackups.due(last+NotionBackups.INTERVAL,last));assertTrue(NotionBackups.due(last+NotionBackups.INTERVAL*3,last));assertTrue(NotionBackups.due(last,0));}
    @Test public void retainsTwentyAndProtectsRestoreTarget(){List<NotionBackups.Entry> all=new ArrayList<>();for(int i=0;i<22;i++)all.add(new NotionBackups.Entry("id"+i,i));List<NotionBackups.Entry> removed=NotionBackups.prune(all,"id0");assertEquals(2,removed.size());assertEquals("id1",removed.get(0).id);assertEquals("id2",removed.get(1).id);}
    @Test public void noPruningBelowLimit(){assertTrue(NotionBackups.prune(Collections.singletonList(new NotionBackups.Entry("a",1)),"").isEmpty());}
    @Test public void hiddenContainerStillSelectsChildren(){NotesModel m=new NotesModel();m.parent="root";NotesModel.Page root=new NotesModel.Page();root.id="root";root.selected=true;m.pages.put(root.id,root);NotesModel.Page child=new NotesModel.Page();child.id="child";child.parent="root";m.pages.put(child.id,child);assertFalse(m.visible(root));assertTrue(m.visible(child));}
    @Test public void backupPageAndDescendantsAreHidden(){NotesModel m=new NotesModel();m.parent="root";NotesModel.Page root=new NotesModel.Page();root.id="root";root.selected=true;m.pages.put(root.id,root);NotesModel.Page backup=new NotesModel.Page();backup.id="backup";backup.parent="root";backup.title="BulletinBackupCodes";backup.selected=true;m.pages.put(backup.id,backup);NotesModel.Page child=new NotesModel.Page();child.id="child";child.parent="backup";child.selected=true;m.pages.put(child.id,child);assertFalse(m.visible(backup));assertFalse(m.visible(child));}
}
