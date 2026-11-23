package com.bulletin.app;

import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;
import java.util.*;
import java.io.IOException;

public class NotesSyncTest {
    static class FakeNotion extends NotionClient {
        final LinkedHashMap<String,String> titles=new LinkedHashMap<>();
        final Map<String,String> parents=new HashMap<>(),bodies=new HashMap<>();
        final List<String> moves=new ArrayList<>();final Set<String> archived=new HashSet<>();
        int creates,renames,writes;boolean loseCreate,failRename,failWrite;Runnable onWrite,onRename;
        FakeNotion(){super("test");add("root","Bulletin","","");}
        void add(String id,String title,String parent,String body){titles.put(id,title);parents.put(id,parent);bodies.put(id,body);}
        JSONObject page(String id)throws Exception{return new JSONObject().put("id",id).put("parent",new JSONObject().put("page_id",parents.get(id))).put("properties",new JSONObject().put("title",new JSONObject().put("type","title").put("title",new JSONArray().put(new JSONObject().put("plain_text",titles.get(id))))));}
        @Override JSONObject request(String method,String path,JSONObject body)throws Exception{if(method.equals("PATCH")&&body.optBoolean("in_trash")){archived.add(path.substring(6));return new JSONObject();}if(method.equals("GET")&&path.startsWith("pages/"))return page(path.substring(6));throw new AssertionError("Unexpected request: "+method+" "+path);}
        @Override List<JSONObject> search()throws Exception{List<JSONObject> list=new ArrayList<>();for(String id:titles.keySet())list.add(page(id));return list;}
        @Override List<String> children(String id){List<String> list=new ArrayList<>();for(String child:titles.keySet())if(id.equals(parents.get(child)))list.add(child);return list;}
        @Override List<JSONObject> blocks(String id)throws Exception{List<JSONObject> list=new ArrayList<>();for(String child:children(id))list.add(new JSONObject().put("id",child).put("type","child_page").put("child_page",new JSONObject().put("title",titles.get(child))));return list;}
        @Override String create(String parent,String title)throws Exception{assertFalse(parent.startsWith("local-"));String id="remote-"+(++creates);add(id,title,parent,"");if(loseCreate){loseCreate=false;throw new IOException("Lost create response");}return id;}
        @Override void rename(String id,String title)throws Exception{if(failRename)throw new IOException("Offline rename");renames++;titles.put(id,title);if(onRename!=null){Runnable hook=onRename;onRename=null;hook.run();}}
        @Override JSONObject markdown(String id)throws Exception{return new JSONObject().put("markdown",bodies.get(id));}
        @Override JSONObject replace(String id,String after)throws Exception{if(failWrite)throw new IOException("Offline write");writes++;bodies.put(id,after);if(onWrite!=null){Runnable hook=onWrite;onWrite=null;hook.run();}return new JSONObject().put("markdown",after);}
        @Override void move(String id,String parent){moves.add(id+"->"+parent);parents.put(id,parent);}
    }
    @Test public void alreadyArchivedDeletionIsClearedAndOtherNotesStillUpload() throws Exception {
        NotesModel model = new NotesModel();
        model.parent = "root";
        NotesModel.Page deleted = model.create("root", "test", false);
        FakeNotion api = new FakeNotion() {
            @Override JSONObject request(String method, String path, JSONObject body) throws Exception {
                JSONObject response = super.request(method, path, body);
                if ("GET".equals(method)) response.put("in_trash", archived.contains(path.substring(6)));
                return response;
            }
            @Override void rename(String id, String title) throws Exception {
                if (archived.contains(id)) {
                    throw new NotionClient.RejectedRequest(400, "Can't edit block that is archived. You must unarchive block before editing.");
                }
                super.rename(id, title);
            }
        };
        sync(model, api);
        api.archived.add(deleted.notionId());
        deleted.body = "Locally retained draft";
        model.trash(deleted.id);
        NotesModel.Page healthy = model.create("root", "Healthy", false);
        healthy.body = "Sync still works";
        sync(model, api);
        assertFalse(model.visible(deleted));
        assertFalse(model.hasPending());
        assertEquals("Locally retained draft", deleted.body);
        assertEquals("Sync still works", api.bodies.get(healthy.notionId()));
        assertFalse(NotesModel.read(model.json().toString()).hasPending());
    }

