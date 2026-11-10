package com.fieldview.tv;

/** One hardware main stream in a dense wall; its neighbours remain native substreams. */
final class WallStreamPlan {
    private final ViewSelector.Selection selection;
    private final boolean nativeGrid, useMain;
    private final String center;
    private final java.util.Set<String> unavailable;

    WallStreamPlan(ViewSelector.Selection selection, boolean nativeGrid, boolean useMain, boolean highResolutionCenter) {
        this(selection, nativeGrid, useMain, highResolutionCenter, java.util.Collections.emptySet());
    }
    WallStreamPlan(ViewSelector.Selection selection, boolean nativeGrid, boolean useMain, boolean highResolutionCenter,
                   java.util.Set<String> unavailable) {
        this(selection, nativeGrid, useMain, highResolutionCenter, unavailable, false);
    }
    WallStreamPlan(ViewSelector.Selection selection, boolean nativeGrid, boolean useMain, boolean highResolutionCenter,
                   java.util.Set<String> unavailable, boolean motionLayoutEight) {
        this.selection = selection;
        this.unavailable = unavailable;
        this.nativeGrid = nativeGrid;
        this.useMain = useMain;
        // The 13-tile geometry puts the first visible camera in the center, even in Grid/overview.
        int slots = selection.motionLayoutSlots;
        int expected = slots == 1 ? 8 : slots == 2 ? 10 : slots == 3 ? 7 : slots == 4 ? 4 : 0;
        boolean motionEight = motionLayoutEight && expected > 0 && selection.visible.size() == expected
                && ViewSelector.AUTO.equals(selection.mode) && "activity".equals(selection.reason);
        center = nativeGrid && useMain && ((highResolutionCenter && selection.visible.size() == 13) || motionEight)
                ? selection.visible.get(0) : "";
    }

    boolean highResolutionCenter(AppConfig.Camera camera) {
        return camera.id.equals(center) && !unavailable.contains(camera.id) && !camera.streamUri(true).equals(camera.streamUri(false));
    }
    boolean mainUnavailable(AppConfig.Camera camera) { return camera.id.equals(center) && unavailable.contains(camera.id); }
    boolean nativeGrid(AppConfig.Camera camera) { return nativeGrid && !highResolutionCenter(camera); }
    boolean main(AppConfig.Camera camera) {
        return highResolutionCenter(camera) || (!nativeGrid && useMain && selection.featured.contains(camera.id));
    }
}
