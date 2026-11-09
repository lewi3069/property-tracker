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
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
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
    private final PlayerView surface;
    private final TextView status;
    private final boolean floating;
    private final boolean texture;
    private StartupCoordinator coordinator = StartupCoordinator.shared();
    private GridVideoSession gridSession;
    private StartupCoordinator.Ticket startup;
    private String cameraLabel;
    private final String session = java.util.UUID.randomUUID().toString().substring(0, 8);
    private int generation;
    private long attemptStartedAt;
    private boolean mainStream;
    private final boolean nativeGrid;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private String activeUri;
    private Runnable retry;
    private Runnable stalled;
    private VideoProgress progress;
    private DefaultAllocator allocator;
    private long nextHealthLogAt;
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
        if (this.nativeGrid) surface = null;
        else {
            surface = floating || texture ? (PlayerView) LayoutInflater.from(context).inflate(texture ? R.layout.grid_texture_player : R.layout.floating_player, this, false) : new PlayerView(context);
            surface.setUseController(false);
            surface.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            surface.setFocusable(false); surface.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
            addView(surface, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
        status = new TextView(context); status.setTextColor(0xFFE3EFDB); status.setTextSize(12);
        status.setMaxLines(3);
        status.setPadding(dp(10), dp(5), dp(10), dp(5)); status.setBackgroundColor(0xB0000000);
        addView(status, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START));
        setOnFocusChangeListener((view, focus) -> updateBorder());
        updateBorder();
    }

    public void setFeatured(boolean value) { featured = value; if (startup != null) startup.prioritize(value); updateBorder(); }
    boolean usesTexture() { return texture; }
    boolean usesNativeGrid() { return nativeGrid; }
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
        if (!released && uri.equals(activeUri) && (player != null || gridSession != null || retry != null || startup != null)) return;
        disposePlayer();
        mainStream = useMain;
        activeUri = uri; released = false; attempts = 0; stalledRecoveries = 0;
        queueStart();
    }
    private void queueStart() {
        if (released) return;
        if (coordinator == null) { startPlayer(); return; }
        showStatus("Waiting to connect…");
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

    private void startPlayer() {
        if (released) return;
        generation++; attemptStartedAt = SystemClock.elapsedRealtime();
        Log.i("FieldviewPlayer", diagnosticPrefix() + "event=start renderer=" + (texture ? "texture" : "surface") + " engine=" + (nativeGrid ? "native" : "media3"));
        firstFrame = false;
        VideoProgress attemptProgress = new VideoProgress(SystemClock.elapsedRealtime());
        progress = attemptProgress;
        nextHealthLogAt = SystemClock.elapsedRealtime() + 30_000;
        showStatus("Connecting…");
        armStallTimeout();
        if (nativeGrid) {
            try { startNativeGrid(attemptProgress); }
            catch (RuntimeException | LinkageError error) { scheduleRetry("Native grid unavailable · try the standard layout"); }
            return;
        }
        try {
            DefaultTrackSelector tracks = new DefaultTrackSelector(getContext());
            tracks.setParameters(tracks.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true));
            DefaultRenderersFactory renderers = new DefaultRenderersFactory(getContext()).setEnableDecoderFallback(true)
                    .setMediaCodecSelector(decoderSelector(softwareFloatingH264, MediaCodecSelector.DEFAULT, softwareFirst(MediaCodecSelector.DEFAULT)));
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
                @Override public void onPlayerError(PlaybackException error) {
                    if (released || player != attempt) return;
                    String reason = PlaybackFailure.describe(error);
                    Log.w("FieldviewPlayer", diagnosticPrefix() + "event=failure code=" + error.getErrorCodeName() + " detail=" + PlaybackFailure.diagnostic(error) + "; " + reason);
                    scheduleRetry(reason);
                }
                @Override public void onRenderedFirstFrame() {
                    if (released || player != attempt) return;
                    firstFrame = true; attemptProgress.rendered(SystemClock.elapsedRealtime()); showStatus("Live");
                    finishStartup();
                    Format format = attempt.getVideoFormat();
                    Log.i("FieldviewPlayer", diagnosticPrefix() + "event=first_frame startup_ms=" + (SystemClock.elapsedRealtime() - attemptStartedAt)
                            + (format == null ? "" : " mime=" + format.sampleMimeType + " size=" + format.width + "x" + format.height + " fps=" + format.frameRate));
                }
                @Override public void onPlaybackStateChanged(int state) {
                    if (released || player != attempt) return;
                    if (state == Player.STATE_READY && firstFrame) showStatus("Live");
                    else if (state == Player.STATE_BUFFERING) { showStatus("Buffering…"); armStallTimeout(); }
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

    private void startNativeGrid(VideoProgress attemptProgress) {
        GridVideoSession attempt = GridVideoFactory.create(getContext()); gridSession = attempt;
        addView(attempt.view(), 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        Log.i("FieldviewPlayer", diagnosticPrefix() + "event=decoder name=libvlc.avcodec.software");
        attempt.start(activeUri, new GridVideoSession.Listener() {
            @Override public void frames() {
                if (released || gridSession != attempt) return;
                long now = SystemClock.elapsedRealtime(); attemptProgress.frame(now);
                if (!firstFrame) {
                    firstFrame = true; attemptProgress.rendered(now); finishStartup(); showStatus("Live");
                    GridVideoSession.Stats stats = attempt.stats();
                    Log.i("FieldviewPlayer", diagnosticPrefix() + "event=first_frame startup_ms=" + (now - attemptStartedAt)
                            + " mime=video/native size=" + stats.width + "x" + stats.height);
                }
            }
            @Override public void error() {
                if (released || gridSession != attempt) return;
                Log.w("FieldviewPlayer", diagnosticPrefix() + "event=failure code=NATIVE_STREAM_ERROR");
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
            String message = diagnosticPrefix() + (stalledVideo ? "event=stalled " : "event=health ")
                    + "frame_silent_ms=" + currentProgress.silentFor(now) + " native_decoded=" + stats.decoded
                    + " native_displayed=" + stats.displayed + " native_lost=" + stats.lost + " native_input_bytes=" + stats.bytes
                    + " native_demux_bytes=" + stats.demuxBytes + " native_media_time_ms=" + stats.mediaTimeMs
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

    public void release() { released = true; disposePlayer(); activeUri = null; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
