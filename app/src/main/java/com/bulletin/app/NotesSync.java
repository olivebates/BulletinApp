package com.bulletin.app;

import org.json.*;
import java.util.*;
import java.io.IOException;

/** Serialized network operations with durable local identities and recoverable page creation. */
final class NotesSync {
    static final class InvalidNotes extends IOException {
        InvalidNotes(List<String> failures) {
            super(failures.get(0) + (failures.size() > 1
                ? " (" + (failures.size() - 1) + " more rejected operations.)" : ""));
        }
    }

    private static void recordRejection(List<String> failures, String operation,
            NotesModel.Page page, NotionClient.RejectedRequest error) throws IOException {
        String message = operation + " \"" + page.title + "\" — " + error.getMessage();
        if (error.status != 400 && error.status != 404) throw new IOException(message, error);
        failures.add(message);
    }
    private final NotesModel model;
    private final Object lock;
    private final Runnable save;
    NotesSync(NotesModel model,Object lock,Runnable save){this.model=model;this.lock=lock;this.save=save;}
    private List<NotesModel.Page> snapshot(){synchronized(lock){return new ArrayList<>(model.pages.values());}}

    private boolean reconcileArchivedDeletion(NotionClient api, NotesModel.Page page,
            NotionClient.RejectedRequest error) throws Exception {
        if (error.status != 400 || !error.getMessage().toLowerCase(Locale.ROOT)
                .contains("unarchive")) return false;
        List<NotesModel.Page> deletions = new ArrayList<>();
        synchronized (lock) {
            for (NotesModel.Page candidate : model.pages.values()) {
                if ("TRASH".equals(candidate.move) && !candidate.notionId().isEmpty()
                        && model.descendant(page.id, candidate.id)) deletions.add(candidate);
            }
        }
        for (NotesModel.Page deletion : deletions) {
            JSONObject remote;
            try {
                remote = api.request("GET", "pages/" + deletion.notionId(), null);
            } catch (NotionClient.RejectedRequest lookupError) {
                if (lookupError.status == 400 || lookupError.status == 404) continue;
                throw lookupError;
            }
            if (!remote.optBoolean("in_trash") && !remote.optBoolean("archived")) continue;
            synchronized (lock) {
                if (!"TRASH".equals(deletion.move)) continue;
                for (NotesModel.Page child : model.pages.values()) {
                    if (!model.descendant(child.id, deletion.id)) continue;
                    child.move = "";
                    child.hidden = true;
                    child.uploadPending = false;
                }
                save.run();
            }
            return true;
        }
        return false;
    }
    void run(NotionClient api)throws Exception{
        synchronized(lock){if(model.parent.isEmpty())return;for(NotesModel.Page p:model.pages.values())if(p.id.startsWith("local-")&&p.parent.isEmpty())p.parent=model.parent;save.run();}
        Set<NotesModel.Page> changed=new LinkedHashSet<>();
        synchronized(lock){for(NotesModel.Page p:model.pages.values())if(model.pending(p)){changed.add(p);p.named=false;if(!p.category)p.uploadPending=true;}save.run();}
        List<String> failures = new ArrayList<>();
        createPending(api, changed, failures);
        for (NotesModel.Page p : changed) {
            synchronized (lock) {
                if (!model.visible(p) && !model.pendingTrash(p)) continue;
                p.conflict = false;
            }
            try {
                renamePending(api, p);
            } catch (NotionClient.RejectedRequest error) {
                if (reconcileArchivedDeletion(api, p, error)) continue;
                recordRejection(failures, "Rename", p, error);
            }
        }
        synchronized(lock){for(NotesModel.Page p:model.pages.values()){NotesModel.Page parent=model.pages.get(p.parent);if(parent!=null)parent.category=true;}save.run();}
        // Hidden, queued Trash subtrees still upload drafts before their final move.
        for (NotesModel.Page p : changed) {
            synchronized (lock) {
                if (!model.descendant(p.id, model.parent) || p.notionId().isEmpty() || p.category
                    || (!model.visible(p) && !model.pendingTrash(p))) continue;
            }
            try {
                syncPage(api, p);
            } catch (NotionClient.RejectedRequest error) {
                if (reconcileArchivedDeletion(api, p, error)) continue;
                recordRejection(failures, "Upload body for", p, error);
            }
        }
        for(NotesModel.Page p:snapshot()){
            String move,destination,remote;synchronized(lock){move=p.move;remote=p.notionId();if(!model.descendant(p.id,model.parent)||move.isEmpty()||remote.isEmpty())continue;boolean pending=false;for(NotesModel.Page child:model.pages.values())if(model.descendant(child.id,p.id)&&(child.notionId().isEmpty()||!child.named||child.conflict||child.uploadPending||(!child.category&&!child.body.equals(child.base))))pending=true;if(pending)continue;destination="TRASH".equals(move)?model.trash:remoteParent(move);}
            if(destination.isEmpty()&&!"TRASH".equals(move))continue;
            try {
                if ("TRASH".equals(move)) api.request("PATCH", "pages/"+remote, new JSONObject().put("in_trash",true));
                else api.move(remote,destination);
            } catch (NotionClient.RejectedRequest error) {
                if (reconcileArchivedDeletion(api, p, error)) continue;
                recordRejection(failures, "TRASH".equals(move) ? "Archive" : "Move", p, error);
                continue;
            }
            synchronized(lock){if(p.move.equals(move)){if(!"TRASH".equals(move))p.parent=move;p.move="";if("TRASH".equals(move))p.hidden=true;}save.run();}
        }
        if (!failures.isEmpty()) throw new InvalidNotes(failures);
    }
    private String remoteParent(String id){NotesModel.Page p=model.pages.get(id);return p==null?id:p.notionId();}
    private void createPending(NotionClient api, Set<NotesModel.Page> changed, List<String> failures)throws Exception{
        Set<NotesModel.Page> rejected = new HashSet<>();
        boolean progress;
        do{progress=false;for(NotesModel.Page p:snapshot()){
            String parent,remote;synchronized(lock){if(!p.id.startsWith("local-")||!model.descendant(p.id,model.parent))continue;parent=model.notionParent(p);remote=p.notionId();if(parent.isEmpty())continue;}
            if(remote.isEmpty() && changed.contains(p) && !rejected.contains(p)){
                // A unique temporary title makes a lost POST response recoverable without duplicates.
                String marker="Bulletin pending "+p.id;
                synchronized(lock){p.creating=true;if(p.creationParent.isEmpty())p.creationParent=parent;parent=p.creationParent;save.run();}
                try {
                    for(JSONObject b:api.blocks(parent))if("child_page".equals(b.optString("type"))&&marker.equals(b.getJSONObject("child_page").optString("title")))remote=b.getString("id");
                    if(remote.isEmpty())remote=api.create(parent,marker);
                } catch (NotionClient.RejectedRequest error) {
                    recordRejection(failures, "Create", p, error);
                    rejected.add(p);
                    continue;
                }
                synchronized(lock){p.remoteId=remote;p.creating=false;save.run();}progress=true;
            }
        }}while(progress);
    }
    private void renamePending(NotionClient api,NotesModel.Page p)throws Exception{
        String title,id;synchronized(lock){if(p.named||p.notionId().isEmpty()||!model.descendant(p.id,model.parent))return;title=p.title;id=p.notionId();}
        api.rename(id,title);synchronized(lock){if(p.title.equals(title))p.named=true;save.run();}
    }
    private void syncPage(NotionClient api,NotesModel.Page p)throws Exception{
        String upload;
        synchronized(lock){upload=p.body;p.conflict=false;p.unsupported=false;}
        JSONObject written=api.replace(p.notionId(),upload);
        if(written.optJSONArray("warnings")!=null&&written.getJSONArray("warnings").length()>0)throw new IOException("Notion reported an edit warning. Draft retained; review on next sync.");
        synchronized(lock){p.base=upload;p.loaded=true;p.uploadPending=false;save.run();}
    }
}

