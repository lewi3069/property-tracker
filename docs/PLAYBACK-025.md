# Version 0.2.5: opt-in dense TV grids

The TV app can show 13 live cameras as a large 2-by-2 center feed surrounded by 12 tiles on a 4-by-4 wall. Choose the default center camera in Layout. Auto can move a detected camera into the center and restore the default afterward; Grid holds the default ordering. All configured, enabled cameras remain available to Auto and View, including cameras outside the current wall.

Enable **Layout → Experimental larger layouts**, choose the count and save. Grid compatibility is required and enabled with this option. The request is saved separately from the device's effective compatibility budget. Normal AFTKM installations still default to four feeds; AFTT, AFTMM and low-memory safeguards remain. The trial is TV-only and requires API 26 or newer. Disable the larger-layout option to return to the normal decoder path.

## Video path and lifecycle

Only the dense multi-camera wall uses the unmodified VideoLAN LibVLC Android 3.7.7 SDK, with software decoding, TextureView output, silent RTSP-over-TCP sessions, a 500 ms network cache and one decoder thread per stream. It uses each camera's configured substream even when enlarged-main quality is selected. Selecting a single camera or opening Float uses the existing Media3/SurfaceView path and stream preference. No camera encoder settings are changed. This is a separate backend trial, not a claim about tinyCam's engine.

The native runtime is shared, with one player per tile. Two startup attempts are admitted at once. First-frame/progress monitoring uses increasing native displayed-picture counters. Stop/release runs on a serial worker after UI view detachment. A shared retirement barrier prevents new players from starting until retiring players finish releasing; stale callbacks cannot cancel a replacement session. Unchanged tiles are retained when activity changes ordering.

The TV APK includes ARMv7/ARM64 native libraries and is approximately 52 MiB; it retains API-22 installation support for ordinary viewing. The phone target has no LibVLC dependency. About contains the third-party notices; [library/source information](../third_party/libvlc-3.7.7/README.md) documents the SDK, license and native build source.

## Physical-device results — 2026-11-08

The comparison target is an AFTKM Fire TV Stick, API 30. Its Android codec capability API reported five AVC hardware instances, which is an advisory capability rather than a guaranteed concurrent workload. A six-feed Media3 trial fell back to the platform software decoder and failed. Isolated platform-software trials also failed on smaller streams. Those failures motivated the native backend; the standard four-feed path remains available.

The observed tinyCam wall used a large center camera with 12 perimeter cameras. Its active streams, decoder mode, frame rate and protocol were not extracted, so this is a layout comparison rather than a matched performance benchmark.

Native trial observations:

- Four feeds eventually displayed correctly. The first cold trial timed out and retried the first two streams before recovery; later six/nine launches did not reproduce this initial delay. It remains a startup caveat.
- Six feeds: 85 seconds, four screenshot samples, all six crops changed between samples; RSS approximately 235–239 MiB, no separate SurfaceView layers and unchanged monitored processes.
- Nine feeds: 94 seconds, four screenshot samples, all nine crops changed between samples; RSS approximately 282–287 MiB, no separate SurfaceView layers and unchanged monitored processes.
- Thirteen feeds: 606 seconds, 18 screenshot samples, all 13 crops changed between every sample; RSS approximately 246–319 MiB and lower at the end, no separate SurfaceView layers and unchanged app/codec/graphics/system processes and boot ID. No retries, failures or stalls were recorded. Native displayed-picture counter rates were approximately 17–22 FPS per feed in one interval. A short CPU sample used approximately 2.6 of four cores; this is an opt-in CPU-intensive profile. The user subsequently confirmed that all 13 pictures looked normal on the physical TV. This is a point-in-time confirmation, not observation throughout the soak.

Two fullscreen → next-camera → 13-camera-wall cycles passed on the same process, with the default center restored and no recorded playback failures/stalls.

Host validation: 104 TV/core tests and 12 phone tests passed, as did three diagnostic-summary tests. TV lint reports zero errors and 34 warnings. Added coverage includes 13-tile geometry, selector limits/default center/activity, requested/effective budgets and asynchronous release admission. The existing signing identity is retained.

Displayed-picture counts and ADB screenshot changes are useful independent observations, but neither alone proves smooth physical-TV playback or latency. Measured live delay, further transition tests, fault recovery, a one-hour/overnight soak, other models and a renewed Float/Plex test remain separate checks. This release does not lift default compatibility budgets or establish 13-camera reliability for every device/stream combination.
