package com.fieldview.tv;

import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import java.util.function.BooleanSupplier;

/** System bars and camera cutouts remain outside phone controls, including Android 15+. */
final class PhoneInsets {
    static void install(View view, BooleanSupplier fullscreen, int horizontal, int vertical) {
        install(view, fullscreen, horizontal, vertical, false);
    }
    static void install(View view, BooleanSupplier fullscreen, int horizontal, int vertical, boolean keyboard) {
        view.setOnApplyWindowInsetsListener((target, insets) -> {
            int[] safe = Build.VERSION.SDK_INT >= 30 ? Modern.safe(insets, fullscreen.getAsBoolean(), keyboard)
                    : new int[]{insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()};
            int x = fullscreen.getAsBoolean() ? 0 : horizontal, y = fullscreen.getAsBoolean() ? 0 : vertical;
            target.setPadding(safe[0] + x, safe[1] + y, safe[2] + x, safe[3] + y);
            return insets;
        });
        view.requestApplyInsets();
    }
    @android.annotation.TargetApi(30)
    private static final class Modern {
        static int[] safe(WindowInsets insets, boolean fullscreen, boolean keyboard) {
            android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.displayCutout()
                    | (fullscreen ? 0 : WindowInsets.Type.systemBars()) | (keyboard ? WindowInsets.Type.ime() : 0));
            return new int[]{safe.left, safe.top, safe.right, safe.bottom};
        }
    }
    private PhoneInsets() { }
}
