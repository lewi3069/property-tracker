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

/** Shared native runtime, one silent software-decoded RTSP player per dense-grid tile. */
final class GridVideoFactory {
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final ExecutorService retirements=Executors.newSingleThreadExecutor(task -> {
        Thread thread=new Thread(task,"PropertyTracker-VideoRelease");thread.setDaemon(true);return thread;
    });
    private static final SharedVideoRuntime<LibVLC> runtimes = new SharedVideoRuntime<>(
            retirements, task -> main.post(task), LibVLC::release);
    static GridVideoSession create(Context context) { return new Session(context); }
    private static LibVLC acquire(Context context) {
        return runtimes.acquire(() -> new LibVLC(context.getApplicationContext(),new ArrayList<>(Arrays.asList(
                "--quiet","--no-audio","--no-video-title-show","--no-osd","--rtsp-tcp",
                "--network-caching=500","--avcodec-threads=1","--android-display-chroma=RV32"))));
    }
    private static final class Session implements GridVideoSession {
        private final VLCVideoLayout layout;
        private final LibVLC runtime;
        private MediaPlayer player;
        private Media media;
        private Listener listener;
        private boolean closed, sawFrames;
        private Stats latest=new Stats(0,0,0,0,0,0);
        private final Runnable poll=new Runnable() {
            @Override public void run() {
                if(closed||media==null)return;
                IMedia.Stats sample=media.getStats();
                if(sample!=null) {
                    Media.VideoTrack track=player.getCurrentVideoTrack();
                    Stats before=latest;
                    latest=new Stats(sample.decodedVideo,sample.displayedPictures,sample.lostPictures,sample.readBytes,
                            track==null?0:track.width,track==null?0:track.height,
                            sample.demuxReadBytes,player.getTime());
                    if(latest.displayed>before.displayed) {
                        sawFrames=true;
                        if(listener!=null)listener.frames();
                    }
                }
                // Keep staged startup responsive; steady health needs no per-frame UI polling.
                if(!closed)main.postDelayed(this,sawFrames?2_000:500);
            }
        };
        Session(Context context) { layout=new VLCVideoLayout(context);layout.setFocusable(false);runtime=acquire(context); }
        public View view() { return layout; }
        public Stats stats() { return latest; }
        public void start(String uri,Listener callbacks) {
            listener=callbacks;
            player=new MediaPlayer(runtime);
            player.setEventListener(event -> {
                if(closed||listener==null)return;
                if(event.type==MediaPlayer.Event.EncounteredError||event.type==MediaPlayer.Event.EndReached)listener.error();
            });
            player.attachViews(layout,null,false,true);
            media=new Media(runtime,android.net.Uri.parse(uri));
            media.setHWDecoderEnabled(false,false);media.addOption(":codec=avcodec");
            media.addOption(":network-caching=500");media.addOption(":rtsp-tcp");media.addOption(":no-audio");
            player.setMedia(media);player.setVolume(0);player.play();main.post(poll);
        }
        public void close(Runnable complete) {
            if(closed) { complete.run();return; }
            closed=true;listener=null;main.removeCallbacks(poll);
            MediaPlayer oldPlayer=player;Media oldMedia=media;player=null;media=null;
            if(oldPlayer!=null) { oldPlayer.setEventListener(null);try { oldPlayer.detachViews(); } catch(RuntimeException ignored) { } }
            retirements.execute(() -> {
                try {
                    if(oldPlayer!=null)try { oldPlayer.stop(); } finally { oldPlayer.release(); }
                } catch(RuntimeException ignored) {
                    android.util.Log.w("FieldviewPlayer","event=retirement_failed");
                } finally {
                    try { if(oldMedia!=null)oldMedia.release(); }
                    finally { main.post(() -> runtimes.release(complete)); }
                }
            });
        }
    }
}
