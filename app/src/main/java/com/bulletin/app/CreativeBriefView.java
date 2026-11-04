package com.bulletin.app;

import android.content.*;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

/** Category rows in edit mode and an indented drawing brief in generation mode. */
@android.annotation.SuppressLint("ViewConstructor")
final class CreativeBriefView extends LinearLayout {
    interface Picker { void show(Consumer<String> selected); }
    private final Ui ui;
    private final CreativeBrief model;
    private final BriefDictionary dictionary;
    private final Runnable changed;
    private final Picker picker;
    private final ScrollView scroll;
    private final LinearLayout list;
    private final Button add,generate,newBrief,eye;
    private final LinearLayout eyeBar;
    private boolean restoring,restorePending,frozen,movePending;
    CreativeBriefView(Context context,Ui ui,CreativeBrief model,BriefDictionary dictionary,Runnable changed,Runnable back,Picker picker,Consumer<Boolean> titleChanged) {
        super(context);this.ui=ui;this.model=model;this.dictionary=dictionary;this.changed=changed;this.picker=picker;setOrientation(VERTICAL);
        eyeBar=new LinearLayout(context);eyeBar.setGravity(Gravity.END);
        eye=button("",ui.border);eye.setPadding(0,0,0,0);eye.setOnClickListener(v->{captureState();model.showTitles=!model.showTitles;titleChanged.accept(model.showTitles);rebuild();});
        LayoutParams eyeParams=new LayoutParams(ui.dp(48),ui.dp(48));eyeParams.setMargins(ui.dp(8),ui.dp(8),ui.dp(8),ui.dp(8));eyeBar.addView(eye,eyeParams);addView(eyeBar,new LayoutParams(-1,-2));
        scroll=new ScrollView(context);scroll.setFillViewport(true);list=new LinearLayout(context);list.setOrientation(VERTICAL);list.setPadding(ui.dp(8),ui.dp(8),ui.dp(8),ui.dp(8));
        scroll.addView(list);addView(scroll,new LayoutParams(-1,0,1));
        newBrief=button("New Brief",ui.border);newBrief.setOnClickListener(v->{captureState();model.generate(dictionary);rebuild();});
        addView(newBrief,margins(-1));
        LinearLayout actions=new LinearLayout(context);
        Button returnButton=button("Back",ui.border);returnButton.setOnClickListener(v->{freezeState();back.run();});
        add=button("Add",ui.border);add.setContentDescription("Add brief field");add.setOnClickListener(v->addField());
        generate=button("Generate",ui.border);generate.setOnClickListener(v->{captureState();if(model.generated)model.generated=false;else model.generate(dictionary);rebuild();});
        actions.addView(returnButton,margins(0));actions.addView(add,margins(0));actions.addView(generate,margins(0));addView(actions,new LayoutParams(-1,-2));
        list.setOnDragListener(this::drag);scroll.setOnDragListener(this::drag);rebuild();
    }
    private LayoutParams margins(int width){LayoutParams p=new LayoutParams(width,ui.dp(48),width==0?1:0);p.setMargins(ui.dp(8),ui.dp(8),ui.dp(8),ui.dp(8));return p;}
    private Button button(String title,int color){Button b=new BorderedButton(getContext(),color);b.setText(title);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(ui.text);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(ui.dp(8),0,ui.dp(8),0);b.setStateListAnimator(null);b.setBackground(ui.button(color));return b;}
    private LayoutParams rowParams(){LayoutParams p=new LayoutParams(-1,ui.dp(48));p.topMargin=ui.dp(4);p.bottomMargin=ui.dp(4);return p;}
    private void addField(){captureState();picker.show(category->{model.fields.add(category);model.editScroll=Integer.MAX_VALUE;changed.run();rebuild();});}
    private void rebuild(){
        restoring=true;restorePending=true;list.removeAllViews();newBrief.setVisibility(model.generated?VISIBLE:GONE);add.setVisibility(model.generated?GONE:VISIBLE);
        eyeBar.setVisibility(model.generated?VISIBLE:GONE);eye.setSelected(model.showTitles);eye.setContentDescription(model.showTitles?"Hide brief titles":"Show brief titles");
        Ui.Icon eyeIcon=new Ui.Icon(model.showTitles?"eye-open":"eye-closed",ui.border);eyeIcon.setBounds(0,0,ui.dp(24),ui.dp(24));eye.setForeground(new android.graphics.drawable.InsetDrawable(eyeIcon,ui.dp(12)));
        generate.setSelected(model.generated);generate.setBackground(ui.button(model.generated?Ui.GOLD:ui.border));((BorderedButton)generate).setBorderColor(model.generated?Ui.GOLD:ui.border);
        if(model.generated){
            for(int i=0;i<model.fields.size();i++){
                LinearLayout entry=new LinearLayout(getContext());entry.setOrientation(VERTICAL);entry.setPadding(ui.dp(8),ui.dp(8),ui.dp(8),ui.dp(8));
                TextView name=new TextView(getContext());name.setText(model.fields.get(i));name.setTextSize(16);name.setTextColor(ui.text);name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);name.setVisibility(model.showTitles?VISIBLE:GONE);entry.addView(name);
                TextView value=new TextView(getContext());value.setText(i<model.words.size()?model.words.get(i):"");value.setTextSize(16);value.setTextColor(ui.text);value.setTypeface(Typeface.DEFAULT,Typeface.NORMAL);value.setPadding(ui.dp(16),ui.dp(4),0,0);entry.addView(value);list.addView(entry,new LayoutParams(-1,-2));
            }
        }else for(int i=0;i<model.fields.size();i++)addRow(i);
        final int position=model.generated?model.briefScroll:model.editScroll;
        scroll.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){scroll.getViewTreeObserver().removeOnPreDrawListener(this);if(restoring){scroll.scrollTo(0,position);restorePending=false;}return true;}});
    }
    private void addRow(int index){
        LinearLayout row=new LinearLayout(getContext());row.setGravity(Gravity.CENTER_VERTICAL);
        Button grip=button("≡",ui.border);grip.setContentDescription("Drag brief field");row.addView(grip,new LayoutParams(ui.dp(40),ui.dp(48)));
        Button field=button(model.fields.get(index),ui.border);field.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);field.setPadding(ui.dp(12),0,ui.dp(12),0);field.setTextSize(16);field.setContentDescription("Brief field: "+model.fields.get(index));
        LayoutParams fieldParams=new LayoutParams(0,ui.dp(48),1);fieldParams.rightMargin=ui.dp(8);row.addView(field,fieldParams);
        field.setOnClickListener(v->{captureState();picker.show(category->{int position=list.indexOfChild(row);if(position>=0&&position<model.fields.size()){model.fields.set(position,category);changed.run();rebuild();}});});
        Button remove=button("−",Ui.RED);remove.setContentDescription("Remove brief field");row.addView(remove,new LayoutParams(ui.dp(48),ui.dp(48)));
        remove.setOnClickListener(v->{int position=list.indexOfChild(row);if(position>=0&&position<model.fields.size()){captureState();model.fields.remove(position);list.removeView(row);changed.run();}});
        View.OnLongClickListener begin=v->{boolean started=row.startDragAndDrop(ClipData.newPlainText("brief-field",""),new View.DragShadowBuilder(row),row,0);if(started)row.setVisibility(INVISIBLE);return started;};
        grip.setOnLongClickListener(begin);grip.setOnClickListener(v->begin.onLongClick(v));grip.setOnTouchListener((v,event)->{if(event.getAction()==android.view.MotionEvent.ACTION_DOWN){v.performClick();return true;}return event.getAction()!=android.view.MotionEvent.ACTION_CANCEL;});list.addView(row,rowParams());
    }
    private boolean drag(View target,DragEvent event){
        if(model.generated||!(event.getLocalState() instanceof LinearLayout))return false;LinearLayout moving=(LinearLayout)event.getLocalState();if(moving.getParent()!=list)return false;
        switch(event.getAction()){
            case DragEvent.ACTION_DRAG_STARTED:return true;
            case DragEvent.ACTION_DRAG_LOCATION:
                int[] origin=new int[2],targetOrigin=new int[2],scrollOrigin=new int[2];list.getLocationOnScreen(origin);target.getLocationOnScreen(targetOrigin);scroll.getLocationOnScreen(scrollOrigin);
                float screenY=targetOrigin[1]+event.getY(),y=screenY-origin[1];int from=list.indexOfChild(moving),to=from;
                for(int i=0;i<model.fields.size();i++){View other=list.getChildAt(i);if(i<from&&y<other.getTop()+other.getHeight()/2f){to=i;break;}if(i>from&&y>other.getTop()+other.getHeight()/2f)to=i;}
                if(to!=from&&!movePending){movePending=true;final int destination=to;list.post(()->{movePending=false;int current=list.indexOfChild(moving);if(current<0||destination>=model.fields.size()||current==destination)return;if(Feedback.motion(getContext()))android.transition.TransitionManager.beginDelayedTransition(list,new android.transition.ChangeBounds().setDuration(160));String value=model.fields.remove(current);model.fields.add(destination,value);list.removeView(moving);list.addView(moving,destination);changed.run();});}
                float local=screenY-scrollOrigin[1];if(local<ui.dp(64))scroll.scrollBy(0,-ui.dp(10));else if(local>scroll.getHeight()-ui.dp(64))scroll.scrollBy(0,ui.dp(10));return true;
            case DragEvent.ACTION_DROP:return true;
            case DragEvent.ACTION_DRAG_ENDED:moving.setVisibility(VISIBLE);return true;
            default:return true;
        }
    }
    void captureState(){if(frozen||restorePending)return;if(model.generated)model.briefScroll=scroll.getScrollY();else model.editScroll=scroll.getScrollY();}
    void freezeState(){captureState();frozen=true;}
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event){if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN)restoring=false;return super.dispatchTouchEvent(event);}
}