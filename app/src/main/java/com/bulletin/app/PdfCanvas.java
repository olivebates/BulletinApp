package com.bulletin.app;
import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.os.*;
import android.util.LruCache;
import android.view.*;
import android.widget.OverScroller;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

@android.annotation.SuppressLint("ViewConstructor") // Created programmatically with a PDF file and saved position.
final class PdfCanvas extends View implements AutoCloseable {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(24*1024*1024){protected int sizeOf(String key,Bitmap value){return value.getAllocationByteCount();}};
    private final Set<String> pending=new HashSet<>(),failed=new HashSet<>();
    private final RectF destination=new RectF();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final ScaleGestureDetector scale;
    private final GestureDetector gestures;
    private final OverScroller fling;
    private PdfRenderer renderer;private ParcelFileDescriptor descriptor;private PdfGeometry geometry;
    private final Runnable upward,downward,positionChanged;
    private boolean closed,loaded,restorePending=true;
    private float zoom=1;private double y,x,anchorAllowance;
    private int restorePage;private double restoreFraction,restoreGap,restoreXFraction;private final int gap;
    private String error;
    PdfCanvas(Context context,File file,Bundle state,Runnable upward,Runnable downward,Runnable positionChanged){super(context);this.upward=upward;this.downward=downward;this.positionChanged=positionChanged;gap=Math.round(12*getResources().getDisplayMetrics().density);setBackgroundColor(Color.DKGRAY);fling=new OverScroller(context);
        if(state!=null){restorePage=state.getInt("page");restoreFraction=state.getDouble("fraction");zoom=Math.max(1,Math.min(5,state.getFloat("zoom",1)));x=state.getDouble("x");restoreXFraction=state.getDouble("xFraction",-1);restoreGap=state.getDouble("gap");}
        scale=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector detector){if(!loaded)return false;fling.abortAnimation();float next=Math.max(1,Math.min(5,zoom*detector.getScaleFactor()));double factor=next/zoom;y=(y+detector.getFocusY())*factor-detector.getFocusY();x=(x+detector.getFocusX())*factor-detector.getFocusX();zoom=next;clamp();invalidate();positionChanged.run();return true;}});
        gestures=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){@Override public boolean onDown(MotionEvent e){fling.abortAnimation();return true;}@Override public boolean onScroll(MotionEvent a,MotionEvent b,float dx,float dy){if(scale.isInProgress())return true;move(x+dx,y+dy);return true;}@Override public boolean onFling(MotionEvent a,MotionEvent b,float vx,float vy){if(!loaded)return true;fling.fling((int)x,(int)y,(int)-vx,(int)-vy,0,(int)Math.max(0,getWidth()*zoom-getWidth()),0,(int)Math.min(Integer.MAX_VALUE,maxY()));postInvalidateOnAnimation();return true;}@Override public boolean onDoubleTap(MotionEvent e){if(!loaded)return true;float next=zoom>1?1:2;double factor=next/zoom;y=(y+e.getY())*factor-e.getY();x=(x+e.getX())*factor-e.getX();zoom=next;clamp();invalidate();positionChanged.run();return true;}});
        setContentDescription("PDF document. Pinch to zoom, drag to scroll. Double tap to zoom.");
        worker.execute(()->{try{descriptor=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);renderer=new PdfRenderer(descriptor);float[] ratios=new float[renderer.getPageCount()];if(ratios.length==0)throw new IOException();for(int i=0;i<ratios.length;i++)try(PdfRenderer.Page page=renderer.openPage(i)){ratios[i]=page.getHeight()/(float)Math.max(1,page.getWidth());}post(()->{if(closed)return;geometry=new PdfGeometry(ratios);loaded=true;restorePage=Math.max(0,Math.min(restorePage,ratios.length-1));if(getWidth()>0)restoreAnchor();invalidate();});}catch(Exception e){post(()->{error="This PDF is damaged, encrypted, missing, or unsupported.";invalidate();});}});
    }
    private void restoreAnchor(){y=geometry.position(restorePage,restoreFraction,getWidth()*zoom,gap)+restoreGap*gap;if(restoreXFraction>=0)x=restoreXFraction*getWidth()*zoom;anchorAllowance=y;restorePending=false;clamp();}
    private double maxY(){if(geometry==null)return 0;double total=geometry.total(getWidth()*zoom,gap);return Math.min(Math.max(0,total-1),Math.max(anchorAllowance,Math.max(0,total-getHeight())));}
    private void clamp(){x=Math.max(0,Math.min(Math.max(0,getWidth()*zoom-getWidth()),x));y=Math.max(0,Math.min(maxY(),y));}
    private void move(double nx,double ny){double before=y;x=nx;y=ny;clamp();if(y<before-1)upward.run();else if(y>before+1)downward.run();invalidate();positionChanged.run();}
    @Override public void computeScroll(){if(fling.computeScrollOffset()){move(fling.getCurrX(),fling.getCurrY());postInvalidateOnAnimation();}}
    @Override public boolean onTouchEvent(MotionEvent event){scale.onTouchEvent(event);gestures.onTouchEvent(event);if(event.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);if(!loaded)return;if(restorePending){restoreAnchor();return;}if(oldw<=0)return;int page=geometry.page(y,oldw*zoom,gap);double fraction=geometry.fraction(page,y,oldw*zoom,gap),inGap=geometry.gapFraction(page,y,oldw*zoom,gap);x=x*w/oldw;y=geometry.position(page,fraction,w*zoom,gap)+inGap*gap;anchorAllowance=y;fling.abortAnimation();clamp();positionChanged.run();}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(!loaded){paint.setColor(Color.WHITE);paint.setTextSize(16*getResources().getDisplayMetrics().scaledDensity);canvas.drawText(error==null?"Opening PDF…":error,20,getHeight()/2f,paint);return;}double width=getWidth()*zoom;int first=geometry.page(y,width,gap);double top=geometry.top(first,width,gap);for(int page=first;page<geometry.ratios.length&&top-y<getHeight();page++){float height=(float)(geometry.ratios[page]*width);destination.set((float)-x,(float)(top-y),(float)(width-x),(float)(top-y+height));paint.setColor(Color.WHITE);canvas.drawRect(destination,paint);int target=(int)Math.min(2400,Math.min(width,Math.sqrt(3000000/geometry.ratios[page])));target=Math.max(1,target);String key=page+":"+target;Bitmap bitmap=cache.get(key);if(bitmap!=null)canvas.drawBitmap(bitmap,null,destination,paint);else renderPage(page,target,key);top+=height+gap;}}
    private void renderPage(int index,int width,String key){if(closed||failed.contains(key)||!pending.add(key))return;worker.execute(()->{Bitmap bitmap=null;try{if(closed||renderer==null)return;try(PdfRenderer.Page page=renderer.openPage(index)){int height=Math.max(1,Math.round(width*page.getHeight()/(float)page.getWidth()));bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.WHITE);Matrix matrix=new Matrix();matrix.setScale(width/(float)page.getWidth(),height/(float)page.getHeight());page.render(bitmap,null,matrix,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);}}catch(Exception|OutOfMemoryError e){if(bitmap!=null)bitmap.recycle();bitmap=null;}finally{final Bitmap result=bitmap;post(()->{pending.remove(key);if(closed){if(result!=null)result.recycle();return;}if(result!=null)cache.put(key,result);else failed.add(key);invalidate();});}});}
    void stopMotion(){fling.abortAnimation();}
    Bundle snapshot(){Bundle state=new Bundle();if(loaded&&!restorePending&&getWidth()>0){int page=geometry.page(y,getWidth()*zoom,gap);state.putInt("page",page);state.putDouble("fraction",geometry.fraction(page,y,getWidth()*zoom,gap));state.putDouble("gap",geometry.gapFraction(page,y,getWidth()*zoom,gap));state.putDouble("xFraction",x/(getWidth()*zoom));}else{state.putInt("page",restorePage);state.putDouble("fraction",restoreFraction);state.putDouble("gap",restoreGap);state.putDouble("xFraction",restoreXFraction);}state.putFloat("zoom",zoom);state.putDouble("x",x);return state;}
    @Override public void close(){closed=true;fling.abortAnimation();cache.evictAll();worker.execute(()->{if(renderer!=null)renderer.close();if(descriptor!=null)try{descriptor.close();}catch(IOException ignored){}});worker.shutdown();}
}