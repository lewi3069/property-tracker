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
