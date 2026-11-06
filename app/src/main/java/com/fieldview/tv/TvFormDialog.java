package com.fieldview.tv;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** TV form with bounded scrolling and an action row that never scrolls away. */
public final class TvFormDialog extends Dialog {
    private final Button save, cancel, remove;
    private final EditText lastField;

    public TvFormDialog(Context context, String title, View form) {
        super(context, R.style.TvFormDialogTheme);
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(12), dp(24), dp(12));
        page.setFitsSystemWindows(true);
        page.setBackgroundColor(0xFF101516);

        TextView heading = new TextView(context);
        heading.setId(R.id.form_title); heading.setText(title); heading.setTextColor(Color.WHITE);
        heading.setTextSize(22); heading.setPadding(dp(8), dp(4), dp(8), dp(10));
        if (Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
        page.addView(heading, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(context);
        scroll.setId(R.id.form_scroll); scroll.setFillViewport(false);
        scroll.addView(form, new ScrollView.LayoutParams(-1, -2));
        // The form receives only the space left after the heading and buttons.
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout actions = new LinearLayout(context);
        actions.setId(R.id.form_actions); actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(8), dp(8), dp(8), 0);
        remove = action(context, "Remove", R.id.form_remove);
        remove.setVisibility(View.GONE); actions.addView(remove, new LinearLayout.LayoutParams(dp(112), dp(48)));
        actions.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1));
        cancel = action(context, "Cancel", R.id.form_cancel);
        actions.addView(cancel, new LinearLayout.LayoutParams(dp(112), dp(48)));
        save = action(context, "Save camera", R.id.form_save);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(dp(152), dp(48));
        saveParams.setMarginStart(dp(12)); actions.addView(save, saveParams);
        page.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        setContentView(page);
        cancel.setOnClickListener(view -> dismiss());
        save.setNextFocusLeftId(cancel.getId()); cancel.setNextFocusRightId(save.getId());
        cancel.setNextFocusDownId(cancel.getId()); save.setNextFocusDownId(save.getId());
        lastField = findLastField(form);
        if (lastField != null) {
            if (lastField.getId() == View.NO_ID) lastField.setId(View.generateViewId());
            lastField.setNextFocusDownId(save.getId());
            lastField.setNextFocusForwardId(save.getId());
            save.setNextFocusUpId(lastField.getId()); cancel.setNextFocusUpId(lastField.getId());
            remove.setNextFocusUpId(lastField.getId());
            lastField.setImeOptions(EditorInfo.IME_ACTION_DONE);
            lastField.setOnEditorActionListener((view, actionId, event) -> {
                if (actionId != EditorInfo.IME_ACTION_DONE) return false;
                InputMethodManager keyboard = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (keyboard != null) keyboard.hideSoftInputFromWindow(view.getWindowToken(), 0);
                save.requestFocus();
                return true;
            });
        }
    }

    @Override protected void onStart() {
        super.onStart();
        Window window = getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
    }

    public Button saveButton() { return save; }
    public void setRemoveAction(Runnable click) {
        remove.setVisibility(View.VISIBLE); remove.setOnClickListener(view -> click.run());
        cancel.setNextFocusLeftId(remove.getId()); remove.setNextFocusRightId(cancel.getId());
    }
    private Button action(Context context, String label, int id) {
        Button button = new Button(context); button.setId(id); button.setText(label);
        button.setTextSize(15); button.setAllCaps(false);
        button.setBackgroundResource(R.drawable.form_action_background);
        button.setTextColor(Build.VERSION.SDK_INT >= 23 ? context.getColorStateList(R.color.form_action_text)
                : context.getResources().getColorStateList(R.color.form_action_text));
        return button;
    }
    private EditText findLastField(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                EditText result = findLastField(group.getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
    }
    private int dp(int value) { return Math.round(value * getContext().getResources().getDisplayMetrics().density); }
}
