package com.bulletin.app;

import android.app.*;
import android.os.Bundle;
import android.content.Intent;
import android.webkit.*;
import java.util.concurrent.*;
import org.json.*;

/** Device-level checks against the same bundled editor used by Notes. */
public final class NotesEditorInstrumentation extends Instrumentation {
    private WebView web;
    private Activity activity;
    private int assertions;
    private NotesModel originalNotes;
    private int originalFontSize;
    private boolean hadFontSize;
    private boolean capturedFontSize;
    private final CountDownLatch ready=new CountDownLatch(1);
    public final class Bridge {
        @JavascriptInterface public void ready(){ready.countDown();}
        @JavascriptInterface public void changed(String text,int cursor,int scroll){}
        @JavascriptInterface public void position(int cursor,int scroll){}
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();int resultCode=Activity.RESULT_CANCELED;try{
        activity=startActivitySync(new Intent(getTargetContext(),NotesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync();runUi(()->{android.view.View root=activity.getWindow().getDecorView();for(String label:new String[]{"Back","Bulletin","Law","Tools","Settings"})if(!hasText(root,label))throw new AssertionError("Notes navigation missing: "+label);});assertions++;
        runUi(()->{
            try{
                NotesStore store=NotesStore.get(activity);if(store.connected())throw new AssertionError("Offline workflow test requires a disconnected emulator");originalNotes=store.model;android.content.SharedPreferences fontPrefs=activity.getSharedPreferences("notes-editor",0);hadFontSize=fontPrefs.contains("font-size");originalFontSize=fontPrefs.getInt("font-size",18);capturedFontSize=true;store.model=new NotesModel();
                invokeNotes("build");invokeNotes("newPage",String.class,"");Dialog menu=currentDialog();findText(menu.getWindow().getDecorView(),"Document").performClick();Dialog form=currentDialog();findInput(form.getWindow().getDecorView()).setText("Device draft");findText(form.getWindow().getDecorView(),"Create").performClick();
                if(form.isShowing())throw new AssertionError("Create did not close immediately");
                NotesModel.Page page=store.model.pages.values().iterator().next();if(!page.loaded||!page.notionId().isEmpty())throw new AssertionError("Draft is not immediately editable offline");
                android.widget.EditText title=(android.widget.EditText)findDescription(activity.getWindow().getDecorView(),"Document title");if(title==null)throw new AssertionError("Editable title box missing");title.setText("Inline title");if(!page.title.equals("Inline title"))throw new AssertionError("Title edit was not saved");title.setText("Device draft");
                android.widget.TextView back=findText(activity.getWindow().getDecorView(),"Back");android.widget.LinearLayout.LayoutParams params=(android.widget.LinearLayout.LayoutParams)back.getLayoutParams();if(params.weight!=1||params.leftMargin<=0||params.rightMargin<=0||params.bottomMargin<=0||!hasText(activity.getWindow().getDecorView(),"Find"))throw new AssertionError("Back and Find row missing");
                if(hasText(activity.getWindow().getDecorView(),"Save status and word count"))throw new AssertionError("Editor status remains visible");
                back.performClick();android.widget.TextView draft=findText(activity.getWindow().getDecorView(),"Device draft");((android.view.View)draft.getParent()).performLongClick();
                android.view.View trash=findDescription(activity.getWindow().getDecorView(),"Move Device draft to Trash");if(trash==null)throw new AssertionError("Rearrange trash control missing");if(findDescription(activity.getWindow().getDecorView(),"Drag to rearrange")==null)throw new AssertionError("Bulletin drag grip missing");trash.performClick();Dialog confirmation=currentDialog();findText(confirmation.getWindow().getDecorView(),"Move to Trash").performClick();
                if(confirmation.isShowing()||store.model.visible(page))throw new AssertionError("Trash did not immediately close and hide draft");if(!store.model.pendingTrash(page))throw new AssertionError("Trash move was not queued");
            }catch(Exception e){throw new AssertionError(e);}
        });assertions+=8;
        runUi(()->{try{
            NotesStore store=NotesStore.get(activity);store.model=new NotesModel();NotesModel.Page group=store.create("","Group",true);store.create(group.id,"Child",false);for(int i=0;i<80;i++)store.create("","Filler "+i,false);invokeNotes("build");
            findDescription(activity.getWindow().getDecorView(),"Edit category: Group").performClick();Dialog edit=currentDialog();findInput(edit.getWindow().getDecorView()).setText("Renamed Group");findText(edit.getWindow().getDecorView(),"Use parent color").performClick();((android.widget.EditText)findDescription(edit.getWindow().getDecorView(),"Category color hue")).setText("210");findText(edit.getWindow().getDecorView(),"Save").performClick();
            if(group.hue!=210||!group.title.equals("Renamed Group")||group.named)throw new AssertionError("Category edit was not saved locally");
            android.view.View header=findDescription(activity.getWindow().getDecorView(),"Category: Renamed Group, collapsed");if(header==null)throw new AssertionError("Collapsed Category header missing");header.performClick();if(findText(activity.getWindow().getDecorView(),"Child")==null)throw new AssertionError("Category did not expand while rearranging");findDescription(activity.getWindow().getDecorView(),"Category: Renamed Group, expanded").performClick();if(findText(activity.getWindow().getDecorView(),"Child")!=null)throw new AssertionError("Category did not collapse while rearranging");store.model=NotesModel.read(store.model.json().toString());invokeNotes("build");if(findDescription(activity.getWindow().getDecorView(),"Category: Renamed Group, collapsed")==null)throw new AssertionError("Collapsed state did not survive loading saved Notes");findDescription(activity.getWindow().getDecorView(),"Category: Renamed Group, collapsed").performClick();
            findText(activity.getWindow().getDecorView(),"Back").performClick();if(findDescription(activity.getWindow().getDecorView(),"Edit document: Child")!=null)throw new AssertionError("Document edit button is visible in normal mode");((android.view.View)findText(activity.getWindow().getDecorView(),"Child").getParent()).performLongClick();findDescription(activity.getWindow().getDecorView(),"Edit document: Child").performClick();Dialog document=currentDialog();findInput(document.getWindow().getDecorView()).setText("Renamed child");findDescription(document.getWindow().getDecorView(),"Document emoji").performClick();java.lang.reflect.Field pickerField=NotesActivity.class.getDeclaredField("emojiMenu");pickerField.setAccessible(true);Dialog picker=(Dialog)pickerField.get(activity);if(picker==null)throw new AssertionError("Shared emoji picker missing");findInput(picker.getWindow().getDecorView()).setText("seedling");android.widget.GridView grid=(android.widget.GridView)findType(picker.getWindow().getDecorView(),android.widget.GridView.class);if(grid.getAdapter().getCount()==0)throw new AssertionError("Emoji search found no results");grid.getAdapter().getView(0,null,grid).performClick();findText(document.getWindow().getDecorView(),"Save").performClick();
            NotesModel.Page child=null;for(NotesModel.Page p:store.model.pages.values())if(p.title.equals("Renamed child"))child=p;if(child==null||child.emoji.isEmpty()||store.model.effectiveHue(child,55)!=210)throw new AssertionError("Document name, emoji or inherited color missing");
            java.lang.reflect.Field arranging=NotesActivity.class.getDeclaredField("arranging");arranging.setAccessible(true);arranging.setBoolean(activity,true);invokeNotes("build");
        }catch(Exception e){throw new AssertionError(e);}});assertions+=7;
        layoutNotes();final int[] savedScroll={0};runUi(()->{try{android.widget.ScrollView scroll=notesScroll();if(scroll.getScrollBarStyle()!=android.view.View.SCROLLBARS_INSIDE_INSET)throw new AssertionError("Scrollbar overlays content");if(scroll.getChildAt(0).getRight()>scroll.getWidth()-scroll.getScrollBarSize())throw new AssertionError("Scrollbar lane overlaps buttons");scroll.scrollTo(0,900);savedScroll[0]=scroll.getScrollY();if(savedScroll[0]<=0)throw new AssertionError("List did not scroll");findDescription(activity.getWindow().getDecorView(),"Move Filler 9 to Trash").performClick();Dialog trash=currentDialog();findText(trash.getWindow().getDecorView(),"Move to Trash").performClick();}catch(Exception e){throw new AssertionError(e);}});
        layoutNotes();runUi(()->{try{if(notesScroll().getScrollY()!=savedScroll[0])throw new AssertionError("Trashing a note changed the scroll position");}catch(Exception e){throw new AssertionError(e);}});assertions+=2;
if(android.os.Build.VERSION.SDK_INT>=30){runUi(()->{NotesActivity notes=(NotesActivity)activity;notes.root.dispatchApplyWindowInsets(new android.view.WindowInsets.Builder().setInsets(android.view.WindowInsets.Type.ime(),android.graphics.Insets.of(0,0,0,600)).setVisible(android.view.WindowInsets.Type.ime(),true).build());for(int i=notes.root.getChildCount()-3;i<notes.root.getChildCount();i++)if(notes.root.getChildAt(i).getVisibility()!=android.view.View.GONE)throw new AssertionError("Bottom row is visible over keyboard");notes.root.dispatchApplyWindowInsets(new android.view.WindowInsets.Builder().setVisible(android.view.WindowInsets.Type.ime(),false).build());for(int i=notes.root.getChildCount()-3;i<notes.root.getChildCount();i++)if(notes.root.getChildAt(i).getVisibility()!=android.view.View.VISIBLE)throw new AssertionError("Bottom row did not return after keyboard closes");});assertions+=2;}
        runUi(()->{try{NotesStore store=NotesStore.get(activity);String previous=store.status;store.status="Synced";invokeNotes("updateSyncStatus");java.lang.reflect.Field status=NotesActivity.class.getDeclaredField("syncStatus");status.setAccessible(true);if(!"Connect to Notion using Sync".contentEquals(((android.widget.TextView)status.get(activity)).getText()))throw new AssertionError("Disconnected Notes should show connection instructions");store.status=previous;
            findText(activity.getWindow().getDecorView(),"Back").performClick();findDescription(activity.getWindow().getDecorView(),"Filler 0, Document").performClick();web=notesEditor();
        }catch(Exception e){throw new AssertionError(e);}});awaitEditor();eval("setDisplayFontSize(24,true)");waitForIdleSync();
        for(int attempt=0;attempt<30&&getTargetContext().getSharedPreferences("notes-editor",0).getInt("font-size",18)!=24;attempt++)Thread.sleep(50);
        if(getTargetContext().getSharedPreferences("notes-editor",0).getInt("font-size",18)!=24)throw new AssertionError("Display size was not saved");
        runUi(()->{try{findText(activity.getWindow().getDecorView(),"Back").performClick();web=null;findDescription(activity.getWindow().getDecorView(),"Filler 1, Document").performClick();web=notesEditor();}catch(Exception e){throw new AssertionError(e);}});awaitEditor();
        check("getComputedStyle(paper).fontSize==='24px'","Font size persists across documents");
        runUi(()->{findText(activity.getWindow().getDecorView(),"Back").performClick();web=null;});assertions+=2;
        runUi(()->{if(hasText(activity.getWindow().getDecorView(),"Find Beige"))throw new AssertionError("Empty notes show Find Beige");findText(activity.getWindow().getDecorView(),"Read-only: Off").performClick();if(hasText(activity.getWindow().getDecorView(),"+ New"))throw new AssertionError("Read-only allows creation");findDescription(activity.getWindow().getDecorView(),"Filler 1, Document").performClick();try{web=notesEditor();}catch(Exception e){throw new AssertionError(e);}if(hasText(activity.getWindow().getDecorView(),"Read-only: On"))throw new AssertionError("Read-only toggle appears inside a note");if(findDescription(activity.getWindow().getDecorView(),"Document title").isEnabled())throw new AssertionError("Read-only title is editable");});awaitEditor();
        check("paper.contentEditable==='false'","Menu read-only disables the editor");
        runUi(()->{findText(activity.getWindow().getDecorView(),"Back").performClick();findText(activity.getWindow().getDecorView(),"Read-only: On").performClick();try{NotesStore store=NotesStore.get(activity);NotesModel.Page p=store.create("","Beige target",false);p.body="<span color=\"beige\">First passage</span>\nRegular\n<span color=\"beige\">Second passage</span>";store.save();invokeNotes("build");findText(activity.getWindow().getDecorView(),"Find Beige").performClick();findText(activity.getWindow().getDecorView(),"Second passage").performClick();web=notesEditor();}catch(Exception e){throw new AssertionError(e);}});awaitEditor();
        check("paper.querySelectorAll('[data-beige=true]').length===2","Beige result opens the containing note");
        eval("setEditable(false)");
        check("!paper.innerText.includes('First passage') && !paper.innerText.includes('Second passage') && paper.innerText.includes('Regular')","Read-only hides beige and retains normal text");
        check("Array.from(paper.querySelectorAll('.beige-empty')).length===2 && Array.from(paper.querySelectorAll('.beige-empty')).every(n=>n.getBoundingClientRect().height===0)","Entire beige paragraphs collapse");
        eval("document.getElementById('findText').value='passage';findMatches()");check("matches.length===0","Find cannot reveal hidden beige text");
        eval("setEditable(true)");check("paper.innerText.includes('First passage') && markdown().includes('First passage')","Turning read-only off restores beige without data loss");
        runUi(()->{findText(activity.getWindow().getDecorView(),"Back").performClick();if(!hasText(activity.getWindow().getDecorView(),"First passage"))throw new AssertionError("Back did not return to beige results");findText(activity.getWindow().getDecorView(),"Back").performClick();if(!hasText(activity.getWindow().getDecorView(),"Read-only: Off"))throw new AssertionError("Beige Back did not return to notes list");web=null;});assertions+=4;
        runUi(()->{findText(activity.getWindow().getDecorView(),"Read-only: Off").performClick();if(hasText(activity.getWindow().getDecorView(),"Find Beige"))throw new AssertionError("Read-only exposes Find Beige");findText(activity.getWindow().getDecorView(),"Read-only: On").performClick();if(!hasText(activity.getWindow().getDecorView(),"Find Beige"))throw new AssertionError("Editable notes lost Find Beige");});assertions+=2;
        runUi(()->{findDescription(activity.getWindow().getDecorView(),"Filler 1, Document").performClick();try{web=notesEditor();}catch(Exception e){throw new AssertionError(e);}});awaitEditor();
        runUi(()->activity.finish());waitForIdleSync();web=null;
        Activity main=startActivitySync(new Intent(getTargetContext(),MainActivity.class).putExtra("navigateTab",0).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        ActivityMonitor notesMonitor=addMonitor(NotesActivity.class.getName(),null,false);
        runUi(()->findDescription(main.getWindow().getDecorView(),"Tools").performClick());
        Activity restored=notesMonitor.waitForActivityWithTimeout(10000);removeMonitor(notesMonitor);if(restored==null)throw new AssertionError("Tools did not reopen Notes");activity=restored;waitForIdleSync();
        runUi(()->{if(!"Filler 1".contentEquals(((android.widget.TextView)findDescription(activity.getWindow().getDecorView(),"Document title")).getText()))throw new AssertionError("Tools did not restore the open note");findText(activity.getWindow().getDecorView(),"Back").performClick();findText(activity.getWindow().getDecorView(),"Back").performClick();});waitForIdleSync();
        if(getTargetContext().getSharedPreferences("tool-navigation",0).getBoolean("notes-open",true))throw new AssertionError("Back from Notes did not clear restoration");
        activity=startActivitySync(new Intent(getTargetContext(),NotesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();assertions+=3;
        String html;try(java.io.InputStream in=getTargetContext().getAssets().open("notes-editor.html");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);html=out.toString("UTF-8");}
        final String source=html;runUi(()->{try{java.lang.reflect.Field field=NotesActivity.class.getDeclaredField("editing");field.setAccessible(true);field.set(activity,"instrumentation-surface");}catch(Exception e){throw new AssertionError(e);}web=new WebView(activity);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setBlockNetworkLoads(true);web.addJavascriptInterface(new Bridge(),"Native");activity.setContentView(web);web.loadDataWithBaseURL("https://bulletin.local/",source,"text/html","UTF-8",null);});
        if(!ready.await(30,TimeUnit.SECONDS))throw new AssertionError("Editor did not load");
        eval("loadDocument('',0,0,true,'#fff','#123','#abc')");
        check("paper.dataset.empty==='true' && getComputedStyle(paper,'::before').content.includes('Tap here to type...') && markdown()===''","Empty placeholder is visible without becoming document content");
        eval("loadDocument('Text',0,0,true,'#fff','#123','#abc')");check("paper.dataset.empty==='false'","Placeholder disappears for nonempty documents");
        check("bar.querySelectorAll('button').length===8 && Array.from(bar.querySelectorAll('button')).every(b=>b.querySelector('svg') && b.getAttribute('aria-label'))","Every formatting action has an accessible vector icon");
        check("!bar.querySelector('[data-command=link]') && !!bar.querySelector('[data-command=insertOrderedList]') && !bar.querySelector('[data-command=italic]') && !!bar.querySelector('[data-command=beige]')","Numbered list and beige replace link and italic");
        check("Array.from(bar.querySelectorAll('button')).every(b=>{const r=b.getBoundingClientRect();return r.width===44 && r.height===44})","Formatting buttons are identical squares");
        eval("loadDocument('First\\nSecond',0,0,true,'#fff','#123','#abc')");
        check("parseFloat(getComputedStyle(paper.firstElementChild).marginBottom)>0","Paragraphs have vertical spacing");
        eval("bar.querySelector('[data-command=fontSize]').click()");
        check("!document.getElementById('fontPanel').hidden","Aa opens the font size slider");
        eval("let slider=document.getElementById('fontSize');slider.value=26;slider.dispatchEvent(new Event('input',{bubbles:true}))");
        check("getComputedStyle(paper).fontSize==='26px' && markdown()==='First\\nSecond'","Display size changes without altering Markdown");
        eval("document.getElementById('fontClose').click();setDisplayFontSize(18,false)");
        String sample="## Heading\n**Bold** and *italic*\n- [ ] Task\n- [x] Done\n[site](https://example.com)";
        eval("loadDocument("+JSONObject.quote(sample)+",0,0,true,'#fff','#123','#abc')");
        check("paper.querySelector('h2').textContent==='Heading'","Heading renders");
        check("paper.querySelector('strong').textContent==='Bold' && paper.querySelector('em').textContent==='italic'","Inline formatting renders");
        check("paper.querySelectorAll('input[type=checkbox]').length===2 && paper.querySelectorAll('input')[1].checked","Checkboxes render");
        check("markdown()==="+JSONObject.quote(sample),"Opening preserves original Markdown");
        eval("paper.querySelector('input').click()");
        check("markdown().includes('- [x] Task')","Checkbox toggles save");
        eval("loadDocument('hello',0,0,true,'#fff','#123','#abc');paper.focus();let range=document.createRange();range.selectNodeContents(paper.firstChild);let selection=getSelection();selection.removeAllRanges();selection.addRange(range);update();bar.querySelector('[data-command=bold]').click()");
        check("markdown().includes('**hello**')","Bold toolbar saves Markdown");
        check("bar.querySelector('[data-command=bold]').classList.contains('active')","Active formatting highlights");
        eval("bar.querySelector('[data-command=undo]').click()");
        check("!markdown().includes('**hello**')","Undo removes formatting");
        eval("bar.querySelector('[data-command=redo]').click()");
        check("markdown().includes('**hello**')","Redo restores formatting");
        eval("loadDocument('beige passage',0,0,true,'#fff','#123','#abc');paper.focus();let r=document.createRange();r.selectNodeContents(paper.firstChild);getSelection().removeAllRanges();getSelection().addRange(r);update();bar.querySelector('[data-command=beige]').click()");
        check("markdown().includes('<span color=\"beige\">beige passage</span>')","Beige formatting saves a searchable passage");
        check("getComputedStyle(paper.querySelector('font')).color==='rgb(196, 160, 106)'","Beige uses the darker warm color");
        eval("bar.querySelector('[data-command=beige]').click()");
        check("!markdown().includes('color=\"beige\"') && paper.textContent==='beige passage'","All-beige selection toggles back to normal text");
        eval("loadDocument('<span color=\"beige\">First</span>\\n## <span color=\"beige\">Heading</span>\\n<span color=\"beige\">Last</span>',0,0,true,'#fff','#123','#abc');paper.focus();let r=document.createRange();r.selectNodeContents(paper);getSelection().removeAllRanges();getSelection().addRange(r);update();bar.querySelector('[data-command=beige]').click()");
        check("!markdown().includes('color=\"beige\"') && paper.querySelector('h2').textContent==='Heading'","Reloaded beige toggles off across headings without removing headings");
        eval("loadDocument('<span color=\"beige\">Colored</span> plain',0,0,true,'#fff','#123','#abc');paper.focus();let r=document.createRange();r.selectNodeContents(paper);getSelection().removeAllRanges();getSelection().addRange(r);update();bar.querySelector('[data-command=beige]').click()");
        check("selectedTextRuns(getSelection().getRangeAt(0)).every(isBeige)","Mixed selection becomes entirely beige");
        eval("loadDocument('<span color=\"beige\">keep remove keep</span>',0,0,true,'#fff','#123','#abc');paper.focus();let t=paper.querySelector('[data-beige=true]').firstChild,r=document.createRange();r.setStart(t,5);r.setEnd(t,11);getSelection().removeAllRanges();getSelection().addRange(r);update();bar.querySelector('[data-command=beige]').click();let saved=markdown();loadDocument(saved,0,0,true,'#fff','#123','#abc')");
        check("paper.textContent==='keep remove keep' && !isBeige(Array.from(paper.firstChild.childNodes).find(n=>n.nodeType===3&&n.textContent.includes('remove'))) && paper.querySelectorAll('[data-beige=true]').length===2","Partial removal preserves surrounding beige after reload");
        eval("loadDocument('<span color=\"beige\">**Beige** passage</span>',0,0,true,'#fff','#123','#abc');dirty=true");
        check("paper.querySelector('[data-beige=true] strong').textContent==='Beige' && markdown().includes('<span color=\"beige\">Beige</span>') && markdown().includes('<span color=\"beige\"> passage</span>')","Beige and nested bold survive reload and serialization");
        eval("loadDocument('Alpha alpha ALPHA',0,0,false,'#fff','#123','#abc');toggleFind();document.getElementById('findText').value='alpha';findMatches()");
        check("matches.length===3 && document.getElementById('matchCount').textContent==='1/3' && paper.contentEditable==='false'","Find works case-insensitively in read-only mode");
        eval("moveMatch(-1)");check("document.getElementById('matchCount').textContent==='3/3'","Previous match wraps");
        eval("moveMatch(1);toggleFind()");check("document.getElementById('matchCount').textContent==='1/3' && searchPanel.hidden","Next match wraps and Find closes");
        eval("loadDocument('<script>window.injected=true</script>',0,0,true,'#fff','#123','#abc')");
        check("!window.injected && !paper.querySelector('script')","Document markup cannot execute");
        eval("loadDocument('one\\ntwo',0,0,true,'#fff','#123','#abc');setCaret(4)");
        check("caret()===4","Caret position restores");
        eval("loadDocument('- Parent\\n  - Child\\n- Sibling',0,0,true,'#fff','#123','#abc');dirty=true");
        check("paper.querySelector('li ul li').textContent==='Child' && markdown().includes('  - Child')","Nested lists retain structure");
        eval("loadDocument('```java\\nint x = 1;\\n```',0,0,true,'#fff','#123','#abc');dirty=true");
        check("markdown().includes('```java') && markdown().includes('int x = 1;')","Code block language survives editing");
        eval("loadDocument('```html\\n<span color=\"beige\">line\\nline</span>\\n```',0,0,true,'#fff','#123','#abc');dirty=true");
        check("paper.querySelector('pre').textContent.includes('<span color=\"beige\">line\\nline</span>') && !paper.querySelector('[data-beige=true]')","Multiline beige markup in code remains literal");
        eval("loadDocument('read only',0,0,false,'#fff','#123','#abc')");
        check("paper.contentEditable==='false' && Array.from(bar.querySelectorAll('button')).filter(b=>b.dataset.command!=='fontSize').every(b=>b.disabled)","Unsupported pages are protected");
        result.putString("stream","Passed "+assertions+" rich editor assertions\n");resultCode=Activity.RESULT_OK;
    }catch(Throwable e){result.putString("stream","FAILED: "+e+"\n");}finally{if(web!=null)runUi(()->web.destroy());if(capturedFontSize)runUi(()->{android.content.SharedPreferences.Editor settings=getTargetContext().getSharedPreferences("notes-editor",0).edit();if(hadFontSize)settings.putInt("font-size",originalFontSize);else settings.remove("font-size");settings.commit();});if(originalNotes!=null)runUi(()->{NotesStore store=NotesStore.get(activity);store.model=originalNotes;store.save();});if(activity!=null)activity.finish();}finish(resultCode,result);}
    private void runUi(Runnable action){Throwable[] error={null};super.runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private NotesEditor notesEditor()throws Exception{java.lang.reflect.Field field=NotesActivity.class.getDeclaredField("editor");field.setAccessible(true);return (NotesEditor)field.get(activity);}
    private void awaitEditor()throws Exception{for(int i=0;i<100;i++){if("true".equals(eval("return typeof setDisplayFontSize==='function'")))return;Thread.sleep(50);}throw new AssertionError("Notes editor did not load");}
    private String eval(String script)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] value={null};runUi(()->web.evaluateJavascript("(()=>{"+script+"})()",v->{value[0]=v;done.countDown();}));if(!done.await(10,TimeUnit.SECONDS))throw new AssertionError("JavaScript timed out");return value[0];}
    private void check(String expression,String description)throws Exception{if(!"true".equals(eval("return "+expression)))throw new AssertionError(description);assertions++;}
    private boolean hasText(android.view.View view,String value){if(view instanceof android.widget.TextView&&value.contentEquals(((android.widget.TextView)view).getText()))return true;if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(hasText(group.getChildAt(i),value))return true;}return false;}
    private android.widget.TextView findText(android.view.View view,String value){if(view instanceof android.widget.TextView&&value.contentEquals(((android.widget.TextView)view).getText()))return (android.widget.TextView)view;if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){android.widget.TextView found=findText(group.getChildAt(i),value);if(found!=null)return found;}}return null;}
    private android.widget.EditText findInput(android.view.View view){if(view instanceof android.widget.EditText)return (android.widget.EditText)view;if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){android.widget.EditText found=findInput(group.getChildAt(i));if(found!=null)return found;}}return null;}
    private android.view.View findDescription(android.view.View view,String description){if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){android.view.View found=findDescription(group.getChildAt(i),description);if(found!=null)return found;}}return null;}
    private Dialog currentDialog()throws Exception{java.lang.reflect.Field field=NotesActivity.class.getDeclaredField("dialog");field.setAccessible(true);return (Dialog)field.get(activity);}
    private void invokeNotes(String method)throws Exception{java.lang.reflect.Method m=NotesActivity.class.getDeclaredMethod(method);m.setAccessible(true);m.invoke(activity);}
    private void invokeNotes(String method,Class<?> type,Object value)throws Exception{java.lang.reflect.Method m=NotesActivity.class.getDeclaredMethod(method,type);m.setAccessible(true);m.invoke(activity,value);}
    private android.view.View findType(android.view.View view,Class<?> type){if(type.isInstance(view))return view;if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){android.view.View found=findType(group.getChildAt(i),type);if(found!=null)return found;}}return null;}
    private android.widget.ScrollView notesScroll()throws Exception{java.lang.reflect.Field field=NotesActivity.class.getDeclaredField("scroll");field.setAccessible(true);return (android.widget.ScrollView)field.get(activity);}
    private void layoutNotes()throws Exception{CountDownLatch drawn=new CountDownLatch(1);runUi(()->{android.view.View root=((NotesActivity)activity).root;root.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){root.getViewTreeObserver().removeOnPreDrawListener(this);drawn.countDown();return true;}});root.requestLayout();});if(!drawn.await(10,TimeUnit.SECONDS))throw new AssertionError("Notes layout timed out");waitForIdleSync();}
}

