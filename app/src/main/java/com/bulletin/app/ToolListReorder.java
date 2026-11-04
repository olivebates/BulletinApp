package com.bulletin.app;

import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Named-list dragging with smooth movement, edge scrolling and canceled-drop rollback. */
final class ToolListReorder implements View.OnDragListener {
    private final LinearLayout rows;
    private final ScrollView scroll;
    private final List<Game.ChoiceList> entries;
    private final Runnable changed;
    private final Ui ui;
    private List<Game.ChoiceList> original;
    private boolean active,pending;
    private float speed,remainder;
    private long lastFrame;
    private final Runnable autoScroll=new Runnable(){public void run(){if(!active||speed==0||!scroll.isAttachedToWindow())return;long now=SystemClock.uptimeMillis();float distance=remainder+speed*Math.min(50,now-lastFrame)/1000f;int pixels=(int)distance;remainder=distance-pixels;lastFrame=now;scroll.scrollBy(0,pixels);scroll.postOnAnimation(this);}};
    ToolListReorder(LinearLayout rows,ScrollView scroll,LinearLayout content,List<Game.ChoiceList> entries,Runnable changed,Ui ui){this.rows=rows;this.scroll=scroll;this.entries=entries;this.changed=changed;this.ui=ui;rows.setOnDragListener(this);scroll.setOnDragListener(this);content.setOnDragListener(this);}
    void cancel(){if(active&&original!=null){entries.clear();entries.addAll(original);}active=false;speed=0;scroll.removeCallbacks(autoScroll);}
    @Override public boolean onDrag(View target,DragEvent event){
        if(!(event.getLocalState() instanceof LinearLayout))return false;
        LinearLayout moving=(LinearLayout)event.getLocalState();if(moving.getParent()!=rows||!(moving.getTag() instanceof Game.ChoiceList))return false;
        switch(event.getAction()){
            case DragEvent.ACTION_DRAG_STARTED:if(!active){original=new ArrayList<>(entries);active=true;}return true;
            case DragEvent.ACTION_DRAG_LOCATION:
                if(!active)return true;int[] origin=new int[2],targetOrigin=new int[2],scrollOrigin=new int[2];rows.getLocationOnScreen(origin);target.getLocationOnScreen(targetOrigin);scroll.getLocationOnScreen(scrollOrigin);
                float screenY=targetOrigin[1]+event.getY(),y=screenY-origin[1];int from=rows.indexOfChild(moving),to=from;
                for(int i=0;i<rows.getChildCount();i++){View other=rows.getChildAt(i);if(i<from&&y<other.getTop()+other.getHeight()/2f){to=i;break;}if(i>from&&y>other.getTop()+other.getHeight()/2f)to=i;}
                if(to!=from&&!pending){pending=true;final int destination=to;rows.post(()->{pending=false;if(!active)return;int current=rows.indexOfChild(moving);if(current<0||destination>=entries.size()||current==destination)return;
                    if(Feedback.motion(rows.getContext()))android.transition.TransitionManager.beginDelayedTransition(rows,new android.transition.ChangeBounds().setDuration(160));
                    Game.ChoiceList entry=entries.remove(current);entries.add(destination,entry);rows.removeView(moving);rows.addView(moving,destination);});}
                float density=rows.getResources().getDisplayMetrics().density;float next=DragScroll.speed((screenY-scrollOrigin[1])/density,scroll.getHeight()/density)*density;
                if(speed==0&&next!=0){lastFrame=SystemClock.uptimeMillis();scroll.postOnAnimation(autoScroll);}speed=next;if(speed==0){scroll.removeCallbacks(autoScroll);remainder=0;}return true;
            case DragEvent.ACTION_DROP:return true;
            case DragEvent.ACTION_DRAG_ENDED:
                if(!active)return true;active=false;speed=0;scroll.removeCallbacks(autoScroll);boolean accepted=event.getResult();
                rows.post(()->{moving.setVisibility(View.VISIBLE);if(!accepted&&original!=null){entries.clear();entries.addAll(original);}changed.run();});return true;
            default:return true;
        }
    }
}