# Playback validation

## November 3, 2026 comparison

Fieldview 0.1.10 (the development name preceding Property Tracker) was tested on a Fire TV Stick 4K 2018, model AFTMM, Fire OS 6 / Android API 25.

| Phase | Duration | Observed result |
| --- | --- | --- |
| Plex alone | 5 minutes | Same Plex process throughout; foreground at every sample |
| Plex plus one Amcrest IP2M-853E substream in Float | 15 minutes | Same Plex and camera-app processes; floating window present in all 60 samples |

Plex used the hardware AVC/H.264 decoder. Its source resolution and direct-play/transcoding status were not verified. There was a brief buffering state after switching apps to start Float; Plex subsequently reported playing. The operator confirmed that both pictures looked normal. Camera compositor timestamps continued advancing through the final sample. No intentional process termination, reboot, or installation occurred during this comparison; no process death, crash, or ANR was recorded for either app.

Available system memory varied from approximately 113 to 214 MiB during the combined phase. A background Amazon process became active during the run. These observations do not prove the cause of earlier terminations or establish longer-term stability, HEVC Plex coexistence, multiple-camera capacity, or compatibility with other hardware.

The 0.1.11 update changes the visible app name to Property Tracker and hides the camera-name bar only in Float. It preserves package identity, signing identity, stored camera data, stream selection, and the video surface. Its host checks include 49 unit/Robolectric tests and Android lint; the floating-title visibility assertion covers fullscreen styling changes as well.

## Follow-up freeze observed in 0.1.11

After installing 0.1.11 and restoring Float, the operator confirmed the name bar was gone but reported that playback froze. The camera process remained alive while its compositor presentation timestamps stopped advancing. No matching Java/native crash report was found. Property Tracker was then intentionally force-stopped to isolate the camera playback; Plex's process was left running. The precise cause and recovery behavior remain unverified. This follow-up means the successful 15-minute comparison above must not be treated as proof of reliable sustained Plex coexistence.

The ongoing frame-progress watchdog gap described in [the code review](CODE-REVIEW-2026-11-03.md) is relevant: the current watchdog monitors startup/buffering, but can miss a frozen stream that remains READY. This is a confirmed detection gap, not proof of the underlying freeze's cause. Investigation should separate decoder/surface contention from RTSP backlog and network behavior before selecting a fix.

## Isolation follow-up

A later camera-only run kept the same Amcrest substream floating over a static Fire TV Settings screen for five minutes. Camera presentation timestamps advanced at every 15-second sample, the app process remained running, and sampled resident memory decreased rather than growing steadily. Plex had no active video session during this phase.

During reopening of the viewer after the previous freeze, the shared Android `media.codec` process crashed and restarted. Its native tombstone reports SIGSEGV in the MediaTek H.265 decoder (`libvcodecdrv.so`, including `Vdec_Drv_H265_decodeEx`). This establishes a platform decoder failure in this session. The crash timestamp is later than the reported freeze; it does not identify which client/stream caused it or prove the earlier freeze had the same cause. The isolated run began after the codec-service restart, which also limits direct comparison with the earlier run.

The operator reported that the TV interface remained responsive and manually restarted the video. This was a video-playback failure rather than a confirmed complete device/UI lockup. Further comparison should leave the floating camera running while starting Plex, identify the active codec paths, and avoid returning through the multi-camera overview during that phase.

## Resumed Plex comparison

With the isolated camera kept running, the operator unlocked Plex and resumed a video. Property Tracker 0.1.11 then completed a 15-minute comparison (901 seconds, 61 samples at approximately 15-second intervals):

- Both the camera and Plex compositor presentation timestamps advanced at every interval.
- The camera, Plex, shared codec service, and SurfaceFlinger each retained the same process ID throughout.
- Plex was foreground and reported playing at every sample; the floating window remained present.
- Camera resident memory ranged from approximately 29 to 37 MiB; available system memory ranged from approximately 112 to 180 MiB.
- Plex used the hardware AVC/H.264 decoder. The operator confirmed that both pictures looked normal.
- No intentional app restart, installation, or reboot occurred during the phase. Both apps were left playing afterward.

This is a successful observed H.264 coexistence run after the codec-service restart, with the same camera instance carried into Plex playback. It does not resolve the earlier native H.265 failure, prove which stream triggered it, or validate transitions through the multi-camera overview. No playback-code fix was deployed for this retest. Longer sessions and targeted stream/transition checks remain necessary; sub-interval glitches are not ruled out by periodic frame samples.

## Repeating a passive comparison

With ADB connected to the intended device, start the monitor and play a video normally:

```powershell
python .\scripts\monitor-firetv.py --serial DEVICE_ADDRESS:5555 --minutes 25
```

The monitor reads PIDs, global memory/swap, foreground package, floating-window presence, and selected lifecycle logs. It never launches, stops, kills, installs, or reboots anything. Timestamped reports are written under ignored `.tools/diagnostics`. Use Ctrl+C to end early. Missing ADB responses must be distinguished from process death; use lifecycle evidence as well as samples.

Establish a Plex-only baseline, then repeat with one floating substream using the same video and playback settings. Record the time and affected component if a window disappears, playback freezes, an app exits, or the device restarts. Keep deliberate process-recovery tests separate and clearly announce any intentional interruption.

## Remaining validation

- Complete isolated foreground-service recovery and pending-restart cancellation on physical hardware.
- Longer Plex coexistence sessions, HEVC/high-resolution Plex, and other streaming apps.
- Additional Fire TV models and NVIDIA Shield.
- Real Home Assistant MQTT delivery and activity/location automations.
- Resolution-aware stream budgets and RTSP backlog behavior under sustained decoder/network pressure.

ADB video captures can be green even when the physical TV picture is normal on the tested device. A first-frame callback or screenshot alone is insufficient visual confirmation.
