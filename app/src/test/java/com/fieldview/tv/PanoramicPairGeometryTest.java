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
public class PanoramicPairGeometryTest {
    private List<Rect> layout(int count, double first, double second) {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup();
        try {
            CameraWall wall = new CameraWall(controller.get()); wall.setFullscreen(true);
            wall.setMotionLayoutSlots(2); wall.setFeaturedCount(2); wall.setActiveAspects(first, second);
            List<View> children = new ArrayList<>();
            for (int i = 0; i < count; i++) { View v = new View(controller.get()); children.add(v); wall.addView(v); }
            wall.setOrder(children);
            wall.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
            wall.layout(0, 0, 1920, 1080);
            List<Rect> result = new ArrayList<>(); long area = 0;
            for (View v : children) {
                Rect r = new Rect(v.getLeft(), v.getTop(), v.getRight(), v.getBottom());
                assertTrue(r.width() > 0 && r.height() > 0); assertTrue(r.left >= 0 && r.top >= 0 && r.right <= 1920 && r.bottom <= 1080);
                for (Rect previous : result) assertFalse(Rect.intersects(previous, r));
                area += (long) r.width() * r.height(); result.add(r);
            }
            assertEquals(1920L * 1080, area); return result;
        } finally { controller.pause().stop().destroy(); }
    }
    @Test public void twoDuosStackWithFourSixteenByNineThumbnails() {
        List<Rect> r = layout(6, 8.0/3, 8.0/3);
        assertEquals(new Rect(0,0,1440,540),r.get(0)); assertEquals(new Rect(0,540,1440,1080),r.get(1));
        for (int i=2;i<6;i++) { assertEquals(480,r.get(i).width()); assertEquals(270,r.get(i).height()); }
    }
    @Test public void mixedPairFitsBothActiveImagesAndSixThumbnailsInEitherFeaturedOrder() {
        for (boolean wideFirst : new boolean[]{true,false}) {
            List<Rect> r = layout(8,wideFirst?8.0/3:16.0/9,wideFirst?16.0/9:8.0/3);
            assertEquals(new Rect(0,0,1440,540),r.get(wideFirst?0:1));
            assertEquals(new Rect(0,540,960,1080),r.get(wideFirst?1:0));
            for (int i=2;i<8;i++) { assertEquals(480,r.get(i).width()); assertEquals(270,r.get(i).height()); }
        }
    }
    @Test public void unequalPanoramasUseTheirMeasuredRatiosAndBadMetadataRemainsBounded() {
        List<Rect> r = layout(6,2.5,3);
        assertEquals(2.5,(double)r.get(0).width()/r.get(0).height(),.01);
        assertEquals(3,(double)r.get(1).width()/r.get(1).height(),.01);
        layout(8,8.0/3,Double.NaN);
        layout(10,Double.POSITIVE_INFINITY,0);
    }
}
