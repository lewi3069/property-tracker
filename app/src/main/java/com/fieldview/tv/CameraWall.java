package com.fieldview.tv;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.OverScroller;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Native view placement: grid, one large feed, two large feeds, fullscreen, or the phone Browse list. */
public final class CameraWall extends ViewGroup {
    /** Browse rows or their scroll position changed. Called during layout, scrolling or drawing: post view changes. */
    interface ViewportListener { void viewportChanged(boolean scrolled); }
    private int featured;
    private int motionLayoutSlots;
    private double panoramicAspect;
    private double secondaryAspect;
    public void setActiveAspects(double first, double second) {
        panoramicAspect = first > 0 && first < 100 ? first : 0;
        secondaryAspect = second > 0 && second < 100 ? second : 0;
        requestLayout();
    }
    private boolean fullscreen;
    private final List<Rect> frames = new ArrayList<>();
    private final List<View> ordered = new ArrayList<>();
    // Browse scrolls this view's content; other layouts always stay at offset zero.
    private boolean browse, listMeasured;
    private double[] browseAspects = new double[0];
    private int contentHeight, browseOffset, restoreTarget = -1, notifiedHeight = -1;
    private List<Rect> notifiedFrames = new ArrayList<>();
    private ViewportListener viewportListener;
    private final OverScroller scroller;
    private final int touchSlop, minimumFling, maximumFling;
    private VelocityTracker velocity;
    private int activePointer = -1;
    private float downY, lastY;
    private boolean dragging;
    public CameraWall(Context context) {
        super(context); setClipChildren(true);
        ViewConfiguration configuration = ViewConfiguration.get(context);
        touchSlop = configuration.getScaledTouchSlop();
        minimumFling = configuration.getScaledMinimumFlingVelocity(); maximumFling = configuration.getScaledMaximumFlingVelocity();
        scroller = new OverScroller(context);
    }
    public void setFeaturedCount(int count) { featured = count; requestLayout(); }
    public void setMotionLayoutSlots(int count) { motionLayoutSlots = count; requestLayout(); }
    public void setFullscreen(boolean value) { fullscreen = value; requestLayout(); }
    public void setOrder(List<View> views) { ordered.clear(); ordered.addAll(views); requestLayout(); }

