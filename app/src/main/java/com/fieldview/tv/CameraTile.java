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
    private final TextView title, status;
    private final boolean floating;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private String activeUri;
    private Runnable retry;
    private Runnable stalled;
    private VideoProgress progress;
    private DefaultAllocator allocator;
    private long nextHealthLogAt;
    private int attempts, stalledRecoveries;
    private boolean released, featured, firstFrame, fullscreen, softwareFloatingH264;

    public CameraTile(Context context, String name) {
        this(context, name, false);
    }

    public CameraTile(Context context, String name, boolean floating) {
        super(context);
        this.floating = floating;
        setId(View.generateViewId()); setFocusable(true); setClickable(true);
        setContentDescription("Enlarge " + name);
        setPadding(dp(3), dp(3), dp(3), dp(3));
        surface = floating ? (PlayerView) LayoutInflater.from(context).inflate(R.layout.floating_player, this, false) : new PlayerView(context);
        surface.setUseController(false);
        surface.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        surface.setFocusable(false); surface.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        addView(surface, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        title = new TextView(context); title.setText(name); title.setTextColor(Color.WHITE);
        title.setTextSize(14); title.setMaxLines(2); title.setPadding(dp(10), dp(6), dp(10), dp(6));
        title.setBackgroundColor(0xB0000000);
        title.setVisibility(floating ? View.GONE : View.VISIBLE);
        addView(title, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP));
        status = new TextView(context); status.setTextColor(0xFFE3EFDB); status.setTextSize(12);
        status.setMaxLines(3);
        status.setPadding(dp(10), dp(5), dp(10), dp(5)); status.setBackgroundColor(0xB0000000);
        addView(status, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START));
        setOnFocusChangeListener((view, focus) -> updateBorder());
        updateBorder();
    }

    public void setFeatured(boolean value) { featured = value; updateBorder(); }
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
        if (fullscreen == value) return;
        fullscreen = value;
        title.setVisibility(value || floating ? View.GONE : View.VISIBLE);
        setPadding(value ? 0 : dp(3), value ? 0 : dp(3), value ? 0 : dp(3), value ? 0 : dp(3));
        updateStatusVisibility(); updateBorder();
    }
    private void showStatus(String value) { status.setText(value); updateStatusVisibility(); }
    private void updateStatusVisibility() { status.setVisibility(fullscreen && "Live".contentEquals(status.getText()) ? View.GONE : View.VISIBLE); }
    private void updateBorder() {
        if (fullscreen) { setBackgroundColor(Color.BLACK); return; }
        GradientDrawable background = new GradientDrawable(); background.setColor(Color.BLACK);
        background.setStroke(dp(hasFocus() ? 3 : 1), hasFocus() ? 0xFFD9F2BD : featured ? 0xFF9BBF79 : 0xFF28322D);
        setBackground(background);
    }

    public void play(String uri) {
        if (!released && uri.equals(activeUri) && (player != null || retry != null)) return;
        disposePlayer();
        activeUri = uri; released = false; attempts = 0; stalledRecoveries = 0;
        startPlayer();
    }

    private void startPlayer() {
        if (released) return;
        firstFrame = false;
        VideoProgress attemptProgress = new VideoProgress(SystemClock.elapsedRealtime());
        progress = attemptProgress;
        nextHealthLogAt = SystemClock.elapsedRealtime() + 30_000;
        showStatus("Connecting…");
        armStallTimeout();
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
                            .setTargetBufferBytes(featured ? PlaybackLimits.ENLARGED_BUFFER_BYTES : PlaybackLimits.GRID_BUFFER_BYTES)
                            .setPrioritizeTimeOverSizeThresholds(false).build()).build();
            ExoPlayer attempt = player;
            player.setVideoFrameMetadataListener((presentationTimeUs, releaseTimeNs, format, mediaFormat) ->
                    attemptProgress.frame(SystemClock.elapsedRealtime()));
            player.setVolume(0f); surface.setPlayer(player);
            player.addListener(new Player.Listener() {
                @Override public void onPlayerError(PlaybackException error) {
                    if (released || player != attempt) return;
                    String reason = PlaybackFailure.describe(error);
                    Log.w("FieldviewPlayer", "Playback failed: " + error.getErrorCodeName() + "; " + reason);
                    scheduleRetry(reason);
                }
                @Override public void onRenderedFirstFrame() {
                    if (released || player != attempt) return;
                    firstFrame = true; attemptProgress.rendered(SystemClock.elapsedRealtime()); showStatus("Live");
                    Format format = attempt.getVideoFormat();
                    Log.i("FieldviewPlayer", "First video frame rendered" + (format == null ? ""
                            : ": " + format.sampleMimeType + " " + format.width + "x" + format.height));
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
                    if (!released && player == attempt) Log.i("FieldviewPlayer", (floating ? "Floating" : "Wall") + " video decoder: " + decoderName);
                }
            });
            RtspMediaSource source = new RtspMediaSource.Factory().setForceUseRtpTcp(true).setTimeoutMs(10_000)
                    .setSocketFactory(new TimedSocketFactory(5_000))
                    .createMediaSource(MediaItem.fromUri(activeUri));
            player.setMediaSource(source); player.prepare(); player.play();
        } catch (RuntimeException error) { scheduleRetry(PlaybackFailure.describe(error)); }
    }

    private void armStallTimeout() {
        if (stalled != null) return;
        stalled = new Runnable() {
            @Override public void run() {
                if (stalled != this) return;
                if (released || player == null || progress == null) { stalled = null; return; }
                long now = SystemClock.elapsedRealtime();
                if (progress.stalled(now)) {
                    stalled = null;
                    logVideoHealth(now, true);
                    if (++stalledRecoveries >= 3) {
                        releasePlayer(); showStatus("Video not recovering · reopen camera");
                        Log.w("FieldviewPlayer", "Stopped after three unrecovered video stalls");
                        return;
                    }
                    scheduleRetry(firstFrame ? "Video stopped updating" : "No video received · check network and stream settings");
                    return;
                }
                if (progress.sustained()) { attempts = 0; stalledRecoveries = 0; }
                if (floating && now >= nextHealthLogAt) { logVideoHealth(now, false); nextHealthLogAt = now + 30_000; }
                handler.postDelayed(this, Math.min(5_000, VideoProgress.TIMEOUT_MS - progress.silentFor(now)));
            }
        };
        handler.postDelayed(stalled, 5_000);
    }
    private void logVideoHealth(long now, boolean stalledVideo) {
        ExoPlayer current = player; VideoProgress currentProgress = progress;
        if (current == null || currentProgress == null) return;
        DecoderCounters counters = current.getVideoDecoderCounters();
        if (counters != null) counters.ensureUpdated();
        Runtime runtime = Runtime.getRuntime();
        String message = (stalledVideo ? "Video stalled: " : "Video health: ")
                + "state=" + current.getPlaybackState() + ", frame_silent_ms=" + currentProgress.silentFor(now)
                + ", buffered_ms=" + current.getTotalBufferedDuration()
                + ", rendered=" + (counters == null ? -1 : counters.renderedOutputBufferCount)
                + ", dropped=" + (counters == null ? -1 : counters.droppedBufferCount)
                + ", media_buffer_bytes=" + (allocator == null ? -1 : allocator.getTotalBytesAllocated())
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
        showStatus(reason + " · retry in " + delay / 1000 + " s");
        releasePlayer();
        retry = () -> { retry = null; startPlayer(); };
        handler.postDelayed(retry, delay);
    }

    private void disposePlayer() {
        clearStallTimeout();
        if (retry != null) { handler.removeCallbacks(retry); retry = null; }
        releasePlayer();
    }

    private void releasePlayer() {
        ExoPlayer previous = player;
        player = null;
        progress = null;
        allocator = null;
        // Detaching/releasing can dispatch callbacks. The old instance must already
        // be inactive so a callback cannot release it twice or schedule another retry.
        if (previous != null) { surface.setPlayer(null); previous.release(); }
    }

    public void release() { released = true; disposePlayer(); activeUri = null; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
