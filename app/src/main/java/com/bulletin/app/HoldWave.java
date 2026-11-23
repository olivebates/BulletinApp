package com.bulletin.app;

import android.view.View;

/** Fit the entire wavy edge within the window, including its outward peaks. */
final class HoldWave {
    static float radius(View source,float x,float y) {
        View root=source.getRootView();
        float margin=12*source.getResources().getDisplayMetrics().density;
        return Math.max(0,(Math.min(root.getWidth(),root.getHeight())/2f-margin)/1.07f);
    }
}
