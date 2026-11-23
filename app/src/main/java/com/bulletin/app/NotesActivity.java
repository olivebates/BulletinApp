package com.bulletin.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import android.text.*;
import java.util.*;

public final class NotesActivity extends ToolScreen {
    private NotesStore store;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Set<String> expanded=new HashSet<>();
    private String editing="",dragId="";
    private boolean arranging,notesResumed,beigeResults,returnToBeige; private int beigeScrollY,jumpBeige=-1;
    private Dialog dialog;
    private ScrollView scroll;
    private int listScrollY;
    private boolean restoringScroll;
    private Dialog emojiMenu;

    private float dragSpeed;
    private final Runnable autoScroll=new Runnable(){public void run(){if(dragId.isEmpty()||scroll==null)return;scroll.scrollBy(0,Math.round(dragSpeed/60f));handler.postDelayed(this,16);}};
    private NotesEditor editor;
    private EditText documentTitle;
    private boolean refreshingTitle;
    private boolean keyboardOpen,leavingTools;
    private final List<View> bottomRows=new ArrayList<>();
    private TextView syncStatus;
    
    
    private final Runnable syncIndicator=new Runnable(){public void run(){if(!notesResumed||isFinishing()||isDestroyed()||!editing.isEmpty())return;updateSyncStatus();if(store.busy)handler.postDelayed(this,1000);}};
    @Override public void onCreate(Bundle state){super.onCreate(state);store=NotesStore.get(this);getSharedPreferences("tool-navigation",0).edit().putBoolean("notes-open",true).putBoolean("youtube-open",false).apply();android.content.SharedPreferences saved=getSharedPreferences("notes-location",0);editing=saved.getString("editing","");listScrollY=saved.getInt("scroll",0);beigeResults=saved.getBoolean("beige",false);returnToBeige=saved.getBoolean("returnBeige",false);beigeScrollY=saved.getInt("beigeScroll",0);if(state!=null){editing=state.getString("editing","");arranging=state.getBoolean("arranging");listScrollY=state.getInt("listScrollY");ArrayList<String> a=state.getStringArrayList("expanded");if(a!=null)expanded.addAll(a);}build();if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::back);sync(false);}
    private void sync(boolean picker){updateSyncStatus();}
    private void updateSyncStatus(){
        if(syncStatus==null||!editing.isEmpty())return;
        String message=store.displayStatus();
        if(!message.contentEquals(syncStatus.getText()))syncStatus.setText(message);
        syncStatus.setVisibility(message.isEmpty()?View.GONE:View.VISIBLE);
    }
    @Override void leavingToolsTab(){if(editor==null)super.leavingToolsTab();}
    private void build(){
        if(scroll!=null&&scroll.isAttachedToWindow()&&editor==null&&!restoringScroll)listScrollY=scroll.getScrollY();
        synchronized(store){expanded.clear();for(NotesModel.Page p:store.model.pages.values())if(p.category&&!p.collapsed)expanded.add(p.id);}
        documentTitle=null;scroll=null;bottomRows.clear();root.removeAllViews();if(editor!=null){editor.flush();editor.destroy();editor=null;}syncStatus=text(store.status,12);
        synchronized(store){if(!editing.isEmpty()){NotesModel.Page p=store.model.pages.get(editing);if(p!=null){buildEditor(p);return;}editing="";}}
        if(store.readOnly){beigeResults=false;returnToBeige=false;}if(beigeResults){buildBeige();return;}title("Notes");root.addView(syncStatus);updateSyncStatus();handler.removeCallbacks(syncIndicator);handler.post(syncIndicator);
        scroll=new ScrollView(this);scroll.setFillViewport(arranging);scroll.setClipToPadding(false);
        if(arranging){scroll.setVerticalScrollBarEnabled(true);scroll.setHorizontalScrollBarEnabled(false);scroll.setVerticalScrollbarPosition(View.SCROLLBAR_POSITION_RIGHT);scroll.setScrollBarSize(ui.dp(30));scroll.setScrollbarFadingEnabled(false);scroll.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
            if(Build.VERSION.SDK_INT>=29){android.graphics.drawable.GradientDrawable thumb=new android.graphics.drawable.GradientDrawable();thumb.setColor(ui.border);thumb.setCornerRadius(ui.dp(4));android.graphics.drawable.GradientDrawable track=new android.graphics.drawable.GradientDrawable();track.setColor((ui.border&0xffffff)|0x33000000);track.setCornerRadius(ui.dp(4));scroll.setVerticalScrollbarThumbDrawable(thumb);scroll.setVerticalScrollbarTrackDrawable(track);}}
        LinearLayout list=column();list.setPadding(ui.dp(8),0,ui.dp(8),0);scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        final ScrollView currentScroll=scroll;restoringScroll=true;currentScroll.setOnScrollChangeListener((v,x,y,ox,oy)->{if(scroll==currentScroll&&!restoringScroll)listScrollY=y;});

        synchronized(store){Set<String> drawn=new HashSet<>();for(NotesModel.Page p:store.model.pages.values()){NotesModel.Page parent=store.model.pages.get(p.parent);if(store.model.visible(p)&&(parent==null||!store.model.visible(parent)))addPage(list,p,0,drawn);}}
        if(arranging){TextView top=text("Drop here to move outside categories",11);top.setTextColor(ui.border);top.setGravity(Gravity.CENTER);list.addView(top,new LinearLayout.LayoutParams(-1,ui.dp(64)));drop(top,null);}
        else if(!store.readOnly){Button add=button("+ New");list.addView(add,new LinearLayout.LayoutParams(-1,ui.dp(48)));add.setOnClickListener(v->newPage(store.model.parent));}
        LinearLayout bar=row();Button back=button("Back");action(bar,back);Button lock=button(store.readOnly?"Read-only: On":"Read-only: Off"),beige=button("Find Beige");action(bar,lock);if(!store.readOnly&&hasBeige())action(bar,beige);lock.setOnClickListener(v->{store.readOnly=!store.readOnly;arranging=false;build();});beige.setOnClickListener(v->{beigeResults=true;build();});root.addView(bar);back.setOnClickListener(v->back());if(!store.connected()){Button sync=button("Sync");action(bar,sync);sync.setOnClickListener(v->connection());}addToolNavigation();trackBottomRows();
        final int restoreY=listScrollY;currentScroll.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){if(currentScroll.getViewTreeObserver().isAlive())currentScroll.getViewTreeObserver().removeOnPreDrawListener(this);if(scroll==currentScroll){currentScroll.scrollTo(0,restoreY);listScrollY=currentScroll.getScrollY();restoringScroll=false;}return true;}});
    }
    private int defaultHue(){return Math.round((((BulletinApplication)getApplication()).store().game.hue+55)%360);}
    private Ui palette(NotesModel.Page p){return new Ui(this,store.model.effectiveHue(p,defaultHue()));}
    private TextView coloredText(String value,int size,Ui palette){TextView v=text(value,size);v.setTextColor(palette.text);return v;}
    private View documentIcon(NotesModel.Page p,Ui palette,int size){if(p.emoji.isEmpty())return palette.icon("bulletin",palette.border,size);TextView emoji=coloredText(p.emoji,24,palette);emoji.setGravity(Gravity.CENTER);emoji.setPadding(0,0,0,0);LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(ui.dp(size),ui.dp(size));iconParams.leftMargin=8;emoji.setLayoutParams(iconParams);emoji.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return emoji;}
    private void addPage(LinearLayout list,NotesModel.Page p,int depth,Set<String> drawn){
        if(!drawn.add(p.id))return;
        if(p.category){categoryRow(list,p,depth);if(expanded.contains(p.id))for(NotesModel.Page child:store.model.pages.values())if(child.parent.equals(p.id)&&store.model.visible(child))addPage(list,child,depth+1,drawn);return;}
        if(arranging){arrangePage(list,p,depth);return;}
        Ui palette=palette(p);LinearLayout row=row();row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(ui.dp(Math.min(depth,12)*16),0,0,ui.dp(6));row.setBackgroundColor(ui.background);
        LinearLayout entry=row();entry.setGravity(Gravity.CENTER_VERTICAL);entry.setBackground(palette.button(palette.border));entry.setFocusable(true);entry.setContentDescription(p.title+", Document");entry.addView(documentIcon(p,palette,32));
        TextView title=coloredText(p.title,15,palette);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);entry.addView(title,new LinearLayout.LayoutParams(0,-2,1));entry.addView(coloredText("("+store.model.words(p)+")",13,palette));row.addView(entry,new LinearLayout.LayoutParams(0,ui.dp(52),1));
        entry.setOnClickListener(v->{editing=p.id;build();});entry.setOnLongClickListener(v->{if(!store.readOnly){arranging=true;build();}return true;});
        list.addView(row);
    }
    private void categoryRow(LinearLayout list,NotesModel.Page p,int depth){
        Ui palette=palette(p);FrameLayout header=new FrameLayout(this);header.setMinimumHeight(ui.dp(32));boolean open=expanded.contains(p.id);
        TextView name=coloredText((open?"\u25be  ":"\u25b8  ")+p.title+" ("+store.model.words(p)+")",13,palette);name.setTextColor(palette.border);name.setTypeface(android.graphics.Typeface.create("sans-serif-light",android.graphics.Typeface.NORMAL));name.setGravity(getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE?Gravity.CENTER:Gravity.CENTER_VERTICAL);name.setPadding(ui.dp(8),0,ui.dp(arranging?108:36),0);
        name.setContentDescription("Category: "+p.title+(open?", expanded":", collapsed"));header.addView(name,new FrameLayout.LayoutParams(-1,ui.dp(32)));name.setOnClickListener(v->{store.setCollapsed(p.id,!p.collapsed);build();});name.setOnLongClickListener(v->{if(arranging)return beginDrag(header,p);if(!store.readOnly){arranging=true;build();}return true;});
        TextView plus=coloredText("+",20,palette);plus.setTextColor(palette.border);plus.setGravity(Gravity.CENTER);plus.setContentDescription("Create inside "+p.title);header.addView(plus,new FrameLayout.LayoutParams(ui.dp(36),ui.dp(32),Gravity.END|Gravity.CENTER_VERTICAL));plus.setIncludeFontPadding(false);plus.setPadding(0,0,0,0);plus.setVisibility(store.readOnly?View.GONE:View.VISIBLE);plus.setOnClickListener(v->newPage(p.id));
        if(arranging){LinearLayout actions=row();actions.addView(editButton(p,20),new LinearLayout.LayoutParams(ui.dp(32),ui.dp(32)));actions.addView(trashButton(p,20),new LinearLayout.LayoutParams(ui.dp(32),ui.dp(32)));FrameLayout.LayoutParams ap=new FrameLayout.LayoutParams(ui.dp(64),ui.dp(32),Gravity.END);ap.rightMargin=ui.dp(40);header.addView(actions,ap);drop(header,p);}
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,ui.dp(32));layout.setMargins(ui.dp(Math.min(depth,12)*16),ui.dp(12),0,ui.dp(12));list.addView(header,layout);
    }
    private void arrangePage(LinearLayout list,NotesModel.Page p,int depth){Ui palette=palette(p);
            LinearLayout card=row();card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(palette.dp(8),0,palette.dp(8),0);card.setBackground(palette.button(palette.border));
            View grip=dragGrip(palette);LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(palette.dp(16),palette.dp(36));gp.rightMargin=palette.dp(6);card.addView(grip,gp);immediateDrag(grip,card,p);
            card.addView(documentIcon(p,palette,30));TextView name=coloredText(p.title,14,palette);name.setPadding(palette.dp(8),0,palette.dp(4),0);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(name,new LinearLayout.LayoutParams(0,-2,1));card.addView(coloredText("("+store.model.words(p)+")",11,palette));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(ui.dp(32),ui.dp(36));ep.rightMargin=ui.dp(4);card.addView(editButton(p,24),ep);card.addView(trashButton(p,24),new LinearLayout.LayoutParams(palette.dp(32),palette.dp(36)));
            card.setOnLongClickListener(v->beginDrag(card,p));card.setContentDescription(p.title+". Drag to rearrange");
            if(getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE){
                String tag="notes-grid-"+depth;View last=list.getChildCount()==0?null:list.getChildAt(list.getChildCount()-1);LinearLayout grid;
                if(last instanceof LinearLayout&&tag.equals(last.getTag())&&((LinearLayout)last).getChildCount()==2&&"empty-slot".equals(((LinearLayout)last).getChildAt(1).getTag())){grid=(LinearLayout)last;grid.removeViewAt(1);}else{grid=row();grid.setTag(tag);grid.setPadding(palette.dp(depth*16),0,0,0);list.addView(grid);}
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,palette.dp(48),1);cp.setMargins(palette.dp(4),palette.dp(4),palette.dp(4),palette.dp(4));grid.addView(card,cp);if(grid.getChildCount()==1){View empty=new View(this);empty.setTag("empty-slot");grid.addView(empty,new LinearLayout.LayoutParams(0,palette.dp(48),1));}
            }else{LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,palette.dp(48));layout.setMargins(palette.dp(4+depth*16),palette.dp(4),palette.dp(4),palette.dp(4));list.addView(card,layout);}drop(card,p);
    }
    private Button iconButton(NotesModel.Page p,String iconName,String description,int size){
        Ui palette=palette(p);Button b=button("");b.setBackground(palette.button(palette.border));((BorderedButton)b).setBorderColor(palette.border);Ui.Icon icon=new Ui.Icon(iconName,palette.border);icon.setBounds(0,0,ui.dp(size),ui.dp(size));b.setCompoundDrawables(icon,null,null,null);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(ui.dp(4),0,ui.dp(4),0);b.setContentDescription(description);return b;
    }
    private Button trashButton(NotesModel.Page p,int size){Button b=iconButton(p,"trash","Move "+p.title+" to Trash",size);b.setOnClickListener(v->confirmTrash(p));return b;}
    private Button editButton(NotesModel.Page p,int size){Button b=iconButton(p,"edit","Edit "+(p.category?"category: ":"document: ")+p.title,size);b.setOnClickListener(v->editPage(p));return b;}
    private View dragGrip(Ui palette){return new View(this){final android.graphics.Paint paint=new android.graphics.Paint(3);@Override protected void onDraw(android.graphics.Canvas c){super.onDraw(c);paint.setColor(palette.border);paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(1);c.drawRect(.5f,.5f,getWidth()-.5f,getHeight()-.5f,paint);paint.setStyle(android.graphics.Paint.Style.FILL);for(int r=0;r<3;r++)for(int col=0;col<2;col++)c.drawCircle(getWidth()*(col==0?.33f:.67f),getHeight()/2f+palette.dp((r-1)*7),palette.dp(1.2f),paint);}};}
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private void immediateDrag(View grip,View card,NotesModel.Page p){grip.setContentDescription("Drag to rearrange");grip.setOnClickListener(v->beginDrag(card,p));grip.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN)v.performClick();return true;});}
    private boolean beginDrag(View card,NotesModel.Page p){if(store.readOnly)return false;if(!dragId.isEmpty())return false;dragId=p.id;boolean started=card.startDragAndDrop(ClipData.newPlainText("note",p.id),new View.DragShadowBuilder(card),card,0);if(started){card.setAlpha(.3f);handler.post(autoScroll);}else dragId="";return started;}
    private void drop(View view,NotesModel.Page target){if(target!=null)view.setTag(target.id);view.setOnDragListener((v,event)->{switch(event.getAction()){
        case DragEvent.ACTION_DRAG_STARTED:return !dragId.isEmpty();
        case DragEvent.ACTION_DRAG_ENTERED:v.setAlpha(.5f);return true;
        case DragEvent.ACTION_DRAG_LOCATION:previewOrder(v,event,target);if(scroll!=null){int[] vp=new int[2],sp=new int[2];v.getLocationOnScreen(vp);scroll.getLocationOnScreen(sp);float density=getResources().getDisplayMetrics().density;dragSpeed=DragScroll.speed((vp[1]+event.getY()-sp[1])/density,scroll.getHeight()/density)*density;}return true;
        case DragEvent.ACTION_DRAG_EXITED:v.setAlpha(1);dragSpeed=0;return true;
        case DragEvent.ACTION_DRAG_ENDED:v.setAlpha(1);dragSpeed=0;handler.removeCallbacks(autoScroll);if(!dragId.isEmpty()){dragId="";handler.post(()->{if(!isDestroyed())build();});}return true;
        case DragEvent.ACTION_DROP:v.setAlpha(1);try{synchronized(store){NotesModel.Page movingPage=store.model.pages.get(dragId);boolean siblingCategory=target!=null&&target.category&&movingPage!=null&&movingPage.category&&movingPage.parent.equals(target.parent);String parent=target==null?store.model.parent:target.category&&!siblingCategory?target.id:target.parent;if(target!=null&&target.id.equals(dragId))return true;store.move(dragId,parent);
            if(target!=null&&(!target.category||siblingCategory)){LinkedHashMap<String,NotesModel.Page> ordered=new LinkedHashMap<>();NotesModel.Page moving=store.model.pages.get(dragId);for(NotesModel.Page p:store.model.pages.values()){if(p.id.equals(target.id)&&event.getY()<v.getHeight()/2f)ordered.put(moving.id,moving);if(!p.id.equals(moving.id))ordered.put(p.id,p);if(p.id.equals(target.id)&&event.getY()>=v.getHeight()/2f)ordered.put(moving.id,moving);}store.model.pages.clear();store.model.pages.putAll(ordered);store.save();}}
            sync(false);}catch(Exception e){message(e.getMessage());}return true;default:return true;}});}
    private Dialog showDialog(LinearLayout content){Dialog d=NotesDialogs.show(this,ui,content);dialog=d;d.setOnDismissListener(v->{if(dialog==d)dialog=null;});return d;}
    private boolean previewPending;
    private void previewOrder(View targetView,DragEvent event,NotesModel.Page target){
        if(previewPending||target==null||!(event.getLocalState() instanceof View))return;
        View moving=(View)event.getLocalState();if(moving==targetView||moving.getParent()!=targetView.getParent()||!(moving.getParent() instanceof LinearLayout))return;
        NotesModel.Page page=store.model.pages.get(dragId);if(page==null||target.category&&!page.category||!page.parent.equals(target.parent))return;
        LinearLayout parent=(LinearLayout)moving.getParent();int from=parent.indexOfChild(moving),to=parent.indexOfChild(targetView);if(from<0||to<0)return;
        if(from<to&&event.getY()<targetView.getHeight()/2f||from>to&&event.getY()>targetView.getHeight()/2f)return;
        previewPending=true;parent.post(()->{previewPending=false;if(dragId.isEmpty()||moving.getParent()!=parent)return;
            List<View> block=new ArrayList<>();for(int i=0;i<parent.getChildCount();i++){View child=parent.getChildAt(i);if(child==moving||child.getTag() instanceof String&&store.model.descendant((String)child.getTag(),page.id))block.add(child);}
            View anchor=targetView;if(from<to&&target.category)for(int i=to+1;i<parent.getChildCount();i++){View child=parent.getChildAt(i);if(child.getTag() instanceof String&&store.model.descendant((String)child.getTag(),target.id))anchor=child;else break;}
            if(block.contains(anchor))return;if(Feedback.motion(this))android.transition.TransitionManager.beginDelayedTransition(parent,new android.transition.ChangeBounds().setDuration(160));for(View child:block)parent.removeView(child);int destination=parent.indexOfChild(anchor)+(from<to?1:0);for(View child:block)parent.addView(child,Math.min(destination++,parent.getChildCount()));});
    }
    private void confirmTrash(NotesModel.Page p){if(store.readOnly)return;
        LinearLayout content=column();content.addView(text("Move to Trash?",18));content.addView(text("Remove "+p.title+(p.category?" and its nested documents":"")+" from Notes? They will be archived in Notion when connected.",14));
        LinearLayout actions=row();Button no=button("Cancel"),yes=button("Move to Trash");action(actions,no);action(actions,yes);content.addView(actions);Dialog d=showDialog(content);no.setOnClickListener(v->d.dismiss());yes.setOnClickListener(v->{try{store.trash(p.id);d.dismiss();build();sync(false);}catch(Exception e){message(e.getMessage());}});
    }
    private void newPage(String parent){if(store.readOnly)return;
        LinearLayout content=column();content.addView(text("ADD TO NOTES",18));Button document=button("Document"),category=button("Category");content.addView(document,NotesDialogs.field(ui));content.addView(category,NotesDialogs.field(ui));Dialog d=showDialog(content);
        document.setOnClickListener(v->{d.dismiss();createPage(parent,false);});category.setOnClickListener(v->{d.dismiss();createPage(parent,true);});
    }
    private void createPage(String parent,boolean category){
        NotesModel.Page draft=new NotesModel.Page();draft.parent=parent;draft.category=category;draft.title="";editPage(draft,true);
    }
    private void editPage(NotesModel.Page p){if(store.readOnly)return;editPage(p,false);}
    private void editPage(NotesModel.Page p,boolean creating){
        Ui palette=palette(p);LinearLayout content=column();content.addView(text(creating?(p.category?"New Category":"New Document"):(p.category?"Edit Category":"Edit Document"),18));
        EditText name=NotesDialogs.input(this,palette,"Name");name.setText(p.title);name.setSelectAllOnFocus(true);name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(200)});content.addView(name,NotesDialogs.field(ui));
        final EditText[] emoji={null},hue={null};final CheckBox[] inherit={null};
        if(p.category){
            content.addView(text("Color hue (0–359)",14));int inheritedHue=store.model.effectiveHue(store.model.pages.get(p.parent),defaultHue());
            CheckBox useParent=new CheckBox(this);useParent.setText("Use parent color");useParent.setTextColor(ui.text);useParent.setChecked(p.hue<0);content.addView(useParent);inherit[0]=useParent;
            EditText value=NotesDialogs.input(this,palette,"Color hue");value.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);value.setContentDescription("Category color hue");value.setText(Integer.toString(store.model.effectiveHue(p,defaultHue())));content.addView(value,NotesDialogs.field(ui));hue[0]=value;
            SeekBar slider=new SeekBar(this);slider.setMax(359);slider.setProgress(store.model.effectiveHue(p,defaultHue()));slider.setContentDescription("Category color");content.addView(slider,NotesDialogs.field(ui));
            TextView preview=text("Category color",16);preview.setGravity(Gravity.CENTER);content.addView(preview,NotesDialogs.field(ui));
            Runnable refresh=()->{int number=inheritedHue;try{if(!useParent.isChecked())number=Integer.parseInt(value.getText().toString());}catch(NumberFormatException ignored){}number=Math.max(0,Math.min(359,number));Ui color=new Ui(this,number);preview.setTextColor(color.text);preview.setBackground(color.panel(color.border));slider.setProgressTintList(android.content.res.ColorStateList.valueOf(color.border));slider.setThumbTintList(android.content.res.ColorStateList.valueOf(color.border));value.setEnabled(!useParent.isChecked());slider.setEnabled(!useParent.isChecked());};
            value.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){try{int n=Integer.parseInt(s.toString());if(n>=0&&n<=359&&slider.getProgress()!=n)slider.setProgress(n);}catch(NumberFormatException ignored){}refresh.run();}public void afterTextChanged(Editable e){}});
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar v,int n,boolean user){if(user)value.setText(Integer.toString(n));}public void onStartTrackingTouch(SeekBar v){}public void onStopTrackingTouch(SeekBar v){}});
            useParent.setOnCheckedChangeListener((v,checked)->refresh.run());refresh.run();
        }else{
            content.addView(text("Emoji",14));EditText value=NotesDialogs.input(this,palette,"Choose emoji");value.setContentDescription("Document emoji");value.setText(p.emoji);value.setFocusable(false);value.setCursorVisible(false);value.setShowSoftInputOnFocus(false);content.addView(value,NotesDialogs.field(ui));emoji[0]=value;
        }
        LinearLayout actions=row();Button cancel=button("Cancel"),save=button(creating?"Create":"Save");action(actions,cancel);action(actions,save);content.addView(actions);Dialog d=showDialog(content);cancel.setOnClickListener(v->d.dismiss());
        if(emoji[0]!=null)emoji[0].setOnClickListener(v->{hideKeyboard();emojiMenu=EmojiPickerDialog.show(this,palette,emoji[0],d,()->emojiMenu=null);});
        save.setOnClickListener(v->{String title=name.getText().toString().trim();if(title.isEmpty()){name.setError("Enter a name");return;}int color=-1;if(p.category&&!inherit[0].isChecked()){try{color=Integer.parseInt(hue[0].getText().toString());if(color<0||color>359)throw new NumberFormatException();}catch(NumberFormatException e){hue[0].setError("Enter 0–359");return;}}
            try{NotesModel.Page target=creating?store.create(p.parent,title,p.category):p;store.update(target,title,color,p.category?p.emoji:emoji[0].getText().toString());if(creating){store.setCollapsed(p.parent,false);if(!p.category){editing=target.id;arranging=false;}}hideKeyboard();d.dismiss();build();sync(false);}catch(Exception e){message(e.getMessage());}});
    }
    private void hideKeyboard(){View focus=getCurrentFocus();if(focus!=null)getSystemService(android.view.inputmethod.InputMethodManager.class).hideSoftInputFromWindow(focus.getWindowToken(),0);}
    private EditText input(String hint){EditText v=new EditText(this);v.setHint(hint);v.setTextColor(ui.text);v.setHintTextColor(ui.muted);v.setSingleLine(true);return v;}
    private void connection(){NotionConnection.show(this,()->{build();sync(false);});}
    private void buildEditor(NotesModel.Page p){
        root.setFocusableInTouchMode(true);root.requestFocus();
        documentTitle=NotesDialogs.input(this,palette(p),"Document title");documentTitle.setContentDescription("Document title");documentTitle.setTextSize(20);documentTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);documentTitle.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});documentTitle.setText(p.title);documentTitle.setEnabled(!store.readOnly);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,ui.dp(52));tp.setMargins(ui.dp(8),ui.dp(8),ui.dp(8),ui.dp(4));root.addView(documentTitle,tp);
        documentTitle.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(Editable e){if(refreshingTitle||store.readOnly)return;String title=e.toString().trim();if(title.isEmpty())return;try{store.rename(p,title);}catch(Exception error){message(error.getMessage());}}});
        documentTitle.setOnFocusChangeListener((v,focused)->{if(!focused&&documentTitle!=null&&documentTitle.getText().toString().trim().isEmpty()){refreshingTitle=true;documentTitle.setText(p.title);documentTitle.setEnabled(!store.readOnly);refreshingTitle=false;}});
        editor=new NotesEditor(this,palette(p),p.id,p.body,!store.readOnly&&!p.category,value->{if(store.readOnly)return;try{store.edit(p.id,value);}catch(Exception ex){message(ex.getMessage());}});
        root.addView(editor,new LinearLayout.LayoutParams(-1,0,1));if(jumpBeige>=0){editor.jumpBeige(jumpBeige);jumpBeige=-1;}
        LinearLayout bar=row();Button back=button("Back"),find=button("Find");action(bar,back);action(bar,find);back.setOnClickListener(v->back());find.setOnClickListener(v->editor.toggleFind());root.addView(bar);addToolNavigation();trackBottomRows();
    }
    private boolean hasBeige(){synchronized(store){for(NotesModel.Page p:store.model.pages.values())if(!p.category&&store.model.visible(p)&&!BeigePassages.find(p.body).isEmpty())return true;}return false;}
    private void buildBeige(){
        title("Find Beige");ScrollView results=new ScrollView(this);LinearLayout list=column();results.addView(list);root.addView(results,new LinearLayout.LayoutParams(-1,0,1));
        synchronized(store){for(NotesModel.Page p:store.model.pages.values())if(!p.category&&store.model.visible(p))for(BeigePassages.Passage passage:BeigePassages.find(p.body)){
            Button entry=button(passage.text);entry.setTextColor(0xffc4a06a);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(ui.dp(8),ui.dp(6),ui.dp(8),ui.dp(6));list.addView(entry,lp);entry.setOnClickListener(v->{beigeScrollY=results.getScrollY();beigeResults=false;returnToBeige=true;editing=p.id;jumpBeige=passage.firstSpan;build();});}}
        if(list.getChildCount()==0)list.addView(text("No beige text in your notes.",16));results.post(()->results.scrollTo(0,beigeScrollY));results.setOnScrollChangeListener((v,x,y,ox,oy)->beigeScrollY=y);
        LinearLayout actions=row();Button back=button("Back");action(actions,back);back.setOnClickListener(v->{beigeResults=false;build();});root.addView(actions);addToolNavigation();trackBottomRows();
    }
    private void trackBottomRows(){bottomRows.clear();for(int i=Math.max(0,root.getChildCount()-3);i<root.getChildCount();i++)bottomRows.add(root.getChildAt(i));onKeyboardVisibilityChanged(keyboardOpen);}
    @Override void onKeyboardVisibilityChanged(boolean visible){keyboardOpen=visible;if(bottomRows!=null)for(View row:bottomRows)row.setVisibility(visible?View.GONE:View.VISIBLE);}
    private void refreshEditor(){synchronized(store){NotesModel.Page p=store.model.pages.get(editing);if(p==null)return;if(documentTitle!=null&&!documentTitle.hasFocus()&&!documentTitle.getText().toString().equals(p.title)){refreshingTitle=true;documentTitle.setText(p.title);documentTitle.setEnabled(!store.readOnly);refreshingTitle=false;}if(editor!=null)editor.remote(p.body,!store.readOnly&&!p.category);}}
    private void back(){if(!editing.isEmpty()){hideKeyboard();editing="";if(returnToBeige){beigeResults=true;returnToBeige=false;}build();sync(false);}else if(beigeResults){beigeResults=false;build();}else if(arranging){arranging=false;build();}else{getSharedPreferences("tool-navigation",0).edit().putBoolean("notes-open",false).apply();finish();}}
    @Override protected void onResume(){super.onResume();notesResumed=true;if(editing.isEmpty()){if(arranging&&store.readOnly)arranging=false;build();}else{if(documentTitle!=null)documentTitle.setEnabled(!store.readOnly);refreshEditor();}updateSyncStatus();handler.removeCallbacks(syncIndicator);handler.post(syncIndicator);}
    @Override protected void onPause(){notesResumed=false;if(editor!=null)editor.flush(()->{if(leavingTools)NotesSyncJob.schedule(this);});getSharedPreferences("notes-location",0).edit().putString("editing",editing).putInt("scroll",listScrollY).putBoolean("beige",beigeResults).putBoolean("returnBeige",returnToBeige).putInt("beigeScroll",beigeScrollY).apply();handler.removeCallbacks(syncIndicator);super.onPause();}
    @Override public void startActivity(Intent intent){leavingTools=intent.hasExtra("navigateTab")&&intent.getIntExtra("navigateTab",3)!=3;if(intent.getBooleanExtra("toolsHome",false))getSharedPreferences("tool-navigation",0).edit().putBoolean("notes-open",false).apply();super.startActivity(intent);}
    @Override public void finish(){super.finish();}
    // API 33+ gestures use the OnBackInvokedCallback registered in onCreate.
    @SuppressWarnings("deprecation") @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed(){back();}
    @Override protected void onSaveInstanceState(Bundle out){out.putString("editing",editing);out.putBoolean("arranging",arranging);out.putInt("listScrollY",listScrollY);out.putStringArrayList("expanded",new ArrayList<>(expanded));super.onSaveInstanceState(out);}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(emojiMenu!=null)emojiMenu.dismiss();if(dialog!=null)dialog.dismiss();if(editor!=null){root.removeView(editor);editor.destroy();editor=null;}super.onDestroy();}
}






