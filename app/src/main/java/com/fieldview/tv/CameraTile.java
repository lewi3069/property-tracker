package com.fieldview.tv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.upstream.DefaultAllocator;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import java.util.ArrayList;
import java.util.List;

@UnstableApi
public final class CameraTile extends FrameLayout {
    private PlayerView surface;
    private final TextView status;
    private final boolean floating;
    private boolean texture;
    private StartupCoordinator coordinator = StartupCoordinator.shared();
    private GridVideoSession gridSession;
    private GridVideoSession previewSession;
    private VideoProgress previewProgress;
    private StartupCoordinator.Ticket startup;
    private String cameraLabel;
    private final String session = java.util.UUID.randomUUID().toString().substring(0, 8);
    private int generation;
    private long attemptStartedAt;
    private boolean mainStream;
    private boolean nativeGrid;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private String activeUri;
    private String requestedUri, fallbackUri;
    private boolean fallbackActive;
    private boolean mainSuppressed;
    private boolean mainFormatRejected, mainSuppressedFormatUnsupported;
    private Runnable mainUnavailable;
    private java.util.function.DoubleConsumer aspectListener;
    private double reportedAspect;
    private boolean reportedMainStream;
    void setAspectListener(java.util.function.DoubleConsumer value) { aspectListener = value; }
    private void reportAspect(int width, int height) {
        reportAspect(width, height, 1f);
    }
    private void reportAspect(int width, int height, float pixelWidthHeightRatio) {
        if (width <= 0 || height <= 0) return;
        double ratio = (double) width / height;
        if (!BuildConfig.TV_INTERFACE) {
            double displayAspect = ratio;
            if (Float.isFinite(pixelWidthHeightRatio) && pixelWidthHeightRatio > 0) {
                displayAspect *= pixelWidthHeightRatio;
            }
            // Refit the zoom bounds when the displayed picture changes shape, including after a stream fallback.
            if (displayAspect != pictureAspect) {
                pictureAspect = displayAspect;
                if (zoom != 1f) applyZoom();
            }
        }
        if (Math.abs(ratio - reportedAspect) < .01
                && (BuildConfig.TV_INTERFACE || reportedMainStream == mainStream)) return;
        reportedAspect = ratio;
        reportedMainStream = mainStream;
        Log.i("FieldviewPlayer", diagnosticPrefix() + "event=video_size size=" + width + "x" + height);
        if (aspectListener != null) aspectListener.accept(ratio);
    }
    private Runnable retry;
    private Runnable stalled;
    private VideoProgress progress;
    private DefaultAllocator allocator;
    private long nextHealthLogAt;
    private long nativeTileResizes;
    private int attempts, stalledRecoveries;
    private boolean released, featured, firstFrame, fullscreen, fullscreenFocusBorder, softwareFloatingH264;

    public CameraTile(Context context, String name) {
        this(context, name, false);
    }