    private boolean stacked;
    private double[] stackedAspects = new double[0];
    /** Phone portrait Auto: full-width rows top to bottom, however many feeds are active. */
    void setStacked(boolean value) { if (stacked != value) { stacked = value; requestLayout(); } }
    /** Substream picture aspect per stacked row, aligned with the order; zero or unknown uses 16:9. */
    void setStackedAspects(double[] aspects) {
        if (Arrays.equals(stackedAspects, aspects)) return;
        stackedAspects = aspects.clone(); requestLayout();
    }
    /** Height of the first count stacked rows at their natural full-width heights, gaps included. */
    int stackedNaturalHeight(int width, double[] aspects, int count) {
        int gap = gap(), total = 0;
        for (int row : naturalRows(Math.max(1, width - 2 * gap), aspects, count)) total += row + 2 * gap;
        return total;
    }
    /** A row fills the width at its picture's own shape, so nothing is cropped or stretched. */
    private static int[] naturalRows(int rowWidth, double[] aspects, int count) {
        int[] rows = new int[count];
        for (int i = 0; i < count; i++) {
            double aspect = i < aspects.length && aspects[i] > 0 && aspects[i] < 100 ? aspects[i] : 16.0 / 9.0;
            rows[i] = Math.max(1, (int) Math.round(rowWidth / aspect));
        }
        return rows;
    }
    void setViewportListener(ViewportListener value) { viewportListener = value; }
    /** Phone portrait Browse: full-width rows in the given order. Leaving keeps the offset for the next Browse. */
    void setBrowse(boolean value) {
        if (browse == value) return;
        stopScrolling();
        if (value) { browse = true; restoreTarget = browseOffset; notifiedFrames = new ArrayList<>(); notifiedHeight = -1; }
        else { browseOffset = browseOffset(); browse = false; restoreTarget = -1; super.scrollTo(0, 0); }
        requestLayout();
    }
    /** Picture aspect ratio per row, aligned with the order; zero or unknown uses 16:9. */
    void setBrowseAspects(double[] aspects) {
        if (Arrays.equals(browseAspects, aspects)) return;
        browseAspects = aspects.clone(); requestLayout();
    }
    int browseOffset() { return browse ? (restoreTarget >= 0 ? restoreTarget : getScrollY()) : browseOffset; }
    void restoreBrowseOffset(int value) {
        if (browse) { restoreTarget = Math.max(0, value); requestLayout(); } else browseOffset = Math.max(0, value);
    }
    /** Browse rows in content coordinates, or null until the current list has been measured. */
    List<Rect> browseFrames() {
        if (!browse || !listMeasured || frames.size() != ordered.size() || getHeight() <= 0) return null;
        List<Rect> copy = new ArrayList<>();
        for (Rect frame : frames) copy.add(new Rect(frame));
        return copy;
    }

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
        if (!browse) return;
        // A returning list keeps asking for its saved offset until the user scrolls, so late inset changes cannot lose it.
        applyScroll(restoreTarget >= 0 ? restoreTarget : getScrollY());
        if (getHeight() != notifiedHeight || !frames.equals(notifiedFrames)) {
            notifiedHeight = getHeight(); notifiedFrames = new ArrayList<>(frames);
            if (viewportListener != null) viewportListener.viewportChanged(false);
        }
    }
    private int gap() { return fullscreen ? 0 : Math.max(2, Math.round(getResources().getDisplayMetrics().density * 2)); }
    private void add(double left, double top, double right, double bottom, int width, int height) {
        int gap = gap();
        frames.add(new Rect((int)(left * width) + gap, (int)(top * height) + gap,
                Math.max((int)(left * width) + gap + 1, (int)(right * width) - gap),
                Math.max((int)(top * height) + gap + 1, (int)(bottom * height) - gap)));
    }
    /** Full-width rows, each fitted to its camera's picture shape; a tall picture letterboxes within one screen. */
    private void calculateBrowse(int width, int height, int count) {
        int gap = gap(), rowWidth = Math.max(1, width - 2 * gap), y = 0;
        for (int i = 0; i < count; i++) {
            double aspect = i < browseAspects.length && browseAspects[i] > 0 && browseAspects[i] < 100 ? browseAspects[i] : 16.0 / 9.0;
            int row = (int) Math.round(rowWidth / aspect);
            if (height > 2 * gap) row = Math.min(row, height - 2 * gap);
            row = Math.max(1, row);
            frames.add(new Rect(gap, y + gap, gap + rowWidth, y + gap + row));
            y += row + 2 * gap;
        }
        contentHeight = y;
    }
    private void calculate(int width, int height, int count) {
        frames.clear();
        listMeasured = browse;
        if (browse) { calculateBrowse(width, height, count); return; }
        contentHeight = height;
        if (count == 0) return;
        if (count == 1) { add(0, 0, 1, 1, width, height); return; }
        if (stacked) {
            // Natural heights from the top; if they overflow, every row shrinks by the same factor.
            int gap = gap(), rowWidth = Math.max(1, width - 2 * gap), natural = 0, y = 0;
            int[] rows = naturalRows(rowWidth, stackedAspects, count);
            for (int row : rows) natural += row;
            int available = height - 2 * gap * count;
            if (natural > available && available > 0)
                for (int i = 0; i < count; i++) rows[i] = Math.max(1, (int) ((long) rows[i] * available / natural));
            for (int row : rows) { frames.add(new Rect(gap, y + gap, gap + rowWidth, y + gap + row)); y += row + 2 * gap; }
            return;
        }
        if (BuildConfig.TV_INTERFACE && motionLayoutSlots == 1 && count == 11 && ViewSelector.isFourByThree(panoramicAspect)) {
            // Fit the doorbell's measured picture, with six beside it and four below.
            double side = Math.max(.3, Math.min(.85, panoramicAspect * height * .75 / width));
            add(0, 0, side, .75, width, height);
            for (int row = 0; row < 3; row++) for (int column = 0; column < 2; column++)
                add(side + (1 - side) * column / 2, row / 4.0,
                        side + (1 - side) * (column + 1) / 2, (row + 1) / 4.0, width, height);
            for (int column = 0; column < 4; column++) add(column / 4.0, .75, (column + 1) / 4.0, 1, width, height);
            return;
        }
        if (BuildConfig.TV_INTERFACE && motionLayoutSlots == 1 && count == 5 && panoramicAspect > 2.2) {
            double split = Math.min(.8, Math.max(.5, width / panoramicAspect / height));
            add(0, 0, 1, split, width, height);
            for (int column = 0; column < 4; column++) add(column / 4.0, split, (column + 1) / 4.0, 1, width, height);
            return;
        }
        if (BuildConfig.TV_INTERFACE && motionLayoutSlots == 2 && count == 10) {
            add(0, 0, .5, .5, width, height); add(.5, 0, 1, .5, width, height);
            for (int row = 0; row < 2; row++) for (int column = 0; column < 4; column++)
                add(column / 4.0, .5 + row / 4.0, (column + 1) / 4.0, .5 + (row + 1) / 4.0, width, height);
            return;
        }
        if (BuildConfig.TV_INTERFACE && motionLayoutSlots == 2 && count == 6 && panoramicAspect > 2.2 && secondaryAspect > 2.2) {
            // Fit both panoramic images in a shared column, with four thumbnails beside them.
            double reciprocal = 1 / panoramicAspect + 1 / secondaryAspect;
            double side = Math.max(.5, Math.min(.85, (double) height / width / reciprocal));
            double split = (1 / panoramicAspect) / reciprocal;
            add(0, 0, side, split, width, height); add(0, split, side, 1, width, height);
            for (int row = 0; row < 4; row++) add(side, row / 4.0, 1, (row + 1) / 4.0, width, height);
            return;
        }
        if (BuildConfig.TV_INTERFACE && motionLayoutSlots == 2 && count == 8 && (panoramicAspect > 2.2 || secondaryAspect > 2.2)) {
            boolean firstWide = panoramicAspect > 2.2;
            double wide = firstWide ? panoramicAspect : secondaryAspect;
            double regular = firstWide ? secondaryAspect : panoramicAspect;
            if (!(regular > 0)) regular = 16.0 / 9.0;
            double topWidth = Math.max(.3, Math.min(.85, wide * height / (2 * width)));
            double bottomWidth = Math.max(.3, Math.min(.85, regular * height / (2 * width)));
            // Keep the featured order (and its one-main-decoder assignment) stable.
            if (firstWide) { add(0, 0, topWidth, .5, width, height); add(0, .5, bottomWidth, 1, width, height); }
            else { add(0, .5, bottomWidth, 1, width, height); add(0, 0, topWidth, .5, width, height); }
            for (int row = 0; row < 2; row++) add(topWidth, row / 4.0, 1, (row + 1) / 4.0, width, height);
            for (int row = 0; row < 2; row++) for (int column = 0; column < 2; column++)
                add(bottomWidth + (1 - bottomWidth) * column / 2, .5 + row / 4.0,
                        bottomWidth + (1 - bottomWidth) * (column + 1) / 2, .5 + (row + 1) / 4.0, width, height);
            return;
        }
        if (BuildConfig.TV_INTERFACE && motionLayoutSlots == 4 && count == 4) {
            for (int row = 0; row < 2; row++) for (int column = 0; column < 2; column++)
                add(column / 2.0, row / 2.0, (column + 1) / 2.0, (row + 1) / 2.0, width, height);
            return;
        }
        if (BuildConfig.TV_INTERFACE && motionLayoutSlots == 3 && count == 7) {
            add(0, 0, .5, .5, width, height); add(.5, 0, 1, .5, width, height);
            add(0, .5, .5, 1, width, height);
            for (int row = 0; row < 2; row++) for (int column = 0; column < 2; column++)
                add(.5 + column / 4.0, .5 + row / 4.0, .5 + (column + 1) / 4.0, .5 + (row + 1) / 4.0, width, height);
            return;
        }
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

    // Browse scrolling. Rows keep their content positions; the view offset moves them.
    private int maxScroll() { return browse ? Math.max(0, contentHeight - getHeight()) : 0; }
    private void applyScroll(int y) {
        int target = browse ? Math.max(0, Math.min(maxScroll(), y)) : 0;
        if (target != getScrollY() || getScrollX() != 0) super.scrollTo(0, target);
    }
    @Override public void scrollTo(int x, int y) { restoreTarget = -1; applyScroll(y); }
    @Override protected void onScrollChanged(int left, int top, int oldLeft, int oldTop) {
        super.onScrollChanged(left, top, oldLeft, oldTop);
        if (browse && viewportListener != null) viewportListener.viewportChanged(true);
    }
    @Override public void computeScroll() {
        if (browse && scroller.computeScrollOffset()) { applyScroll(scroller.getCurrY()); if (!scroller.isFinished()) postInvalidateOnAnimation(); }
    }
    @Override protected int computeVerticalScrollRange() { return browse ? Math.max(contentHeight, getHeight()) : super.computeVerticalScrollRange(); }
    @Override protected int computeVerticalScrollOffset() { return browse ? getScrollY() : super.computeVerticalScrollOffset(); }
    @Override protected int computeVerticalScrollExtent() { return browse ? getHeight() : super.computeVerticalScrollExtent(); }
    @Override protected void onDetachedFromWindow() { stopScrolling(); super.onDetachedFromWindow(); }
    private void stopScrolling() {
        scroller.forceFinished(true); dragging = false; activePointer = -1;
        if (velocity != null) { velocity.recycle(); velocity = null; }
    }
    private void track(MotionEvent event) {
        if (velocity == null) velocity = VelocityTracker.obtain();
        velocity.addMovement(event);
    }
    private void touchDown(MotionEvent event) {
        restoreTarget = -1;
        activePointer = event.getPointerId(0); downY = lastY = event.getY();
        if (velocity != null) velocity.clear();
        track(event);
    }
    private void startDragIfNeeded(MotionEvent event) {
        if (dragging || maxScroll() == 0) return;
        int index = event.findPointerIndex(activePointer);
        if (index < 0 || Math.abs(event.getY(index) - downY) <= touchSlop) return;
        dragging = true; lastY = event.getY(index);
        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
    }
    private void secondaryPointerUp(MotionEvent event) {
        int index = event.getActionIndex();
        if (event.getPointerId(index) != activePointer) return;
        int next = index == 0 ? 1 : 0;
        activePointer = event.getPointerId(next); downY = lastY = event.getY(next);
        if (velocity != null) velocity.clear();
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if (!browse) return super.onInterceptTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchDown(event);
                // A touch during a fling stops the list instead of tapping a camera.
                dragging = !scroller.isFinished(); scroller.forceFinished(true); break;
            case MotionEvent.ACTION_MOVE: track(event); startDragIfNeeded(event); break;
            case MotionEvent.ACTION_POINTER_UP: secondaryPointerUp(event); break;
            case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: stopScrolling(); break;
            default: break;
        }
        return dragging;
    }
    // Rows handle taps; the wall itself only scrolls.
    @SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!browse) return super.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                boolean flinging = !scroller.isFinished(); scroller.forceFinished(true);
                touchDown(event); dragging = flinging; return true;
            }
            case MotionEvent.ACTION_MOVE: {
                track(event); startDragIfNeeded(event);
                int index = event.findPointerIndex(activePointer);
                if (dragging && index >= 0) {
                    int delta = (int) (lastY - event.getY(index));
                    if (delta != 0) { lastY -= delta; applyScroll(getScrollY() + delta); }
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                int index = event.getActionIndex();
                activePointer = event.getPointerId(index); downY = lastY = event.getY(index); return true;
            }
            case MotionEvent.ACTION_POINTER_UP: secondaryPointerUp(event); return true;
            case MotionEvent.ACTION_UP:
                if (dragging && velocity != null) {
                    track(event); velocity.computeCurrentVelocity(1000, maximumFling);
                    float speed = velocity.getYVelocity(activePointer);
                    if (Math.abs(speed) > minimumFling) {
                        stopScrolling();
                        scroller.fling(0, getScrollY(), 0, (int) -speed, 0, 0, 0, maxScroll());
                        postInvalidateOnAnimation(); return true;
                    }
                }
                stopScrolling(); return true;
            case MotionEvent.ACTION_CANCEL: stopScrolling(); return true;
            default: return true;
        }
    }
    @Override public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
        if (!browse) return super.requestChildRectangleOnScreen(child, rectangle, immediate);
        int top = child.getTop() + rectangle.top, bottom = child.getTop() + rectangle.bottom, y = getScrollY(), height = getHeight();
        int target = top < y || bottom - top > height ? top : bottom > y + height ? bottom - height : y;
        if (target == y) return false;
        stopScrolling(); scrollTo(0, target);
        return getScrollY() != y;
    }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (!browse || maxScroll() == 0) return;
        info.setScrollable(true);
        if (getScrollY() > 0) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        if (getScrollY() < maxScroll()) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
    }
    @Override public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (browse && (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
            int before = getScrollY(), page = Math.max(1, getHeight() - 2 * gap());
            stopScrolling(); scrollTo(0, before + (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? page : -page));
            return getScrollY() != before;
        }
        return super.performAccessibilityAction(action, arguments);
    }
}
