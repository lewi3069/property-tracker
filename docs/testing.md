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

The ongoing frame-progress watchdog gap described in [the code review](CODE-REVIEW-2026-11-03.md) is relevant: the 0.1.11 watchdog monitors startup/buffering, but can miss a frozen stream that remains READY. This is a confirmed detection gap, not proof of the underlying freeze's cause. Version 0.1.12 adds continuing frame-submission monitoring; its limits are described below.

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

This is a successful observed coexistence run after the codec-service restart, with the same camera instance carried into Plex playback. H.264 here identifies Plex; subsequent decoder inspection identified the floating camera as HEVC. It does not resolve the earlier native H.265 failure, prove which stream triggered it, or validate transitions through the multi-camera overview. No playback-code fix was deployed for this retest. Longer sessions and targeted stream/transition checks remain necessary; sub-interval glitches are not ruled out by periodic frame samples.

## Later freeze and 0.1.12 compatibility trial

Playback froze again after the resumed comparison had ended, more than 30 minutes into coexistence. Both compositor timestamps stopped advancing while the camera app, Plex, codec service, and SurfaceFlinger retained their process IDs. No new matching crash was found. Camera resident memory had increased to approximately 98 MiB; this is an observation, not proof of which queue or component caused the freeze. Decoder/resource inspection identified the floating camera's secondary stream as HEVC at 640 × 480, while Plex used hardware H.264.

During a later viewer startup, SurfaceFlinger crashed in native graphics-buffer handling and the Android framework restarted. Subsequent players initialized decoders but produced no frames. The new 0.1.12 watchdog detected those 20-second stalls and retried; this did not restore the wedged platform. The device was intentionally rebooted before further comparison. Version 0.1.12 now stops after three unrecovered frame stalls rather than rebuilding players indefinitely.

Version 0.1.12 includes 61 passing host tests, zero lint errors, and 14 lint warnings. Its changes are:

- Monitor frame-submission progress after READY and require an actual rendered first frame during startup. Retry after 20 seconds without progress; reset retry history after 10 seconds of sustained frame progress.
- Stop automatic frame-stall recovery after three consecutive unrecovered stalls and ask the viewer to reopen the camera.
- Offer **Layout → Prefer software H.264 for floating video**, off by default. Only floating H.264 changes decoder preference; other formats and wall playback retain normal selection. Hardware fallback remains available if software initialization is unsupported.
- Log low-frequency frame counters, buffering, media-buffer allocation, and heap use without stream addresses or credentials.
- Allow an ADB setup launch with playback paused, so changing configuration or starting a single Float does not first initialize the overview: `am start -n com.fieldview.tv/.MainActivity --ez com.fieldview.tv.SETUP_ONLY true`.

These app changes cannot repair a wedged system codec or compositor. Frame metadata indicates renderer submission, which can continue even if the physical display has stopped; external compositor timestamps and operator confirmation remain necessary. The media-buffer metric excludes RTSP packet queues and native allocations.

The operator changed only the camera's secondary stream to H.264 for comparison. After reconnecting, 0.1.12 logged **OMX.google.h264.decoder** and **video/avc 640 × 480**; compositor timestamps advanced during the initial camera-only check. Plex initialized **OMX.MTK.VIDEO.DECODER.AVC**. The main recording stream was not changed.

The software-H.264 Float / hardware-H.264 Plex comparison completed **45 minutes** (2,701 seconds, 181 samples at approximately 15-second intervals):

- Camera and Plex compositor timestamps advanced at every sampled interval. Plex reported playing and the floating window was present at all 181 samples.
- Both apps, the codec service, and SurfaceFlinger retained their process IDs throughout. No intentional restart, installation, or app switch occurred during collection.
- No camera playback errors, watchdog stalls, or decoder-dropped frames were recorded.
- Camera resident memory ranged from approximately 53 to 67 MiB, ending at 53 MiB versus 66 MiB initially. Available system memory ranged from approximately 141 to 197 MiB.
- Reported camera buffering stayed between 943 and 1,032 ms. Media-buffer allocation was 192–256 KiB, and sampled Java heap use was approximately 13–17 MiB. These metrics exclude other RTSP queues and native allocations.
- The operator confirmed both pictures were normal at the beginning and again after 35 minutes. Both apps were left playing after collection ended.

This is one successful 45-minute run on the tested AFTMM, beyond the earlier failure window. Camera encoding, decoder preference, application version, and the platform restart differ from earlier runs; this comparison does not isolate the effect of each change or establish indefinite stability. It does not validate hardware-decoded H.264 Float, HEVC coexistence, other Plex encodings/resolutions, multi-camera transitions, or other devices. Periodic samples cannot rule out shorter glitches. Real watchdog recovery from a deliberate connection loss remains a separate check.

## Repeating a passive comparison

With ADB connected to the intended device, start the monitor and play a video normally:

```powershell
python .\scripts\monitor-firetv.py --serial DEVICE_ADDRESS:5555 --minutes 25
```

The monitor reads app/codec/compositor PIDs, process and global memory/swap, camera and Plex compositor timestamps, Plex playback state, foreground package, floating-window presence, and selected lifecycle logs. It never launches, stops, kills, installs, or reboots anything. Timestamped reports are written under ignored `.tools/diagnostics`. Use Ctrl+C to end early. Missing ADB responses must be distinguished from process death; use lifecycle evidence as well as samples. The named Plex surface is specific to the observed TV activity; a missing/differently named surface is not proof of a freeze.

Establish a Plex-only baseline, then repeat with one floating substream using the same video and playback settings. Record the time and affected component if a window disappears, playback freezes, an app exits, or the device restarts. Keep deliberate process-recovery tests separate and clearly announce any intentional interruption.

## Remaining validation

- Complete isolated foreground-service recovery and pending-restart cancellation on physical hardware.
- Longer Plex coexistence sessions, HEVC/high-resolution Plex, and other streaming apps.
- Additional Fire TV models and NVIDIA Shield.
- Real Home Assistant MQTT delivery and activity/location automations.
- Resolution-aware stream budgets and RTSP backlog behavior under sustained decoder/network pressure.

ADB video captures can be green even when the physical TV picture is normal on the tested device. A first-frame callback or screenshot alone is insufficient visual confirmation.
