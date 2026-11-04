package com.bulletin.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.View;

final class Ui {
    static final int GREEN = Color.rgb(125, 231, 169), RED = Color.rgb(255, 143, 151), GOLD = Color.rgb(244, 209, 126);
    final Context context;
    final int background, border, text, muted;
    Ui(Context context, float hue) {
        this.context = context;
        background = Color.HSVToColor(new float[]{hue, .84f, .20f});
        border = Color.HSVToColor(new float[]{hue, .53f, .77f});
        text = Color.HSVToColor(new float[]{hue, .09f, .96f});
        muted = Color.HSVToColor(new float[]{hue, .24f, .70f});
    }
    int dp(float n) { return Math.round(n * context.getResources().getDisplayMetrics().density); }
    Drawable panel(int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(background);
        shape.setStroke(2, stroke); // Two physical pixels on every side.
        return shape;
    }
    Drawable button(int stroke) {
        return new RippleDrawable(ColorStateList.valueOf((stroke & 0x00ffffff) | 0x30000000), panel(stroke), null);
    }
    Drawable requiredButton() {
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(Color.rgb((Color.red(background) * 4 + Color.red(border)) / 5,
            (Color.green(background) * 4 + Color.green(border)) / 5,
            (Color.blue(background) * 4 + Color.blue(border)) / 5));
        fill.setStroke(2, GOLD);
        GradientDrawable stripe = new GradientDrawable(); stripe.setColor(GOLD);
        android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(new Drawable[]{fill, stripe});
        layers.setLayerWidth(1, dp(4)); layers.setLayerGravity(1, android.view.Gravity.LEFT | android.view.Gravity.FILL_VERTICAL);
        return new RippleDrawable(ColorStateList.valueOf((GOLD & 0x00ffffff) | 0x30000000), layers, null);
    }
    View icon(String name, int color, int size) {
        android.widget.ImageView v = new android.widget.ImageView(context);
        v.setImageDrawable(new Icon(name, color));
        v.setPadding(dp(4), dp(4), dp(4), dp(4));
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        v.setLayoutParams(new android.widget.LinearLayout.LayoutParams(dp(size), dp(size)));
        return v;
    }
    static final class Icon extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String name;
        Icon(String name, int color) { this.name = name; p.setColor(color); p.setStrokeWidth(1.5f); p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); }
        @Override public void draw(Canvas canvas) {
            int save = canvas.save();
            canvas.translate(getBounds().left, getBounds().top);
            canvas.scale(getBounds().width() / 24f, getBounds().height() / 24f);
            switch (name) {
                case "flame":
                    Path flame = new Path(); flame.moveTo(13, 2); flame.cubicTo(13, 9, 22, 10, 19, 17);
                    flame.cubicTo(17, 24, 4, 22, 4, 15); flame.cubicTo(4, 10, 9, 9, 9, 5);
                    flame.lineTo(11, 10); flame.close(); canvas.drawPath(flame, p); break;
                case "bolt":
                    Path bolt = new Path(); bolt.moveTo(14, 2); bolt.lineTo(5, 14); bolt.lineTo(11, 14);
                    bolt.lineTo(9, 22); bolt.lineTo(20, 9); bolt.lineTo(13, 9); bolt.close(); canvas.drawPath(bolt, p); break;
                case "sun":
                    canvas.drawCircle(12, 12, 4, p);
                    for (int i = 0; i < 8; i++) {
                        double angle = i * Math.PI / 4;
                        line(canvas, 12 + (float) Math.cos(angle) * 7, 12 + (float) Math.sin(angle) * 7,
                            12 + (float) Math.cos(angle) * 10, 12 + (float) Math.sin(angle) * 10);
                    } break;
                case "coffee":
                    canvas.drawRect(4, 9, 17, 19, p); canvas.drawArc(13, 10, 23, 17, -90, 180, false, p);
                    line(canvas, 2, 22, 20, 22); line(canvas, 7, 3, 7, 6); line(canvas, 12, 3, 12, 6); break;
                case "leaf":
                    Path leaf = new Path(); leaf.moveTo(3, 21); leaf.cubicTo(0, 7, 12, 3, 21, 3);
                    leaf.cubicTo(21, 17, 15, 23, 3, 21); canvas.drawPath(leaf, p); line(canvas, 3, 21, 16, 8); break;
                case "skull":
                    canvas.drawOval(3, 2, 21, 19, p); canvas.drawCircle(8, 10, 2, p); canvas.drawCircle(16, 10, 2, p);
                    line(canvas, 7, 18, 7, 22); line(canvas, 7, 22, 17, 22); line(canvas, 17, 18, 17, 22);
                    line(canvas, 10, 19, 10, 22); line(canvas, 14, 19, 14, 22); break;
                case "euro":
                    canvas.drawCircle(12, 12, 9, p);
                    canvas.drawArc(7, 6, 17, 18, 60, 240, false, p);
                    line(canvas, 6, 10, 15, 10); line(canvas, 6, 14, 14, 14); break;
                case "calendar":
                    canvas.drawRect(4, 5, 20, 21, p); line(canvas, 4, 10, 20, 10); line(canvas, 8, 3, 8, 7); line(canvas, 16, 3, 16, 7);
                    canvas.drawRect(8, 14, 10, 16, p); line(canvas, 14, 15, 16, 15); break;
                case "coin":
                    canvas.drawCircle(12, 12, 9, p); canvas.drawCircle(12, 12, 6, p); line(canvas, 12, 8, 12, 16); break;
                case "plus": line(canvas, 12, 5, 12, 19); line(canvas, 5, 12, 19, 12); break;
                case "timeline": line(canvas, 3, 12, 21, 12); line(canvas, 3, 5, 3, 19); line(canvas, 21, 5, 21, 19); break;
                case "rearrange":
                    line(canvas, 7, 3, 7, 21); line(canvas, 3, 7, 7, 3); line(canvas, 7, 3, 11, 7);
                    line(canvas, 17, 3, 17, 21); line(canvas, 13, 17, 17, 21); line(canvas, 17, 21, 21, 17); break;
                case "check": line(canvas, 5, 12, 10, 17); line(canvas, 10, 17, 20, 6); break;
                case "arrow": line(canvas, 9, 6, 15, 12); line(canvas, 15, 12, 9, 18); break;
                case "exclamation": line(canvas, 12, 4, 12, 14); line(canvas, 12, 19, 12, 19.2f); break;
                case "trash":
                    line(canvas, 4, 6, 20, 6); line(canvas, 9, 3, 15, 3);
                    line(canvas, 6, 6, 7, 21); line(canvas, 7, 21, 17, 21); line(canvas, 17, 21, 18, 6);
                    line(canvas, 10, 10, 10, 17); line(canvas, 14, 10, 14, 17); break;
                case "edit":
                    Path pencil = new Path(); pencil.moveTo(4, 20); pencil.lineTo(5, 14); pencil.lineTo(16, 3);
                    pencil.lineTo(21, 8); pencil.lineTo(10, 19); pencil.close(); canvas.drawPath(pencil, p);
                    line(canvas, 13, 6, 18, 11); line(canvas, 5, 14, 10, 19); break;
                case "eye-open":
                    canvas.drawArc(3, 6, 21, 18, 180, 180, false, p); canvas.drawArc(3, 6, 21, 18, 0, 180, false, p); canvas.drawCircle(12, 12, 3, p); break;
                case "eye-closed":
                    canvas.drawArc(3, 6, 21, 18, 0, 180, false, p); line(canvas, 5, 16, 3, 19); line(canvas, 9, 18, 8, 21); line(canvas, 15, 18, 16, 21); line(canvas, 19, 16, 21, 19); break;
                case "clock":
                    canvas.drawCircle(12, 12, 9, p); line(canvas, 12, 6, 12, 12); line(canvas, 12, 12, 16, 14); break;
                case "fine":
                    Path triangle = new Path(); triangle.moveTo(12, 3); triangle.lineTo(22, 21); triangle.lineTo(2, 21); triangle.close(); canvas.drawPath(triangle, p);
                    line(canvas, 12, 9, 12, 14); line(canvas, 12, 17, 12, 17.2f); break;
                case "settings":
                    for (int i = 0; i < 3; i++) { float y = 6 + i * 6; line(canvas, 3, y, 21, y); canvas.drawCircle(i == 1 ? 8 : 16, y, 2.5f, p); } break;
                default:
                    canvas.drawRect(5, 5, 19, 22, p); canvas.drawRect(9, 2, 15, 7, p);
                    line(canvas, 9, 12, 16, 12); line(canvas, 9, 16, 16, 16); break;
            }
            canvas.restoreToCount(save);
        }
        private void line(Canvas c, float x1, float y1, float x2, float y2) { c.drawLine(x1, y1, x2, y2, p); }
        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
