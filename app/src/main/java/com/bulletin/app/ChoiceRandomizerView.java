package com.bulletin.app;

import android.content.ClipData;
import android.content.Context;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.*;

/** A scrollable editor or two-choice board above a fixed toggle. */
@android.annotation.SuppressLint("ViewConstructor")
final class ChoiceRandomizerView extends LinearLayout {
    private final Ui ui;
    private final ChoiceRandomizer model;
    private final Runnable changed;
    private final ScrollView scroll;
    private final LinearLayout list;
    private final Button toggle;
    private boolean flashing, movePending, stateFrozen, restoringPosition, keyboardRestorePending, restorePending;
    private long flashEnds;
    private final Runnable finishChoice = () -> { if(flashing) { flashing=false; rebuild(); } };
    ChoiceRandomizerView(Context context,Ui ui,ChoiceRandomizer model,Runnable changed,Runnable back) {
        super(context);this.ui=ui;this.model=model;this.changed=changed;
        setOrientation(VERTICAL); setFocusableInTouchMode(true); requestFocus();
        scroll=new ScrollView(context);scroll.setFillViewport(true);
        scroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> { if(restoringPosition) scroll.post(() -> { if(restoringPosition) scroll.scrollTo(0,model.editor.scrollY); }); });
        list=new LinearLayout(context);list.setOrientation(VERTICAL);list.setPadding(ui.dp(8),ui.dp(8),ui.dp(8),ui.dp(8));
        scroll.addView(list);addView(scroll,new LayoutParams(-1,0,1));
        toggle=button("Randomize",ui.border);toggle.setContentDescription("Randomize");
        toggle.setOnClickListener(v->{if(flashing)return;if(!model.enabled)captureState();hideKeyboard();model.editor.keyboardVisible=false;if(model.enabled)model.stop();else model.start();rebuild();});
        LinearLayout actions=new LinearLayout(context);
        LayoutParams randomizeParams=new LayoutParams(0,ui.dp(48),1);randomizeParams.setMargins(ui.dp(8),ui.dp(8),ui.dp(8),ui.dp(8));
        Button returnButton=button("Back",ui.border);returnButton.setOnClickListener(v->{freezeState();hideKeyboard();back.run();});
        LayoutParams backParams=new LayoutParams(0,ui.dp(48),1);backParams.setMargins(ui.dp(8),ui.dp(8),ui.dp(8),ui.dp(8));
        actions.addView(returnButton,backParams);actions.addView(toggle,randomizeParams);addView(actions,new LayoutParams(-1,-2));
        list.setOnDragListener(this::drag);scroll.setOnDragListener(this::drag);
        rebuild();
    }
    private Button button(String title,int color) {
        Button b=new BorderedButton(getContext(),color);b.setText(title);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(ui.text);
        b.setBackground(ui.button(color));b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(ui.dp(8),0,ui.dp(8),0);return b;
    }
    private LayoutParams rowParams() { LayoutParams p=new LayoutParams(-1,ui.dp(48));p.bottomMargin=ui.dp(8);return p; }
    private void rebuild() {
        restoringPosition=false; keyboardRestorePending=false;restorePending=false;list.removeAllViews();toggle.setSelected(model.enabled);toggle.setTextColor(ui.text);
        toggle.setBackground(ui.button(model.enabled?Ui.GOLD:ui.border));
        ((BorderedButton)toggle).setBorderColor(model.enabled?Ui.GOLD:ui.border);
        if(model.enabled) {
            for(int index:new ArrayList<>(model.options)) {
                Button option=button(model.items.get(index),ui.border);option.setContentDescription(model.items.get(index));
                option.setOnClickListener(v->{
                    if(flashing)return;flashing=true;flashEnds=android.os.SystemClock.uptimeMillis()+350;
                    model.choose(index);option.setTextColor(ui.background);option.setBackgroundColor(Ui.GREEN);
                    postDelayed(finishChoice,350);
                });list.addView(option,rowParams());
            }
        } else {
            for(String value:model.items)addEditorRow(value);
            Button add=button("+",ui.border);add.setContentDescription("Add choice");
            add.setOnClickListener(v->{model.items.add("");changed.run();list.removeView(add);EditText field=addEditorRow("");list.addView(add,rowParams());field.requestFocus();scroll.post(()->{scroll.smoothScrollTo(0,list.getBottom());((InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(field,InputMethodManager.SHOW_IMPLICIT);});});
            list.addView(add,rowParams());
            restoreEditor();
        }
    }
    private EditText addEditorRow(String value) {
        LinearLayout row=new LinearLayout(getContext());row.setGravity(Gravity.CENTER_VERTICAL);
        Button grip=button("≡",ui.border);grip.setContentDescription("Drag choice");row.addView(grip,new LayoutParams(ui.dp(40),ui.dp(48)));
        EditText field=new EditText(getContext());field.setTextColor(ui.text);field.setTextSize(16);field.setSingleLine(true);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        field.setPadding(ui.dp(12),0,ui.dp(12),0);field.setMinimumHeight(ui.dp(48));field.setHintTextColor(ui.muted);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});field.setBackground(ui.panel(ui.border));field.setText(value);field.setContentDescription("Choice");
        LayoutParams fieldParams=new LayoutParams(0,ui.dp(48),1);fieldParams.rightMargin=ui.dp(8);row.addView(field,fieldParams);
        Button remove=button("−",Ui.RED);remove.setContentDescription("Remove choice");row.addView(remove,new LayoutParams(ui.dp(48),ui.dp(48)));
        remove.setOnClickListener(v->{int index=list.indexOfChild(row);if(index>=0&&index<model.items.size()){model.items.remove(index);list.removeView(row);changed.run();}});
        field.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){int index=list.indexOfChild(row);if(index>=0&&index<model.items.size()){model.items.set(index,s.toString());changed.run();}}public void afterTextChanged(Editable e){}});
        View.OnLongClickListener begin=v->{hideKeyboard();field.clearFocus();boolean started=row.startDragAndDrop(ClipData.newPlainText("choice",""),new View.DragShadowBuilder(row),row,0);if(started)row.setVisibility(INVISIBLE);return started;};
        grip.setOnLongClickListener(begin);
        grip.setOnClickListener(v -> begin.onLongClick(v));
        grip.setOnTouchListener((v,event)->{if(event.getAction()==android.view.MotionEvent.ACTION_DOWN){v.performClick();return true;}return event.getAction()!=android.view.MotionEvent.ACTION_CANCEL;});
        LayoutParams editorParams=new LayoutParams(-1,ui.dp(48));editorParams.topMargin=ui.dp(4);editorParams.bottomMargin=ui.dp(4);
        list.addView(row,editorParams);return field;
    }
    private boolean drag(View target,DragEvent event) {
        if(model.enabled || !(event.getLocalState() instanceof LinearLayout))return false;
        LinearLayout moving=(LinearLayout)event.getLocalState();if(moving.getParent()!=list)return false;
        switch(event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:return true;
            case DragEvent.ACTION_DRAG_LOCATION:
                int[] origin=new int[2],targetOrigin=new int[2],scrollOrigin=new int[2];
                list.getLocationOnScreen(origin);target.getLocationOnScreen(targetOrigin);scroll.getLocationOnScreen(scrollOrigin);
                float screenY=targetOrigin[1]+event.getY(),y=screenY-origin[1];
                int from=list.indexOfChild(moving),to=from;
                for(int i=0;i<model.items.size();i++){View other=list.getChildAt(i);if(i<from&&y<other.getTop()+other.getHeight()/2f){to=i;break;}if(i>from&&y>other.getTop()+other.getHeight()/2f)to=i;}
                if(to!=from&&!movePending){movePending=true;final int destination=to;list.post(()->{movePending=false;int current=list.indexOfChild(moving);if(current<0||destination>=model.items.size()||current==destination)return;
                    if(Feedback.motion(getContext()))android.transition.TransitionManager.beginDelayedTransition(list,new android.transition.ChangeBounds().setDuration(160));
                    String text=model.items.remove(current);model.items.add(destination,text);list.removeView(moving);list.addView(moving,destination);changed.run();});}
                float local=screenY-scrollOrigin[1];if(local<ui.dp(64))scroll.scrollBy(0,-ui.dp(10));else if(local>scroll.getHeight()-ui.dp(64))scroll.scrollBy(0,ui.dp(10));return true;
            case DragEvent.ACTION_DROP:return true;
            case DragEvent.ACTION_DRAG_ENDED:moving.setVisibility(VISIBLE);return true;
            default:return true;
        }
    }
    void captureState() {
        if(stateFrozen || model.enabled || restorePending) return;
        model.editor.scrollY=scroll.getScrollY(); model.editor.activeIndex=-1; model.editor.keyboardVisible=false;
        View focused=findFocus();
        if(focused instanceof EditText && focused.getParent() instanceof View) {
            int index=list.indexOfChild((View)focused.getParent());
            if(index>=0 && index<model.items.size()) {
                EditText field=(EditText)focused;model.editor.activeIndex=index;
                model.editor.selectionStart=Math.max(0,field.getSelectionStart()); model.editor.selectionEnd=Math.max(0,field.getSelectionEnd());
                WindowInsets insets=getRootWindowInsets();
                if(android.os.Build.VERSION.SDK_INT>=30 && insets!=null) model.editor.keyboardVisible=insets.isVisible(WindowInsets.Type.ime());
                else { android.graphics.Rect visible=new android.graphics.Rect();getWindowVisibleDisplayFrame(visible);model.editor.keyboardVisible=getRootView().getHeight()-visible.bottom>ui.dp(100); }
            }
        }
    }
    void freezeState() { captureState();stateFrozen=true; }
    private void restoreEditor() {
        restoringPosition=true;restorePending=true;keyboardRestorePending=model.editor.keyboardVisible;
        post(() -> {
            if(!isAttachedToWindow() || model.enabled)return;
            int index=model.editor.activeIndex;
            if(index>=0 && index<model.items.size()) {
                EditText field=(EditText)((LinearLayout)list.getChildAt(index)).getChildAt(1);
                field.requestFocus();int length=field.length();
                field.setSelection(Math.min(length,model.editor.selectionStart),Math.min(length,model.editor.selectionEnd));
            } else requestFocus();
            scroll.scrollTo(0,model.editor.scrollY);restorePending=false;restoreKeyboard();
        });
    }
    private void restoreKeyboard() {
        if(!keyboardRestorePending || !hasWindowFocus() || model.enabled)return;
        View focused=findFocus();if(!(focused instanceof EditText))return;
        keyboardRestorePending=false;
        ((InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(focused,InputMethodManager.SHOW_IMPLICIT);
    }
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN) restoringPosition=false;
        return super.dispatchTouchEvent(event);
    }
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused);if(focused)restoreKeyboard(); }
    private void hideKeyboard(){((InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(getWindowToken(),0);clearFocus();}
    @Override protected void onDetachedFromWindow(){removeCallbacks(finishChoice);super.onDetachedFromWindow();}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(flashing)postDelayed(finishChoice,Math.max(0,flashEnds-android.os.SystemClock.uptimeMillis()));}
}