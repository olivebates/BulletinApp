package com.bulletin.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.Button;

/** Draw the full border inside the button's bounds, including the bottom edge. */
@android.annotation.SuppressLint("ViewConstructor") // Programmatic buttons receive their border color explicitly.
final class BorderedButton extends Button {
    private final Paint border = new Paint();
    BorderedButton(Context context, int color) {
        super(context); border.setColor(color); border.setStyle(Paint.Style.STROKE); border.setStrokeWidth(2);
    }
    void setBorderColor(int color) { border.setColor(color); invalidate(); }
    @Override public void draw(Canvas canvas) {
        super.draw(canvas);
        canvas.drawRect(1, 1, getWidth() - 1, getHeight() - 1, border);
    }
}
