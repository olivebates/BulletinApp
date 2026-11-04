package com.bulletin.app;
final class PdfGeometry {
    final float[] ratios;
    PdfGeometry(float[] ratios){this.ratios=ratios;}
    double top(int page,double width,double gap){double y=0;for(int i=0;i<page;i++)y+=ratios[i]*width+gap;return y;}
    double total(double width,double gap){return ratios.length==0?0:top(ratios.length,width,gap)-gap;}
    int page(double y,double width,double gap){double current=0;for(int i=0;i<ratios.length;i++){double next=current+ratios[i]*width+gap;if(y<next)return i;current=next;}return Math.max(0,ratios.length-1);}
    double fraction(int page,double y,double width,double gap){return Math.max(0,Math.min(1,(y-top(page,width,gap))/(ratios[page]*width)));}
    double gapFraction(int page,double y,double width,double gap){return gap<=0?0:Math.max(0,Math.min(1,(y-top(page,width,gap)-ratios[page]*width)/gap));}
    double position(int page,double fraction,double width,double gap){return top(page,width,gap)+Math.max(0,Math.min(1,fraction))*ratios[page]*width;}
}