    @Test public void rejectedBodyKeepsDraftAndAllowsOtherNotesToUpload() throws Exception {
        NotesModel model = new NotesModel();
        model.parent = "root";
        NotesModel.Page rejected = model.create("root", "Rejected", false);
        NotesModel.Page healthy = model.create("root", "Healthy", false);
        rejected.body = "Local draft";
        healthy.body = "First body";
        FakeNotion api = new FakeNotion() {
            @Override JSONObject replace(String id, String body) throws Exception {
                if ("Rejected".equals(titles.get(id))) {
                    throw NotionClient.rejection(400, "{\"message\":\"Invalid page content\"}");
                }
                return super.replace(id, body);
            }
        };
        try {
            sync(model, api);
            fail("Expected a detailed upload error");
        } catch (NotesSync.InvalidNotes error) {
            assertTrue(error.getMessage().contains("Upload body for \"Rejected\""));
            assertTrue(error.getMessage().contains("Invalid page content"));
        }
        assertEquals("Local draft", rejected.body);
        assertTrue(model.pending(rejected));
        assertEquals("First body", api.bodies.get(healthy.notionId()));
        assertFalse(model.pending(healthy));
        healthy.body = "Edited body";
        try {
            sync(model, api);
            fail("Expected the rejected draft to remain queued");
        } catch (NotesSync.InvalidNotes expected) {
            assertEquals("Edited body", api.bodies.get(healthy.notionId()));
        }
    }

