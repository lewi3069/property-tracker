package com.fieldview.tv;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.List;

/** Native view placement: grid, one large feed, two large feeds, or fullscreen. */
public final class CameraWall extends ViewGroup {
    private int featured;
    private boolean fullscreen;
    private final List<Rect> frames = new ArrayList<>();
    private final List<View> ordered = new ArrayList<>();
    public CameraWall(Context context) { super(context); setClipChildren(true); }
    public void setFeaturedCount(int count) { featured = count; requestLayout(); }
    public void setFullscreen(boolean value) { fullscreen = value; requestLayout(); }
    public void setOrder(List<View> views) { ordered.clear(); ordered.addAll(views); requestLayout(); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(width, height);
        calculate(width, height, getChildCount());
        for (int i = 0; i < getChildCount(); i++) {
            Rect frame = frames.get(i);
            ordered.get(i).measure(MeasureSpec.makeMeasureSpec(frame.width(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(frame.height(), MeasureSpec.EXACTLY));
        }
    }
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        for (int i = 0; i < getChildCount(); i++) { Rect frame = frames.get(i); ordered.get(i).layout(frame.left, frame.top, frame.right, frame.bottom); }
    }
    private void add(double left, double top, double right, double bottom, int width, int height) {
        int gap = fullscreen ? 0 : Math.max(2, Math.round(getResources().getDisplayMetrics().density * 2));
        frames.add(new Rect((int)(left * width) + gap, (int)(top * height) + gap,
                Math.max((int)(left * width) + gap + 1, (int)(right * width) - gap),
                Math.max((int)(top * height) + gap + 1, (int)(bottom * height) - gap)));
    }
    private void calculate(int width, int height, int count) {
        frames.clear();
        if (count == 0) return;
        if (count == 1) { add(0, 0, 1, 1, width, height); return; }
        if (count == 13 && (BuildConfig.TV_INTERFACE || width >= height)) {
            add(.25, .25, .75, .75, width, height);
            for (int row = 0; row < 4; row++) for (int column = 0; column < 4; column++) {
                if (row >= 1 && row <= 2 && column >= 1 && column <= 2) continue;
                add(column / 4.0, row / 4.0, (column + 1) / 4.0, (row + 1) / 4.0, width, height);
            }
            return;
        }
        if (!BuildConfig.TV_INTERFACE && height > width) {
            if (featured == 1 && count > 2) {
                add(0, 0, 1, .5, width, height);
                int columns = count > 5 ? 3 : 2, rows = (int)Math.ceil((count - 1.0) / columns);
                for (int i = 0; i < count - 1; i++) add((double)(i % columns) / columns, .5 + .5 * (i / columns) / rows,
                        (double)(i % columns + 1) / columns, .5 + .5 * (i / columns + 1) / rows, width, height);
            } else {
                int columns = count <= 2 ? 1 : count <= 6 ? 2 : 3, rows = (int)Math.ceil((double)count / columns);
                for (int i = 0; i < count; i++) add((double)(i % columns) / columns, (double)(i / columns) / rows,
                        (double)(i % columns + 1) / columns, (double)(i / columns + 1) / rows, width, height);
            }
            return;
        }
        if (BuildConfig.TV_INTERFACE && count == 8 && featured == 1) {
            // A 3x3 main tile and seven 1x1 tiles in a 4x4 grid: all retain 16:9 on a TV.
            add(0, 0, .75, .75, width, height);
            for (int row = 0; row < 4; row++) add(.75, row / 4.0, 1, (row + 1) / 4.0, width, height);
            for (int column = 0; column < 3; column++) add(column / 4.0, .75, (column + 1) / 4.0, 1, width, height);
            return;
        }
        if (featured == 1 && count <= 4) {
            add(0, 0, .72, 1, width, height);
            for (int i = 0; i < count - 1; i++) add(.72, (double)i / (count - 1), 1, (double)(i + 1) / (count - 1), width, height);
        } else if (featured == 1) {
            int columns = count <= 6 ? 3 : 4;
            int rows = count > 9 ? 4 : 3;
            add(0, 0, 2.0 / columns, 2.0 / rows, width, height);
            for (int row = 0; row < rows && frames.size() < count; row++) for (int column = 0; column < columns && frames.size() < count; column++) {
                if (row < 2 && column < 2) continue;
                add((double)column / columns, (double)row / rows, (double)(column + 1) / columns, (double)(row + 1) / rows, width, height);
            }
        } else if (featured >= 2 && count > 2) {
            double split = count <= 4 ? .7 : .6;
            add(0, 0, .5, split, width, height); add(.5, 0, 1, split, width, height);
            int columns = Math.min(4, count - 2), rows = (int)Math.ceil((double)(count - 2) / columns);
            for (int i = 0; i < count - 2; i++) add((double)(i % columns) / columns, split + (1 - split) * (i / columns) / rows,
                    (double)(i % columns + 1) / columns, split + (1 - split) * (i / columns + 1) / rows, width, height);
        } else {
            int columns = (int)Math.ceil(Math.sqrt(count)), rows = (int)Math.ceil((double)count / columns);
            for (int i = 0; i < count; i++) add((double)(i % columns) / columns, (double)(i / columns) / rows,
                    (double)(i % columns + 1) / columns, (double)(i / columns + 1) / rows, width, height);
        }
    }
}
