package com.fieldview.tv;

import android.app.Activity;
import android.graphics.Rect;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DoorbellGeometryTest {
    private List<Rect> layout(int width, int height, double aspect) {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup();
        try {
            CameraWall wall = new CameraWall(controller.get()); wall.setFullscreen(true);
            wall.setMotionLayoutSlots(1); wall.setFeaturedCount(1); wall.setActiveAspects(aspect, 0);
            List<View> children = new ArrayList<>();
            for (int i = 0; i < 11; i++) { View v = new View(controller.get()); children.add(v); wall.addView(v); }
            wall.setOrder(children);
            wall.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            wall.layout(0, 0, width, height);
            List<Rect> bounds = new ArrayList<>(); long area = 0;
            for (View v : children) {
                Rect r = new Rect(v.getLeft(), v.getTop(), v.getRight(), v.getBottom());
                assertTrue(r.width() > 0 && r.height() > 0);
                assertTrue(r.left >= 0 && r.top >= 0 && r.right <= width && r.bottom <= height);
                for (Rect previous : bounds) assertFalse(Rect.intersects(previous, r));
                area += (long) r.width() * r.height(); bounds.add(r);
            }
            assertEquals((long) width * height, area); return bounds;
        } finally { controller.pause().stop().destroy(); }
    }
    @Test public void doorbellFitsWithSixOnTheRightAndFourAcrossTheBottom() {
        List<Rect> r = layout(1920, 1080, 4.0/3);
        assertEquals(new Rect(0, 0, 1080, 810), r.get(0));
        for (int i = 1; i <= 6; i++) {
            int row = (i - 1) / 2, column = (i - 1) % 2;
            assertEquals(new Rect(1080 + column * 420, row * 270, 1500 + column * 420, (row + 1) * 270), r.get(i));
        }
        for (int i = 7; i < 11; i++) assertEquals(new Rect((i - 7) * 480, 810, (i - 6) * 480, 1080), r.get(i));
    }
    @Test public void otherTvSizesAndNearbyMeasuredRatiosStillFitWithoutOverlap() {
        for (int[] size : new int[][]{{1280,720}, {3840,2160}})
            for (double ratio : new double[]{1.30, 4.0/3, 1.38}) {
                List<Rect> r = layout(size[0], size[1], ratio);
                assertEquals(ratio, (double) r.get(0).width() / r.get(0).height(), .002);
                for (int i = 1; i <= 6; i++) assertTrue(r.get(i).left >= r.get(0).right);
                for (int i = 7; i < 11; i++) assertEquals(r.get(0).bottom, r.get(i).top);
            }
    }
}
