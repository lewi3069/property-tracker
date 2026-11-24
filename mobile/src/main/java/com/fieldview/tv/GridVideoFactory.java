package com.fieldview.tv;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.IMedia;
import org.videolan.libvlc.util.VLCVideoLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Phone native backend: LibVLC, hardware decoding first, for the one manually pinned fullscreen RTSP main
 * stream. Every other phone view stays on Media3. CameraTile owns admission, fallback, retries and release.
 */
final class GridVideoFactory {
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final ExecutorService retirements = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "PropertyTracker-VideoRelease"); thread.setDaemon(true); return thread;
    });
    private GridVideoFactory() { }
    static GridVideoSession create(Context context) { return new Session(context); }
    private static void retirementFailed(String stage) {
        android.util.Log.w("FieldviewPlayer", "event=retirement_failed engine=libvlc stage=" + stage);
    }

    private static final class Session implements GridVideoSession {
        private final VLCVideoLayout layout;
        private final LibVLC runtime;
        private MediaPlayer player;
        private Media media;
        private Listener listener;
        private boolean closed, sawFrames;
        private Stats latest = new Stats(0, 0, 0, 0, 0, 0);
        private final Runnable poll = new Runnable() {
            @Override public void run() {
                if (closed || media == null) return;
                IMedia.Stats sample = media.getStats();
                if (sample != null) {
                    // Query the track only after pictures are displayed: LibVLC caches an early empty track list.
                    Media.VideoTrack track = sample.displayedPictures > 0 ? player.getCurrentVideoTrack() : null;
                    Stats before = latest;
                    float pixelRatio = track != null && track.sarNum > 0 && track.sarDen > 0
                            ? (float) track.sarNum / track.sarDen : 1f;
                    latest = new Stats(sample.decodedVideo, sample.displayedPictures, sample.lostPictures, sample.readBytes,
                            track == null ? 0 : track.width, track == null ? 0 : track.height,
                            sample.demuxReadBytes, player.getTime(), pixelRatio);
                    // Health follows displayed pictures, not decoder input.
                    if (latest.displayed > before.displayed) {
                        sawFrames = true;
                        if (listener != null) listener.frames();
                    }
                }
                // Responsive startup, then a slow health cadence; no per-frame UI work.
                if (!closed) main.postDelayed(this, sawFrames ? 2_000 : 500);
            }
        };
        Session(Context context) {
            layout = new VLCVideoLayout(context); layout.setFocusable(false);
            runtime = new LibVLC(context.getApplicationContext(), new ArrayList<>(Arrays.asList(
                    "--quiet", "--no-audio", "--no-video-title-show", "--no-osd", "--rtsp-tcp", "--network-caching=500")));
        }
        @Override public View view() { return layout; }
        @Override public Stats stats() { return latest; }
        @Override public void start(String uri, Listener callbacks) {
            listener = callbacks;
            player = new MediaPlayer(runtime);
            player.setEventListener(event -> {
                if (closed || listener == null) return;
                if (event.type == MediaPlayer.Event.EncounteredError || event.type == MediaPlayer.Event.EndReached) listener.error();
            });
            // A SurfaceView output lets the hardware decoder render directly.
            player.attachViews(layout, null, false, false);
            media = new Media(runtime, android.net.Uri.parse(uri));
            // Hardware first, not forced: LibVLC may still choose software, so logs state the policy, not the decoder.
            media.setHWDecoderEnabled(true, false);
            media.addOption(":network-caching=500"); media.addOption(":rtsp-tcp"); media.addOption(":no-audio");
            player.setMedia(media); player.setVolume(0); player.play(); main.post(poll);
        }
        /** Detach now; stop and release player, media and runtime off the UI thread, then complete on it. */
        @Override public void close(Runnable complete) {
            if (closed) { complete.run(); return; }
            closed = true; listener = null; main.removeCallbacks(poll);
            MediaPlayer oldPlayer = player; Media oldMedia = media; player = null; media = null;
            if (oldPlayer != null) {
                oldPlayer.setEventListener(null);
                try { oldPlayer.detachViews(); } catch (RuntimeException ignored) { retirementFailed("views"); }
            }
            retirements.execute(() -> {
                try {
                    if (oldPlayer != null) try { oldPlayer.stop(); } finally { oldPlayer.release(); }
                } catch (RuntimeException ignored) {
                    retirementFailed("player");
                } finally {
                    try { if (oldMedia != null) oldMedia.release(); }
                    catch (RuntimeException ignored) { retirementFailed("media"); }
                    finally {
                        try { runtime.release(); }
                        catch (RuntimeException ignored) { retirementFailed("runtime"); }
                        finally { main.post(complete); }
                    }
                }
            });
        }
    }
}
