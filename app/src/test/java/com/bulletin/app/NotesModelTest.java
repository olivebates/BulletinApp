package com.bulletin.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class NotesModelTest {
    private NotesModel.Page page(NotesModel m,String id,String parent,boolean category,String body){NotesModel.Page p=new NotesModel.Page();p.id=id;p.parent=parent;p.category=category;p.body=body;m.pages.put(id,p);return p;}
    @Test public void categoriesCountDescendantsButNotOwnText(){NotesModel m=new NotesModel();NotesModel.Page a=page(m,"a","",true,"ignored words");a.selected=true;page(m,"b","a",true,"also ignored");page(m,"c","b",false,"Hello **world**");assertEquals(2,m.words(a));assertTrue(m.visible(m.pages.get("c")));}
    @Test public void exclusionHidesSubtreeAndNewChildrenInheritSelection(){NotesModel m=new NotesModel();NotesModel.Page a=page(m,"a","",true,"");a.selected=true;NotesModel.Page b=page(m,"b","a",true,"");NotesModel.Page c=page(m,"c","b",false,"one");assertTrue(m.visible(c));b.excluded=true;c.selected=true;assertFalse(m.visible(c));}
    @Test public void concurrentChangesRequireChoice(){NotesModel.Page p=new NotesModel.Page();p.loaded=true;p.base="original";p.body="local";NotesModel.reconcile(p,"remote");assertTrue(p.conflict);assertEquals("local",p.body);assertEquals("remote",p.remote);assertEquals("original",p.base);}
    @Test public void unchangedRemoteDoesNotDiscardDraft(){NotesModel.Page p=new NotesModel.Page();p.loaded=true;p.base="original";p.body="draft";NotesModel.reconcile(p,"original");assertFalse(p.conflict);assertEquals("draft",p.body);}
    @Test public void completedWriteAfterLostResponseConverges(){NotesModel.Page p=new NotesModel.Page();p.loaded=true;p.base="original";p.body="draft";NotesModel.reconcile(p,"draft");assertFalse(p.conflict);assertEquals("draft",p.base);}
    @Test public void remoteOnlyEditDownloads(){NotesModel.Page p=new NotesModel.Page();p.loaded=true;p.base=p.body="original";NotesModel.reconcile(p,"new");assertEquals("new",p.body);assertEquals("new",p.base);}
    @Test public void persistenceRetainsDraftConflictEmptyCategoryAndMove()throws Exception{NotesModel m=new NotesModel();NotesModel.Page p=page(m,"a","",true,"draft");p.conflict=true;p.remote="remote";p.move="TRASH";p.hidden=true;NotesModel.Page restored=NotesModel.read(m.json().toString()).pages.get("a");assertEquals("draft",restored.body);assertTrue(restored.category);assertTrue(restored.conflict);assertTrue(restored.hidden);assertEquals("TRASH",restored.move);}
    @Test public void cyclesCannotHangTraversal(){NotesModel m=new NotesModel();NotesModel.Page a=page(m,"a","b",true,"");page(m,"b","a",true,"");a.selected=true;assertTrue(m.descendant("b","a"));assertFalse(m.descendant("b","c"));assertEquals(0,m.words(a));}
    @Test public void trashSubtreeIsExcluded(){NotesModel m=new NotesModel();m.trash="trash";page(m,"trash","",true,"");NotesModel.Page p=page(m,"a","trash",false,"words");p.selected=true;assertFalse(m.visible(p));}
    @Test public void markdownLinksCountLabelsNotAddresses(){assertEquals(4,NotesModel.wordCount("## Hello world\n[my link](https://example.com/path)"));}
}