    public CameraTile(Context context, String name, boolean floating) {
        this(context, name, floating, false);
    }
    CameraTile(Context context, String name, boolean floating, boolean textureGrid) {
        this(context, name, floating, textureGrid, false);
    }
    CameraTile(Context context, String name, boolean floating, boolean textureGrid, boolean nativeGrid) {
        super(context);
        this.floating = floating;
        this.texture = !floating && textureGrid;
        this.nativeGrid = !floating && texture && nativeGrid;
        setId(View.generateViewId()); setFocusable(true); setClickable(true);
        cameraLabel = "tile-" + getId();
        setContentDescription("Enlarge " + name);
        setPadding(dp(3), dp(3), dp(3), dp(3));
        // Native playback supplies its own view. Avoid inflating a hidden Media3 hierarchy per tile.
        if (!this.nativeGrid) ensureSurface();
        status = new TextView(context); status.setTextColor(0xFFE3EFDB); status.setTextSize(12);
        status.setMaxLines(3);
        status.setPadding(dp(10), dp(5), dp(10), dp(5)); status.setBackgroundColor(0xB0000000);
        addView(status, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START));
        setOnFocusChangeListener((view, focus) -> updateBorder());
        updateBorder();
    }

    private void ensureSurface() {
        if (surface != null) return;
        surface = floating || texture ? (PlayerView) LayoutInflater.from(getContext()).inflate(texture ? R.layout.grid_texture_player : R.layout.floating_player, this, false) : new PlayerView(getContext());
        surface.setUseController(false); surface.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        surface.setFocusable(false); surface.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        // A retained native TextureView stays attached above the new main SurfaceView.
        addView(surface, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    /** Reuse live grid video while one hardware main connects, without moving its native view. */
    boolean prepareMainFromGrid(String main, String grid) {
        if (released || !nativeGrid || gridSession == null || !gridSession.supportsLivePreview() || !firstFrame || progress == null
                || progress.silentFor(SystemClock.elapsedRealtime()) >= 5_000 || !grid.equals(requestedUri)
                || main.equals(grid) || coordinator == null) return false;
        clearStallTimeout(); finishStartup();
        previewSession = gridSession; previewProgress = progress; gridSession = null;
        nativeGrid = false; texture = false; firstFrame = false; mainStream = true;
        requestedUri = activeUri = main; fallbackUri = grid; fallbackActive = false;
        mainFormatRejected = false; attempts = stalledRecoveries = 0;
        ensureSurface();
        Log.i("FieldviewPlayer", diagnosticPrefix() + "event=preview_retained");
        queueStart(); return true;
    }

    private void releasePreview() {
        GridVideoSession old = previewSession; previewSession = null; previewProgress = null;
        if (old == null) return;
        Runnable complete = coordinator.beginRelease();
        Log.i("FieldviewPlayer", diagnosticPrefix() + "event=preview_release");
        old.close(complete); removeView(old.view());
    }

    private boolean restorePreview() {
        if (previewSession == null || previewProgress == null || previewProgress.stalled(SystemClock.elapsedRealtime())) return false;
        releasePrimaryPlayer();
        gridSession = previewSession; progress = previewProgress; previewSession = null; previewProgress = null;
        if (surface != null) { removeView(surface); surface = null; }
        nativeGrid = texture = true; firstFrame = true; mainStream = fallbackActive = false;
        requestedUri = activeUri = fallbackUri; fallbackUri = null;
        attempts = stalledRecoveries = 0; nextHealthLogAt = SystemClock.elapsedRealtime() + 30_000;
        GridVideoSession.Stats size = gridSession.stats(); reportAspect(size.width, size.height);
        showStatus(liveStatus()); armStallTimeout();
        Log.i("FieldviewPlayer", diagnosticPrefix() + "event=preview_restored");
        return true;
    }

    public void setFeatured(boolean value) {
        featured = value;
        if (startup != null) startup.prioritize(value);
        if (BuildConfig.TV_INTERFACE) updateBorder();
    }
    boolean usesTexture() { return texture; }
    boolean usesNativeGrid() { return nativeGrid; }
    /** True while this tile's current attempt is a main stream rather than the grid substream. */
    boolean playingMainStream() { return mainStream; }
    void setMainUnavailableListener(Runnable value) { mainUnavailable = value; }
    boolean mainFormatRejected() { return mainFormatRejected; }
    void setMainSuppressed(boolean value) { setMainSuppressed(value, false); }
    void setMainSuppressed(boolean value, boolean formatUnsupported) {
        mainSuppressed = value; mainSuppressedFormatUnsupported = value && formatUnsupported;
        if (firstFrame) showStatus(liveStatus());
    }
    static boolean unsupportedVideoTracks(Tracks tracks) {
        boolean hasVideo = false;
        for (Tracks.Group group : tracks.getGroups()) if (group.getType() == C.TRACK_TYPE_VIDEO) {
            hasVideo = true;
            for (int i = 0; i < group.length; i++)
                if (group.isTrackSupported(i) && !HardwareMainRenderersFactory.tooWide(group.getTrackFormat(i))) return false;
        }
        return hasVideo;
    }
    static void configureVideoTracks(DefaultTrackSelector tracks, boolean hardwareMain) {
        tracks.setParameters(tracks.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .setExceedRendererCapabilitiesIfNecessary(!hardwareMain));
    }
    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (nativeGrid && width > 0 && height > 0 && (width != oldWidth || height != oldHeight)) nativeTileResizes++;
        if (zoom != 1f) applyZoom();
    }

    // Phone Pin zoom. Only the video view scales and moves: the tile, its layout and its status label stay fixed,
    // and the stream, decoder and surface are untouched. Kept across a same-camera fallback; reset on release.
    static final float MAX_ZOOM = 4f;
    private float zoom = 1f, panX, panY;
    private double pictureAspect;
    float videoZoom() { return zoom; }
    /**
     * Scales by factor within 1x fit to 4x so that the picture point under from (tile coordinates) lands under to,
     * then clamps the offset to the fitted picture.
     */
    void zoomVideo(float factor, float fromX, float fromY, float toX, float toY) {
        float next = Math.max(1f, Math.min(MAX_ZOOM, zoom * factor)), ratio = next / zoom;
        panX = toX - getPaddingLeft() - (fromX - getPaddingLeft() - panX) * ratio;
        panY = toY - getPaddingTop() - (fromY - getPaddingTop() - panY) * ratio;
        zoom = next; applyZoom();
    }
    void resetZoom() {
        if (zoom == 1f && panX == 0f && panY == 0f) return;
        zoom = 1f; panX = panY = 0f; applyZoom();
    }
    private void applyZoom() {
        int width = getWidth() - getPaddingLeft() - getPaddingRight(), height = getHeight() - getPaddingTop() - getPaddingBottom();
        if (width > 0 && height > 0) {
            // The measured view fits the picture at its own shape, centered; an unknown shape fills the view.
            double aspect = pictureAspect > 0 ? pictureAspect : (double) width / height;
            panX = clampPan(panX, width, (float) Math.min(width, height * aspect));
            panY = clampPan(panY, height, (float) Math.min(height, width / aspect));
        }
        transformVideo(surface);
        if (gridSession != null) transformVideo(gridSession.view());
    }
    /** The scaled picture covers the view along this axis, or stays centered while smaller, so letterbox bars never pan in. */
    private float clampPan(float pan, int view, float picture) {
        float start = (view - picture) / 2 * zoom, scaled = picture * zoom;
        if (scaled <= view) return (view - scaled) / 2 - start;
        return Math.max(view - start - scaled, Math.min(-start, pan));
    }
    private void transformVideo(View video) {
        if (video == null) return;
        video.setPivotX(0); video.setPivotY(0); video.setScaleX(zoom); video.setScaleY(zoom);
        video.setTranslationX(panX); video.setTranslationY(panY);
    }
    /** Set before play; wall playback and HEVC retain the normal decoder policy. */
    public void setSoftwareFloatingH264(boolean value) { softwareFloatingH264 = floating && value; }
    static MediaCodecSelector decoderSelector(boolean preferSoftware, MediaCodecSelector regular, MediaCodecSelector software) {
        return (mime, secure, tunneling) -> (preferSoftware && MimeTypes.VIDEO_H264.equals(mime) ? software : regular)
                .getDecoderInfos(mime, secure, tunneling);
    }
    static MediaCodecSelector softwareFirst(MediaCodecSelector base) {
        return (mime, secure, tunneling) -> {
            List<MediaCodecInfo> codecs = new ArrayList<>(base.getDecoderInfos(mime, secure, tunneling));
            codecs.sort((a, b) -> Boolean.compare(b.softwareOnly, a.softwareOnly));
            return codecs;
        };
    }
    static MediaCodecSelector hardwareOnly(MediaCodecSelector base) {
        return (mime, secure, tunneling) -> {
            List<MediaCodecInfo> codecs = new ArrayList<>(base.getDecoderInfos(mime, secure, tunneling));
            codecs.removeIf(codec -> !codec.hardwareAccelerated);
            return codecs;
        };
    }
    public void setFullscreen(boolean value) {
        setFullscreen(value, false);
    }
    /** Multi-camera fullscreen retains a visible remote-focus border. */
    public void setFullscreen(boolean value, boolean focusBorder) {
        if (fullscreen == value && fullscreenFocusBorder == focusBorder) return;
        fullscreen = value; fullscreenFocusBorder = focusBorder;
        int padding = value && !focusBorder ? 0 : dp(3);
        setPadding(padding, padding, padding, padding);
        updateStatusVisibility(); updateBorder();
    }
    private void showStatus(String value) { status.setText(value); updateStatusVisibility(); }
    private void updateStatusVisibility() { status.setVisibility(fullscreen && "Live".contentEquals(status.getText()) ? View.GONE : View.VISIBLE); }
    private void updateBorder() {
        if (!BuildConfig.TV_INTERFACE) { setBackgroundColor(Color.BLACK); return; }
        if (fullscreen && !fullscreenFocusBorder) { setBackgroundColor(Color.BLACK); return; }
        GradientDrawable background = new GradientDrawable(); background.setColor(Color.BLACK);
        background.setStroke(dp(hasFocus() ? 3 : 1), hasFocus() ? 0xFFD9F2BD : featured ? 0xFF9BBF79 : 0xFF28322D);
        setBackground(background);
    }

    public void play(String uri) {
        play(uri, false);
    }
    void configureStartup(StartupCoordinator value, String cameraId) {
        coordinator = value;
        cameraLabel = cameraId != null && cameraId.matches("[A-Za-z0-9_-]{1,80}") ? cameraId : "tile-" + getId();
    }
    void play(String uri, boolean useMain) {
        playRequest(uri, useMain, null);
    }
    void playMainOrGrid(String main, String grid) {
        playRequest(main, !main.equals(grid), main.equals(grid) ? null : grid);
    }
    private void playRequest(String uri, boolean useMain, String fallback) {
        boolean sameRequest = !released && uri.equals(requestedUri) && java.util.Objects.equals(fallback, fallbackUri);
        if (sameRequest
                && (player != null || gridSession != null || retry != null || startup != null)) return;
        disposePlayer();
        if (!sameRequest) mainFormatRejected = false;
        fallbackActive = sameRequest && fallbackActive;
        mainStream = useMain && !fallbackActive;
        requestedUri = uri; fallbackUri = fallback;
        activeUri = fallbackActive ? fallback : uri; released = false; attempts = 0; stalledRecoveries = 0;
        queueStart();
    }
    private String liveStatus() {
        if ((!fallbackActive && !mainSuppressed) || mainFormatRejected || mainSuppressedFormatUnsupported) return "Live";
        return "Grid stream · main unavailable";
    }
    private void queueStart() {
        if (released) return;
        if (coordinator == null) { startPlayer(); return; }
        showStatus(previewSession != null ? "Live" : "Waiting to connect…");
        startup = coordinator.enqueue(featured, ticket -> {
            if (released || startup != ticket) { ticket.finish(); return; }
            startPlayer();
        }, () -> { if (!released) scheduleRetry("Camera startup timed out"); });
    }
    private void finishStartup() {
        StartupCoordinator.Ticket previous = startup; startup = null;
        if (previous != null) previous.finish();
    }
    private String diagnosticPrefix() {
        return "camera=" + cameraLabel + " session=" + session + " attempt=" + generation
                + " view=" + (floating ? "float" : "wall") + " profile=" + (mainStream ? "main" : "sub") + " ";
    }

    /** Phone: only the manually pinned, non-floating fullscreen RTSP main stream uses the native hardware-first backend. */
    static boolean phoneNativeMain(boolean tv, boolean floating, boolean main, boolean fullscreen, boolean focusBorder, String uri) {
        return !tv && !floating && main && fullscreen && !focusBorder && uri != null && uri.startsWith("rtsp://");
    }
    boolean usesPhoneNativeMain() {
        return !nativeGrid && phoneNativeMain(BuildConfig.TV_INTERFACE, floating, mainStream, fullscreen, fullscreenFocusBorder, activeUri);
    }
    /** TV hardware-main policy (hardware-only decoders, format checks); never applied to phone playback. */
    private boolean tvHardwareMain() { return BuildConfig.TV_INTERFACE && mainStream && fallbackUri != null; }

    private void startPlayer() {
        if (released) return;
        generation++; attemptStartedAt = SystemClock.elapsedRealtime();
        boolean phoneMain = usesPhoneNativeMain();
        Log.i("FieldviewPlayer", diagnosticPrefix() + "event=start renderer=" + (texture ? "texture" : "surface") + " engine="
                + (nativeGrid ? "native" : phoneMain ? "libvlc decoder=hardware_first" : "media3"));
        firstFrame = false;
        VideoProgress attemptProgress = new VideoProgress(SystemClock.elapsedRealtime());
        progress = attemptProgress;
        nextHealthLogAt = SystemClock.elapsedRealtime() + 30_000;
        showStatus(previewSession != null ? "Live" : "Connecting…");
        armStallTimeout();
        if (nativeGrid || phoneMain) {
            // A failed phone main falls back once to its distinct substream on Media3 (scheduleRetry); never main to main.
            try { startNativeGrid(attemptProgress, phoneMain); }
            catch (RuntimeException | LinkageError error) { scheduleRetry(phoneMain ? "Main stream unavailable" : "Native grid unavailable · try the standard layout"); }
            return;
        }
        try {
            boolean hardwareMain = tvHardwareMain();
            DefaultTrackSelector tracks = new DefaultTrackSelector(getContext());
            configureVideoTracks(tracks, hardwareMain);
            MediaCodecSelector codecs = hardwareMain ? hardwareOnly(MediaCodecSelector.DEFAULT)
                    : decoderSelector(softwareFloatingH264, MediaCodecSelector.DEFAULT, softwareFirst(MediaCodecSelector.DEFAULT));
            DefaultRenderersFactory renderers = hardwareMain
                    ? new HardwareMainRenderersFactory(getContext()) : new DefaultRenderersFactory(getContext());
            renderers.setEnableDecoderFallback(true).setMediaCodecSelector(codecs);
            DefaultAllocator attemptAllocator = new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE);
            allocator = attemptAllocator;
            player = new ExoPlayer.Builder(getContext(), renderers)
                    .setTrackSelector(tracks)
                    .setLoadControl(new DefaultLoadControl.Builder().setAllocator(attemptAllocator).setBufferDurationsMs(1500, 5000, 500, 1000)
                            .setTargetBufferBytes(mainStream ? PlaybackLimits.ENLARGED_BUFFER_BYTES : PlaybackLimits.GRID_BUFFER_BYTES)
                            .setPrioritizeTimeOverSizeThresholds(false).build()).build();
            ExoPlayer attempt = player;
            player.setVideoFrameMetadataListener((presentationTimeUs, releaseTimeNs, format, mediaFormat) ->
                    attemptProgress.frame(SystemClock.elapsedRealtime()));
            player.setVolume(0f); surface.setPlayer(player);
            player.addListener(new Player.Listener() {
                @Override public void onVideoSizeChanged(androidx.media3.common.VideoSize size) {
                    if (!released && player == attempt) reportAspect(size.width, size.height, size.pixelWidthHeightRatio);
                }
                @Override public void onTracksChanged(Tracks available) {
                    if (released || player != attempt || !tvHardwareMain() || !unsupportedVideoTracks(available)) return;
                    mainFormatRejected = true;
                    for (Tracks.Group group : available.getGroups()) if (group.getType() == C.TRACK_TYPE_VIDEO && group.length > 0) {
                        Format format = group.getTrackFormat(0);
                        Log.w("FieldviewPlayer", diagnosticPrefix() + "event=main_unsupported mime=" + format.sampleMimeType
                                + " size=" + format.width + "x" + format.height);
                        break;
                    }
                    scheduleRetry("Main format unsupported on this TV");
                }
                @Override public void onPlayerError(PlaybackException error) {
                    if (released || player != attempt) return;
                    Format rejected = error instanceof ExoPlaybackException ? ((ExoPlaybackException) error).rendererFormat : null;
                    if (tvHardwareMain() && rejected != null && HardwareMainRenderersFactory.tooWide(rejected)) {
                        mainFormatRejected = true;
                        Log.w("FieldviewPlayer", diagnosticPrefix() + "event=main_unsupported mime=" + rejected.sampleMimeType
                                + " size=" + rejected.width + "x" + rejected.height);
                        scheduleRetry("Main format unsupported on this TV"); return;
                    }
                    String reason = PlaybackFailure.describe(error);
                    Log.w("FieldviewPlayer", diagnosticPrefix() + "event=failure code=" + error.getErrorCodeName() + " detail=" + PlaybackFailure.diagnostic(error) + "; " + reason);
                    scheduleRetry(reason);
                }
                @Override public void onRenderedFirstFrame() {
                    if (released || player != attempt) return;
                    firstFrame = true; attemptProgress.rendered(SystemClock.elapsedRealtime()); showStatus(liveStatus());
                    Format format = attempt.getVideoFormat();
                    Log.i("FieldviewPlayer", diagnosticPrefix() + "event=first_frame startup_ms=" + (SystemClock.elapsedRealtime() - attemptStartedAt)
                            + (format == null ? "" : " mime=" + format.sampleMimeType + " size=" + format.width + "x" + format.height + " fps=" + format.frameRate));
                    releasePreview();
                    finishStartup();
                }
                @Override public void onPlaybackStateChanged(int state) {
                    if (released || player != attempt) return;
                    if (state == Player.STATE_READY && firstFrame) showStatus(liveStatus());
                    else if (state == Player.STATE_BUFFERING) { showStatus(previewSession != null ? "Live" : "Buffering…"); armStallTimeout(); }
                    else if (state == Player.STATE_ENDED) scheduleRetry("Camera stream ended");
                }
            });
            player.addAnalyticsListener(new AnalyticsListener() {
                @Override public void onVideoDecoderInitialized(EventTime eventTime, String decoderName, long initializedAtMs, long initializationDurationMs) {
                    if (!released && player == attempt) Log.i("FieldviewPlayer", diagnosticPrefix() + "event=decoder name=" + decoderName.replaceAll("[^A-Za-z0-9_.-]", "_"));
                }
            });
            RtspMediaSource source = new RtspMediaSource.Factory().setForceUseRtpTcp(true).setTimeoutMs(10_000)
                    .setSocketFactory(new TimedSocketFactory(5_000))
                    .createMediaSource(MediaItem.fromUri(activeUri));
            player.setMediaSource(source); player.prepare(); player.play();
        } catch (RuntimeException error) { scheduleRetry(PlaybackFailure.describe(error)); }
    }

    /** TV dense-grid tiles and the phone's pinned main share the compile-time native boundary, GridVideoFactory. */
    private void startNativeGrid(VideoProgress attemptProgress, boolean phoneMain) {
        GridVideoSession attempt = GridVideoFactory.create(getContext()); gridSession = attempt;
        // One video surface at a time; releasePrimaryPlayer restores the Media3 view.
        if (phoneMain && surface != null) surface.setVisibility(View.GONE);
        addView(attempt.view(), 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        if (zoom != 1f) transformVideo(attempt.view());
        Log.i("FieldviewPlayer", diagnosticPrefix() + "event=decoder name=" + (phoneMain ? "libvlc policy=hardware_first" : "libvlc.avcodec.software"));
        boolean[] advancing = {false};
        attempt.start(activeUri, new GridVideoSession.Listener() {
            @Override public void frames() {
                if (!released && previewSession == attempt) { attemptProgress.frame(SystemClock.elapsedRealtime()); return; }
                if (released || gridSession != attempt) return;
                long now = SystemClock.elapsedRealtime(); attemptProgress.frame(now);
                if (!firstFrame) {
                    firstFrame = true; attemptProgress.rendered(now); finishStartup(); showStatus(liveStatus());
                    GridVideoSession.Stats stats = attempt.stats();
                    reportAspect(stats.width, stats.height, stats.pixelWidthHeightRatio);
                    Log.i("FieldviewPlayer", diagnosticPrefix() + "event=first_frame startup_ms=" + (now - attemptStartedAt)
                            + (phoneMain ? " engine=libvlc" : " mime=video/native") + " size=" + stats.width + "x" + stats.height);
                } else if (phoneMain && !advancing[0]) {
                    // One record that pictures keep advancing after the first; later health lines repeat these counters.
                    advancing[0] = true; GridVideoSession.Stats stats = attempt.stats();
                    Log.i("FieldviewPlayer", diagnosticPrefix() + "event=frames_advancing engine=libvlc displayed=" + stats.displayed
                            + " decoded=" + stats.decoded + " lost=" + stats.lost + " size=" + stats.width + "x" + stats.height);
                }
            }
            @Override public void error() {
                if (!released && previewSession == attempt) { releasePreview(); showStatus("Connecting…"); return; }
                if (released || gridSession != attempt) return;
                Log.w("FieldviewPlayer", diagnosticPrefix() + "event=failure code=" + (phoneMain ? "LIBVLC_STREAM_ERROR" : "NATIVE_STREAM_ERROR"));
                scheduleRetry("Camera stream unavailable");
            }
        });
    }

    private void armStallTimeout() {
        if (stalled != null) return;
        stalled = new Runnable() {
            @Override public void run() {
                if (stalled != this) return;
                if (released || (player == null && gridSession == null) || progress == null) { stalled = null; return; }
                long now = SystemClock.elapsedRealtime();
                if (progress.stalled(now)) {
                    stalled = null;
                    logVideoHealth(now, true);
                    if (++stalledRecoveries >= 3) {
                        releasePlayer(); showStatus("Video not recovering · reopen camera");
                        Log.w("FieldviewPlayer", diagnosticPrefix() + "event=stopped unrecovered_stalls=3");
                        return;
                    }
                    scheduleRetry(firstFrame ? "Video stopped updating" : "No video received · check network and stream settings");
                    return;
                }
                if (progress.sustained()) { attempts = 0; stalledRecoveries = 0; }
                if (now >= nextHealthLogAt) { logVideoHealth(now, false); nextHealthLogAt = now + 30_000; }
                handler.postDelayed(this, Math.min(5_000, VideoProgress.TIMEOUT_MS - progress.silentFor(now)));
            }
        };
        handler.postDelayed(stalled, 5_000);
    }
    private void logVideoHealth(long now, boolean stalledVideo) {
        ExoPlayer current = player; VideoProgress currentProgress = progress;
        if (gridSession != null && currentProgress != null) {
            GridVideoSession.Stats stats = gridSession.stats();
            reportAspect(stats.width, stats.height, stats.pixelWidthHeightRatio);
            String message = diagnosticPrefix() + (stalledVideo ? "event=stalled " : "event=health ") + (nativeGrid ? "" : "engine=libvlc ")
                    + "video_width=" + stats.width + " video_height=" + stats.height + " "
                    + "frame_silent_ms=" + currentProgress.silentFor(now) + " native_decoded=" + stats.decoded
                    + " native_displayed=" + stats.displayed + " native_lost=" + stats.lost + " native_input_bytes=" + stats.bytes
                    + " native_demux_bytes=" + stats.demuxBytes + " native_media_time_ms=" + stats.mediaTimeMs
                    + " tile_width=" + getWidth() + " tile_height=" + getHeight() + " tile_resizes=" + nativeTileResizes
                    + " native_heap_kib=" + android.os.Debug.getNativeHeapAllocatedSize() / 1024;
            if (stalledVideo) Log.w("FieldviewPlayer", message); else Log.i("FieldviewPlayer", message);
            return;
        }
        if (current == null || currentProgress == null) return;
        DecoderCounters counters = current.getVideoDecoderCounters();
        if (counters != null) counters.ensureUpdated();
        Runtime runtime = Runtime.getRuntime();
        String message = diagnosticPrefix() + (stalledVideo ? "event=stalled " : "event=health ")
                + "state=" + current.getPlaybackState() + ", frame_silent_ms=" + currentProgress.silentFor(now)
                + ", buffered_ms=" + current.getTotalBufferedDuration()
                + ", submitted_frames=" + currentProgress.submittedFrames()
                + ", decoder_rendered=" + (counters == null ? -1 : counters.renderedOutputBufferCount)
                + ", dropped=" + (counters == null ? -1 : counters.droppedBufferCount)
                + ", media_buffer_bytes=" + (allocator == null ? -1 : allocator.getTotalBytesAllocated())
                + ", native_heap_kib=" + android.os.Debug.getNativeHeapAllocatedSize() / 1024
                + ", java_heap_kib=" + (runtime.totalMemory() - runtime.freeMemory()) / 1024;
        if (stalledVideo) Log.w("FieldviewPlayer", message); else Log.i("FieldviewPlayer", message);
    }
    private void clearStallTimeout() {
        if (stalled != null) { handler.removeCallbacks(stalled); stalled = null; }
    }

    private void scheduleRetry(String reason) {
        if (released || retry != null) return;
        clearStallTimeout();
        if (mainStream && fallbackUri != null) {
            Log.i("FieldviewPlayer", diagnosticPrefix() + "event=fallback code=MAIN_STREAM_UNAVAILABLE reason=" + reason);
            if (restorePreview()) { if (mainUnavailable != null) mainUnavailable.run(); return; }
            releasePlayer();
            activeUri = fallbackUri; fallbackActive = true; mainStream = false; attempts = 0; stalledRecoveries = 0;
            showStatus("Main unavailable · switching to grid stream…");
            retry = () -> { retry = null; queueStart(); };
            handler.postDelayed(retry, 250);
            if (mainUnavailable != null) mainUnavailable.run();
            return;
        }
        long delay = Math.min(30_000, 2_000L << Math.min(attempts++, 4));
        if (reason.startsWith("Video decoder")) delay = Math.max(10_000, delay);
        delay += java.util.concurrent.ThreadLocalRandom.current().nextLong(501);
        Log.i("FieldviewPlayer", diagnosticPrefix() + "event=retry delay_ms=" + delay + " reason=" + reason);
        showStatus(reason + " · retry in " + delay / 1000 + " s");
        releasePlayer();
        retry = () -> { retry = null; queueStart(); };
        handler.postDelayed(retry, delay);
    }

    private void disposePlayer() {
        clearStallTimeout();
        if (retry != null) { handler.removeCallbacks(retry); retry = null; }
        releasePlayer();
    }

    private void releasePlayer() {
        releasePreview();
        releasePrimaryPlayer();
    }

    private void releasePrimaryPlayer() {
        ExoPlayer previous = player;
        GridVideoSession previousGrid = gridSession; gridSession = null;
        StartupCoordinator.Ticket previousStartup = startup; startup = null;
        player = null;
        progress = null;
        allocator = null;
        if (previousGrid != null) {
            if (previousStartup != null) previousStartup.stopDeadline();
            Runnable unblocked = coordinator.beginRelease();
            Log.i("FieldviewPlayer", diagnosticPrefix() + "event=release");
            previousGrid.close(() -> { if (previousStartup != null) previousStartup.finish(); unblocked.run(); });
            removeView(previousGrid.view());
            if (surface != null) surface.setVisibility(View.VISIBLE);
            return;
        }
        // Detaching/releasing can dispatch callbacks. The old instance must already
        // be inactive so a callback cannot release it twice or schedule another retry.
        try {
            if (previous != null) {
                Log.i("FieldviewPlayer", diagnosticPrefix() + "event=release");
                try { if (surface != null) surface.setPlayer(null); } finally { previous.release(); }
            }
        } finally { if (previousStartup != null) previousStartup.finish(); }
    }

    public void release() { released = true; disposePlayer(); resetZoom(); activeUri = null; requestedUri = null; fallbackUri = null; fallbackActive = false; mainUnavailable = null; mainFormatRejected = false; mainSuppressed = false; mainSuppressedFormatUnsupported = false; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
