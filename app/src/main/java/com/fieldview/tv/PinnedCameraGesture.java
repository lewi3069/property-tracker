package com.fieldview.tv;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import androidx.media3.common.util.UnstableApi;

/**
 * Touch on the phone's one manually pinned fullscreen camera. At the 1x fit, a deliberate single-finger vertical
 * drag, judged on release, steps one enabled camera (up = next, down = previous). Two fingers pinch the picture about
 * their focus; one finger pans it while zoomed. A second finger ends any swipe for the rest of the touch, so a pinch
 * never changes camera. Stationary touches stay with the tile, so long-press still opens More; a claimed touch
 * cancels the tile's tap and long-press. Screen edges and system Back stay with Android.
 */
@UnstableApi
final class PinnedCameraGesture {
    interface Host {
        /** The pinned phone camera while a touch may zoom, pan or swipe it; otherwise null. */
        CameraTile pinnedTile();
        /** Delivers the cancel to the views, ending the tile's own tap and long-press for this touch. */
        void cancelViews(MotionEvent cancel);
        /** Steps one enabled camera: 1 for the next, -1 for the previous. */
        void step(int direction);
    }
    /** Minimum vertical travel for a camera swipe; it must also be at least twice the horizontal travel. */
    static final int SWIPE_DP = 72;
    private final Host host;
    private final int slop;
    private final float swipeDistance;
    private CameraTile tile;
    // captured: the tile was canceled and the rest of the touch is ours. passive: a sideways 1x drag stays with the
    // tile, though a second finger can still claim the touch. swiping: a 1x vertical drag, judged on release.
    private boolean captured, passive, swiping;
    private int pointer, fingers;
    private float downX, downY, originX, originY, focusX, focusY, span;

    PinnedCameraGesture(Context context, Host host) {
        this.host = host;
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        swipeDistance = SWIPE_DP * context.getResources().getDisplayMetrics().density;
    }

    /** True when the event belongs to a claimed gesture and must not reach the views. */
    boolean onTouch(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            reset(); tile = host.pinnedTile();
            if (tile == null) return false;
            int[] at = new int[2]; tile.getLocationInWindow(at); originX = at[0]; originY = at[1];
            pointer = event.getPointerId(0); downX = event.getX(); downY = event.getY(); baseline(event, -1);
            return false;
        }
        if (tile == null) return false;
        if (tile != host.pinnedTile()) { boolean claimed = captured; reset(); return claimed; }
        switch (action) {
            case MotionEvent.ACTION_POINTER_DOWN:
                swiping = false; capture(event); baseline(event, -1); return true;
            case MotionEvent.ACTION_POINTER_UP:
                // The fingers that stay down carry on from where they are: no jump when a finger lifts or is replaced.
                baseline(event, event.getActionIndex()); return captured;
            case MotionEvent.ACTION_MOVE: return move(event);
            case MotionEvent.ACTION_UP: {
                boolean claimed = captured, swipe = swiping;
                int index = event.findPointerIndex(pointer);
                float dx = index < 0 ? 0 : event.getX(index) - downX, dy = index < 0 ? 0 : event.getY(index) - downY;
                reset();
                if (swipe && index >= 0 && Math.abs(dy) >= swipeDistance && Math.abs(dy) >= 2 * Math.abs(dx)) host.step(dy < 0 ? 1 : -1);
                return claimed;
            }
            case MotionEvent.ACTION_CANCEL: { boolean claimed = captured; reset(); return claimed; }
            default: return captured;
        }
    }
    private boolean move(MotionEvent event) {
        if (swiping) return true;
        if (!captured) {
            if (passive) return false;
            int index = event.findPointerIndex(pointer);
            if (index < 0) return false;
            float dx = event.getX(index) - downX, dy = event.getY(index) - downY;
            if (Math.abs(dx) <= slop && Math.abs(dy) <= slop) return false;
            if (tile.videoZoom() <= 1f) {
                if (Math.abs(dy) <= Math.abs(dx)) { passive = true; return false; }
                swiping = true; capture(event); return true;
            }
            capture(event); // Zoomed: the pan follows the finger from where it touched down.
        }
        float fromX = focusX, fromY = focusY, fromSpan = span; int fromFingers = fingers;
        baseline(event, -1);
        float factor = fingers > 1 && fingers == fromFingers && fromSpan > 0 ? span / fromSpan : 1f;
        tile.zoomVideo(factor, fromX - originX, fromY - originY, focusX - originX, focusY - originY);
        return true;
    }
    private void capture(MotionEvent event) {
        if (captured) return;
        captured = true;
        MotionEvent cancel = MotionEvent.obtain(event); cancel.setAction(MotionEvent.ACTION_CANCEL);
        host.cancelViews(cancel); cancel.recycle();
    }
    /** Focus and mean distance from it of the fingers that stay down; skip is the index of a lifting finger, or -1. */
    private void baseline(MotionEvent event, int skip) {
        float x = 0, y = 0, distance = 0; int count = 0;
        for (int i = 0; i < event.getPointerCount(); i++) if (i != skip) { x += event.getX(i); y += event.getY(i); count++; }
        if (count == 0) return;
        x /= count; y /= count;
        for (int i = 0; i < event.getPointerCount(); i++) if (i != skip) distance += (float) Math.hypot(event.getX(i) - x, event.getY(i) - y);
        focusX = x; focusY = y; span = distance / count; fingers = count;
    }
    void reset() { tile = null; captured = passive = swiping = false; }
}
