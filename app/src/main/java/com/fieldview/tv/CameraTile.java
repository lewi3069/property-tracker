package com.fieldview.tv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

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
    private int attempts;
    private boolean released, featured, firstFrame, fullscreen;

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
        activeUri = uri; released = false; attempts = 0;
        startPlayer();
    }

    private void startPlayer() {
        if (released) return;
        firstFrame = false;
        showStatus("Connecting…");
        armStallTimeout();
        try {
            DefaultTrackSelector tracks = new DefaultTrackSelector(getContext());
            tracks.setParameters(tracks.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true));
            player = new ExoPlayer.Builder(getContext(), new DefaultRenderersFactory(getContext()).setEnableDecoderFallback(true))
                    .setTrackSelector(tracks)
                    .setLoadControl(new DefaultLoadControl.Builder().setBufferDurationsMs(1500, 5000, 500, 1000)
                            .setTargetBufferBytes(featured ? PlaybackLimits.ENLARGED_BUFFER_BYTES : PlaybackLimits.GRID_BUFFER_BYTES)
                            .setPrioritizeTimeOverSizeThresholds(false).build()).build();
            ExoPlayer attempt = player;
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
                    firstFrame = true; attempts = 0; clearStallTimeout(); showStatus("Live");
                    Log.i("FieldviewPlayer", "First video frame rendered");
                }
                @Override public void onPlaybackStateChanged(int state) {
                    if (released || player != attempt) return;
                    if (state == Player.STATE_READY && firstFrame) { attempts = 0; clearStallTimeout(); showStatus("Live"); }
                    else if (state == Player.STATE_BUFFERING) { showStatus("Buffering…"); armStallTimeout(); }
                    else if (state == Player.STATE_ENDED) scheduleRetry("Camera stream ended");
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
        stalled = () -> {
            stalled = null;
            if (!released) {
                Log.w("FieldviewPlayer", "Playback stalled for 20 seconds without a video frame / buffer recovery");
                scheduleRetry("No video received · check network and stream settings");
            }
        };
        handler.postDelayed(stalled, 20_000);
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
        // Detaching/releasing can dispatch callbacks. The old instance must already
        // be inactive so a callback cannot release it twice or schedule another retry.
        if (previous != null) { surface.setPlayer(null); previous.release(); }
    }

    public void release() { released = true; disposePlayer(); activeUri = null; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
