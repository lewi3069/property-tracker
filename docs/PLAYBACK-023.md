# Playback implementation: 0.2.3

This release implements the first playback-plan changes and makes a renderer comparison possible. It does not establish reliable six/nine-camera operation on the affected Fire TV or remove its four-camera safeguard.

## Implemented

- The main viewer owns a queue with two simultaneous startup attempts. Selected/featured tiles take priority, including queued tiles promoted later. First frame releases a slot; failure/cancellation releases the old player before freeing it. A 20-second admission deadline aborts the attempt before another is admitted. Late completion cannot free a newer attempt's slot. Queueing defers connection and stall timers until admission.
- Retries use the same queue, exponential backoff and up to 500 ms of jitter. Decoder failures wait at least ten seconds. Healthy tiles remain running; released/hidden tiles cancel their queued startup and retry work. Player calls remain on the application thread.
- Buffer targets follow the chosen URI's main/substream profile, including fallback to the substream when no distinct main path exists. A featured tile reusing a substream retains its player and smaller target.
- Start, first frame, decoder, failure, retry, release and 30-second wall/Float health events identify a safe camera ID, tile session and attempt. Health includes submitted frames, decoder output/drop counters, allocator bytes, Java/native heaps and buffered duration. Fixed decoder/format/parser messages supplement network/authentication/path errors. Complete URLs, passwords and exception messages are not logged.
- Detection heartbeats extend expiry while preserving the original detection order. Publishing a heartbeat for an older active camera no longer makes it newer than a subsequent detection. Manual Grid/Pin still take precedence; Auto reacts to detections. Existing Float arbitration retains its behavior.
- Requested layout count remains saved independently of the effective device cap. Opening/saving another layout option does not overwrite a larger saved request. The UI describes the cap as an app compatibility limit.
- **Experimental grid compatibility** creates multi-camera PlayerViews with `texture_view`, while keeping the same Media3 1.6.1, hardware decoder policy, RTSP streams and quality. It defaults off. Entering a single-camera view releases the grid tiles before creating a SurfaceView tile; returning recreates the experimental grid. Float always retains its original SurfaceView and software-H.264 preference. No additional camera capacity is enabled by this option.

## Validation

The TV build passes 96 core/UI tests. The phone target passes 12 orientation/fullscreen test cases. Android lint reports zero errors, with 30 TV and 40 phone warnings. The TV APK retains min API 22, target API 35, the package ID and the existing signing certificate. Both targets compile; Play publishing remains paused.

Tests exercise admission limits, priority/FIFO ordering, cancellation, delayed admission deadlines, release-before-replacement on timeout, stale completion, released queued tiles, unchanged-player reuse, requested/effective counts, renderer handoff, Float SurfaceView isolation, persisted opt-in defaults, and heartbeat ordering. Hardware playback and actual presentation require separate checks; unit tests do not measure decoder capacity.

The in-place update on three configured TV installations preserved the encrypted settings files byte for byte. Only the already-open viewer was reopened; sleeping viewers remained asleep. A direct MQTT observation confirmed valid QoS 1, nonretained commands for the approved sensor mapping on all three topics. A sample without active classification does not by itself prove visible detection switching.

Live observation details are recorded after completion below. A first-frame callback, submitted frame count, or decoder counter alone is insufficient evidence of a moving picture on the physical TV.

Capture and summarize diagnostics without changing playback:

```powershell
adb -s DEVICE_ADDRESS:5555 logcat -d -v epoch -s FieldviewPlayer > playback.local.log
python scripts/summarize-playback.py playback.local.log --output playback.local.json
python -m unittest discover -s scripts -p test_summarize_playback.py
```

The summary keeps allowlisted numeric counters and camera/session IDs, without copying raw errors or stream URLs. Submitted FPS is calculated between health samples of the same attempt. Startup concurrency counts only events present in the capture; neither metric verifies physical presentation. For a matched tinyCam comparison, record device/app versions, camera set, exact main/substream choice, RTSP transport, codec, encoded dimensions/FPS/bitrate, decoder mode, layout and live delay separately. Run each viewer alone with the same sources and compare startup time, memory trends, visible motion and recovery after transitions. Keep quality and decoder policy constant when comparing the two Property Tracker renderers.

## Remaining plan work

The matched tinyCam baseline, opt-in two/four-camera renderer comparison, physical picture checks, progressive six/nine-camera trials, one-hour/overnight soaks and other-device/Float/Plex regressions remain outstanding. The experimental option should be evaluated with decoding and streams held constant; do not attribute a result to TextureView when another setting changed simultaneously.

The Media3 RTSP backlog is still not bounded by the allocator targets. A maintained bounded transport/library solution and legacy API-22 build strategy remain separate work. No arbitrary compressed RTP packet dropping or blocking of the multiplexed RTSP control reader was introduced. Automatic stream-dimension/codec-aware budgets and measured device/renderer profiles are also still pending.

Keep existing device caps until these tests support a larger profile. Retaining an earlier request does not automatically qualify that request for future execution.

### AFTKM observation

A ten-minute-plus SurfaceView observation collected 31 samples over 619 seconds with automatic detection control enabled. App, codec, composer, SurfaceFlinger and system-server PIDs and boot ID remained unchanged. Every sample had four camera surfaces. Continuing surfaces advanced between samples; one sample caught a newly switching surface before its latency history was available, and the next sample had four valid presentation-timestamp histories again. App RSS ranged from about 73 to 82 MiB and ended around 75 MiB. Captured diagnostic events showed at most two in-flight starts, no playback failures/stalls, and cameras outside the default four appearing and returning to the overview without manual navigation.

This observation exercised the release's playback code before a final layout-selector-only refinement. Both TV and phone regression checks were rerun after that refinement. It is a short SurfaceView regression check, with physical-TV confirmation still pending; it is not a TextureView comparison, a proof of nine-camera capacity, or a long-term stability guarantee.
