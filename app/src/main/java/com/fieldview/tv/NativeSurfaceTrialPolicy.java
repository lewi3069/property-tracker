package com.fieldview.tv;

/** Scope the optional output experiment to the profile observed in allocation traces. */
final class NativeSurfaceTrialPolicy {
    static boolean enabled(boolean requested, boolean television, String manufacturer, String model, int sdk) {
        return requested && television && sdk == 30
                && "Amazon".equalsIgnoreCase(manufacturer) && "AFTKM".equals(model);
    }
    private NativeSurfaceTrialPolicy() { }
}
