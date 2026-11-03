package com.fieldview.tv;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w640dp-h360dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class TvFormDialogTest {
    private ActivityController<Activity> controller;
    private Activity activity;
    private TvFormDialog dialog;
    private EditText last;

    @Before public void setUp() {
        controller = Robolectric.buildActivity(Activity.class).setup();
        activity = controller.get();
        LinearLayout form = new LinearLayout(activity); form.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < 12; i++) {
            last = new EditText(activity); last.setSingleLine(true); last.setText("Camera field " + i);
            form.addView(last, new LinearLayout.LayoutParams(-1, 64));
        }
        dialog = new TvFormDialog(activity, "Add camera", form); dialog.show();
        shadowOf(Looper.getMainLooper()).idle();
    }

    @After public void tearDown() { dialog.dismiss(); controller.pause().stop().destroy(); }

    private View layout(int width, int height) {
        View decor = dialog.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, width, height);
        return decor;
    }

    private void assertSaveVisible(int width, int height) {
        View decor = layout(width, height);
        Button save = dialog.saveButton();
        Rect actual = new Rect();
        assertTrue("Save must have a visible rectangle", save.getGlobalVisibleRect(actual));
        assertEquals("Save must not be clipped vertically", save.getHeight(), actual.height());
        assertEquals("Save must not be clipped horizontally", save.getWidth(), actual.width());
        assertTrue(actual.bottom <= height);
        ScrollView scroll = dialog.findViewById(R.id.form_scroll);
        assertTrue("The long form must scroll instead of pushing away Save", scroll.getChildAt(0).getHeight() > scroll.getHeight());
        Rect actions = new Rect();
        assertTrue(dialog.findViewById(R.id.form_actions).getGlobalVisibleRect(actions));
        assertTrue("Save must remain outside the scrolling region", scroll.getBottom() <= dialog.findViewById(R.id.form_actions).getTop());
    }

    @Test public void saveIsVisibleOnCompactLandscapeScreenEvenAfterScrolling() {
        assertSaveVisible(640, 360);
        ScrollView scroll = dialog.findViewById(R.id.form_scroll);
        int originalTop = dialog.saveButton().getTop();
        scroll.fullScroll(View.FOCUS_DOWN); layout(640, 360);
        assertSaveVisible(640, 360);
        assertEquals(originalTop, dialog.saveButton().getTop());
    }

    @Test public void actionBarRemainsVisibleWhenKeyboardReducesHeight() {
        assertSaveVisible(640, 220);
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
                dialog.getWindow().getAttributes().softInputMode & WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST);
    }

    @Test public void lastFieldCanMoveToSaveAndBackUsingRemoteFocus() {
        layout(640, 360);
        last.requestFocus();
        assertSame(dialog.saveButton(), last.focusSearch(View.FOCUS_DOWN));
        dialog.saveButton().requestFocus();
        assertSame(last, dialog.saveButton().focusSearch(View.FOCUS_UP));
    }

    @Test public void keyboardDoneFocusesSaveAndButtonExecutesSaveAction() {
        layout(640, 360);
        last.requestFocus();
        last.onEditorAction(EditorInfo.IME_ACTION_DONE);
        assertTrue(dialog.saveButton().hasFocus());
        boolean[] saved = {false};
        dialog.saveButton().setOnClickListener(view -> saved[0] = true);
        assertTrue(dialog.saveButton().performClick());
        assertTrue(saved[0]);
    }
}
