# Controlled native-memory validation

This is a follow-up protocol, not a completed test. The [overnight review](OVERNIGHT-REVIEW-2026-11-09.md) found rising native-allocation counters in the installed 0.2.5 thirteen-camera wall. Version 0.2.6 was subsequently [installed on five TVs](DEPLOYMENT-026.md), and the [daytime investigation](MEMORY-INVESTIGATION-2026-11-09.md) confirmed continued same-process growth followed by SIGKILL. No public 0.2.6 APK or memory fix has been released. Advancing counters, fresh screenshot crops and a stable PID do not establish a stable memory budget or continuous physical presentation.

The later [controlled output experiment](NATIVE-OUTPUT-TRIAL.md) attributed growing allocations to Fire OS timing tables and tested a debug-only SurfaceView workaround. Those short traces and the restored original installation do not complete this multi-hour acceptance protocol.

## Establish a comparable baseline

Run during an authorized interactive window on a designated test screen. Preserve the existing encrypted settings, camera identities, ordering, enabled flags, MQTT identity, chosen layout and original signing identity. Keep local snapshots and diagnostic captures out of Git. Do not alter encoders, broker rules or firewall settings to make a comparison pass.

Use the same device, app ABI, camera streams, resolutions, codecs and layout for the installed-version and candidate runs. Record actual native versus Media3 engine selection; an imported dense-grid flag does not prove the native engine is active. Record APK hashes, process and boot IDs, device model, API level and thermal conditions. Use a fresh application process for each comparison, clearly recording intentional restarts rather than treating them as automatic recovery.

First measure fixed-camera playback without motion-driven replacements. Then repeat with the normal activity behavior. Keep the production Home Assistant rules unchanged: use an isolated test configuration or a manual view override, record which was used and restore the screen's original state afterward. A different device or changed encoding is a separate experiment.

## Measurements and trial sequence

Start with the device's ordinary supported budget. Increase to experimental counts only on an eligible profile; use the same count sequence for both APKs. Do not raise default limits based on a short successful trial.

For each run, record startup, 5-, 15-, 30- and 60-minute points, extending the thirteen-feed comparison beyond the period where growth was previously observed if the device remains healthy. Collect:

- Native allocated bytes, process RSS, PSS and swap as separate accounting measures, plus system available memory. Do not sum these into one memory figure.
- App and relevant codec/compositor process IDs, thread count, CPU and thermal readings.
- Per-camera attempt IDs, tile pixel dimensions and resize counters, startup latency, retries, stalls, cleanup failures and decoded/displayed counter deltas. The candidate's resize counter includes the initial positive tile size; it does not count position-only changes or measure native-buffer reallocations.
- Periodic actual-picture checks and visible camera-clock age against a reference. Submission/display counters and SDK media time are not live-edge latency measurements.

Use comparable sampling overhead in both runs. Prefer lightweight logs/status reads between occasional screenshots and fuller memory dumps. Retain numeric summaries publicly only after excluding operator-specific data; raw captures remain local.

Record screenshot capture-start/end times separately from later processing and file-save times. The file's modification time can include subsequent ADB reads and is not a reliable reference for the visible camera clock.

Separate three cases: stable playback with unchanged sessions, normal motion-driven camera replacement, and repeated wall/solo/return/Float lifecycle transitions. Wait for release completion before each new transition. Verify that startup admission remains blocked through worker disposal, that failures are counted, that completed callbacks do not affect replacement sessions and that the expected default center returns after activity expires. No background recycling timer should conceal a growing allocation trend.

Stop the trial and retain evidence if pictures freeze, the app or system process changes unexpectedly, resources keep rising toward exhaustion, thermal mitigation occurs, or another app is affected. Do not describe an interrupted run or intentional restart as a completed soak.

## Attribute allocations before changing policy

The observed app uses 32-bit ARM. A read-only inventory found Perfetto but no `heapprofd` at its standard system path. Select a profiling method that actually supports that runtime before enabling a trace; executable presence alone does not establish native-allocation tracing support. Profile a controlled test run, not a screen someone is using. If another device is required, record that limitation and keep its results separate from the matched-device comparison.

Compare retained allocations across steady playback, camera replacement and full player/runtime disposal. Distinguish live native allocations from allocator reservation and queued media. The pinned VLC core's decoder FIFO threshold and the RTSP network-cache setting are not application-wide memory caps. Do not add packet dropping, codec changes, process resets or higher stream budgets without evidence from these measurements.

## Candidate acceptance

Repeat the matched run after any memory fix. Validate excluded/older TV profiles, phone playback, reconnect behavior, motion/default-center restoration and Float/Plex coexistence separately. Host tests and CI remain required, but they do not replace physical playback checks. Publish a new APK only with an accurate account of the hardware checks performed and unresolved limits.