    private NotesModel.Page existing(NotesModel m,String id,String title,String parent,String body,boolean category){NotesModel.Page p=new NotesModel.Page();p.id=id;p.title=title;p.parent=parent;p.body=body;p.loaded=p.selected=true;p.category=category;m.pages.put(id,p);return p;}
    private void sync(NotesModel m,FakeNotion api)throws Exception{new NotesSync(m,m,()->{}).run(api);}
    @Test public void offlineCreationRemainsEditableAndSurvivesRestart()throws Exception{
        NotesModel m=new NotesModel();NotesModel.Page p=m.create("","Draft",false);p.body="Offline draft";assertTrue(p.loaded);assertTrue(m.visible(p));assertEquals("",p.notionId());
        m=NotesModel.read(m.json().toString());p=m.pages.get(p.id);m.parent="root";assertTrue(m.visible(p));FakeNotion api=new FakeNotion();sync(m,api);assertEquals("Offline draft",api.bodies.get(p.notionId()));assertTrue(m.visible(p));assertEquals("root",p.parent);
    }
    @Test public void nestedCreationUsesRemoteParentsEvenIfChildIsOrderedFirst()throws Exception{
        NotesModel m=new NotesModel();m.parent="root";NotesModel.Page category=m.create("root","Category",true),child=m.create(category.id,"Document",false);child.body="Text";m.pages.remove(category.id);m.pages.put(category.id,category);FakeNotion api=new FakeNotion();sync(m,api);assertEquals(category.notionId(),api.parents.get(child.notionId()));assertEquals(category.id,child.parent);assertEquals("Text",api.bodies.get(child.notionId()));assertEquals(2,api.creates);
    }
    @Test public void lostCreateResponseIsRecoveredWithoutDuplicateAfterRestart()throws Exception{
        NotesModel m=new NotesModel();m.parent="root";NotesModel.Page p=m.create("root","Document",false);p.body="Draft";FakeNotion api=new FakeNotion();api.loseCreate=true;try{sync(m,api);fail();}catch(IOException expected){}
        assertTrue(p.creating);m=NotesModel.read(m.json().toString());sync(m,api);p=m.pages.get(p.id);assertEquals(1,api.creates);assertEquals("Document",api.titles.get(p.notionId()));assertEquals("Draft",api.bodies.get(p.notionId()));assertFalse(p.creating);
    }
    @Test public void metadataKeepsStableLocalIdentityAndDoesNotImportDuplicate()throws Exception{
        NotesModel m=new NotesModel();m.parent="root";NotesModel.Page p=m.create("root","Document",false);String local=p.id;FakeNotion api=new FakeNotion();sync(m,api);sync(m,api);assertSame(p,m.pages.get(local));assertFalse(m.pages.containsKey(p.notionId()));assertEquals(1,api.creates);
    }
    @Test public void trashImmediatelyHidesSubtreeAndUploadsDraftsBeforeMoving()throws Exception{
        NotesModel m=new NotesModel();m.parent="root";NotesModel.Page category=m.create("root","Category",true),child=m.create(category.id,"Document",false);child.body="Unsynced text";m.trash(category.id);assertFalse(m.visible(category));assertFalse(m.visible(child));assertTrue(m.pendingTrash(child));FakeNotion api=new FakeNotion();sync(m,api);assertEquals("Unsynced text",api.bodies.get(child.notionId()));assertTrue(api.archived.contains(category.notionId()));assertEquals(category.notionId(),api.parents.get(child.notionId()));assertTrue(category.hidden);assertEquals("",category.move);
    }
    @Test public void remoteEditsNeverReplaceLocalAndTrashDoesNotAskForConflicts()throws Exception{
        NotesModel m=new NotesModel();m.parent="root";NotesModel.Page p=m.create("root","Document",false);p.body="Local";FakeNotion api=new FakeNotion();sync(m,api);api.bodies.put(p.notionId(),"Remote");api.add("external","Notion only","root","External");sync(m,api);assertEquals("Local",p.body);assertEquals("Remote",api.bodies.get(p.notionId()));p.body="Local updated";sync(m,api);assertEquals("Local updated",api.bodies.get(p.notionId()));assertFalse(m.pages.containsKey("external"));m.trash(p.id);sync(m,api);assertFalse(p.conflict);assertTrue(api.archived.contains(p.notionId()));
    }
    @Test public void editingDuringUploadPreservesNewerLocalDraft()throws Exception{
        NotesModel m=new NotesModel();m.parent="root";NotesModel.Page p=m.create("root","Document",false);p.body="First";FakeNotion api=new FakeNotion();api.onWrite=()->p.body="Newer draft";sync(m,api);assertEquals("Newer draft",p.body);assertEquals("First",p.base);sync(m,api);assertEquals("Newer draft",api.bodies.get(p.notionId()));
    }
    @Test public void noContainerMeansNoNetworkCreation()throws Exception{NotesModel m=new NotesModel();NotesModel.Page p=m.create("","Document",false);FakeNotion api=new FakeNotion();sync(m,api);assertEquals(0,api.creates);assertEquals("",p.notionId());assertTrue(m.visible(p));}
    @Test public void creationResponseLostBeforeMovingStillRecoversOriginalPage()throws Exception{
        NotesModel m=new NotesModel();m.parent="root";NotesModel.Page p=m.create("root","Document",false);p.body="Draft";FakeNotion api=new FakeNotion();api.add("category","Category","root","");api.loseCreate=true;try{sync(m,api);fail();}catch(IOException expected){}
        NotesModel.Page category=new NotesModel.Page();category.id="category";category.parent="root";category.category=true;m.pages.put(category.id,category);p.parent="category";p.move="category";sync(m,api);assertEquals(1,api.creates);assertEquals("category",api.parents.get(p.notionId()));assertEquals("Draft",api.bodies.get(p.notionId()));
    }
    @Test public void queuedPagesOutsideSelectedContainerDoNotUploadOrMove()throws Exception{
        NotesModel m=new NotesModel();m.parent="root";NotesModel.Page p=m.create("other-workspace","Draft",false);p.body="Private draft";m.trash(p.id);FakeNotion api=new FakeNotion();sync(m,api);assertEquals(0,api.creates);assertTrue(api.moves.isEmpty());assertEquals("",p.notionId());assertEquals("Private draft",p.body);
    }
    @Test public void importedPageRenameSurvivesOfflineAndUploadsOnRetry()throws Exception{
        NotesModel m=new NotesModel();m.parent="root";FakeNotion api=new FakeNotion();api.add("document","Original","root","Text");NotesModel.Page p=existing(m,"document","Original","root","Text",false);sync(m,api);m.update(p,"Renamed",-1,"\uD83D\uDCDD");api.failRename=true;try{sync(m,api);fail();}catch(IOException expected){}
        m=NotesModel.read(m.json().toString());p=m.pages.get("document");assertEquals("Renamed",p.title);assertFalse(p.named);api.failRename=false;sync(m,api);assertEquals("Renamed",api.titles.get("document"));assertTrue(p.named);assertEquals("\uD83D\uDCDD",p.emoji);assertEquals("Text",api.bodies.get("document"));
    }
    @Test public void renameDuringUploadKeepsNewerQueuedName()throws Exception{
        NotesModel m=new NotesModel();m.parent="root";FakeNotion api=new FakeNotion();api.add("document","Original","root","");NotesModel.Page p=existing(m,"document","Original","root","",false);sync(m,api);m.update(p,"First",-1,"");api.onRename=()->m.update(p,"Latest",-1,"");sync(m,api);assertEquals("Latest",p.title);assertFalse(p.named);sync(m,api);assertEquals("Latest",api.titles.get("document"));assertTrue(p.named);
    }
    @Test public void localColorAndEmojiDoNotRenameOrWriteNotionContent()throws Exception{
        NotesModel m=new NotesModel();m.parent="root";FakeNotion api=new FakeNotion();api.add("category","Category","root","");api.add("document","Document","category","Text");NotesModel.Page category=existing(m,"category","Category","root","",true),document=existing(m,"document","Document","category","Text",false);sync(m,api);m.update(category,"Category",210,"");m.update(document,"Document",-1,"\uD83C\uDF31");sync(m,api);assertTrue(api.renames>0);assertEquals("Text",api.bodies.get("document"));assertEquals(210,category.hue);assertEquals("\uD83C\uDF31",document.emoji);
    }
    @Test public void unchangedPagesHaveNoNetworkWritesAndOnlyChangedDocumentUploads()throws Exception{
        NotesModel m=new NotesModel();m.parent="root";NotesModel.Page a=m.create("root","One",false),b=m.create("root","Two",false);a.body="A";b.body="B";FakeNotion api=new FakeNotion();sync(m,api);assertFalse(m.hasPending());int writes=api.writes,renames=api.renames;sync(m,api);assertEquals(writes,api.writes);assertEquals(renames,api.renames);a.body="Edited";assertTrue(m.hasPending());sync(m,api);assertEquals(writes+1,api.writes);assertFalse(m.hasPending());
    }
    @Test public void failedReplacementAfterRenameSurvivesRestartAndRetries()throws Exception{
        NotesModel m=new NotesModel();m.parent="root";NotesModel.Page p=m.create("root","One",false);p.body="Text";FakeNotion api=new FakeNotion();sync(m,api);m.update(p,"Renamed",-1,"");api.bodies.put(p.notionId(),"External edit");api.failWrite=true;try{sync(m,api);fail();}catch(IOException expected){}assertTrue(m.hasPending());m=NotesModel.read(m.json().toString());assertTrue(m.hasPending());api.failWrite=false;sync(m,api);assertFalse(m.hasPending());assertEquals("Text",api.bodies.get(p.notionId()));
    }
}
