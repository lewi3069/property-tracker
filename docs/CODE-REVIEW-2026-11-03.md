# Native APK efficiency and reliability review — November 3, 2026

**Implementation update:** this historical review describes 0.1.10. Version 0.1.12 implements continuing frame-submission monitoring, sustained-progress retry reset, bounded consecutive stall recovery, and low-frequency playback measurements. It also adds an opt-in software H.264 preference for Float. The RTSP packet queue remains unbounded; targeted camera saves, MQTT subscription acknowledgement, and the other recommendations below remain open. See [live comparison results and limits](testing.md#later-freeze-and-0112-compatibility-trial).

Reviewed the native Java application, playback policy, encrypted settings, floating service, MQTT bridge, manifest, build setup, and relevant tests. This is a review with offline reproductions; application runtime code and the installed 0.1.10 APK are unchanged.

This review was performed offline, without live device connections. The operator subsequently authorized a separate live comparison; see [current test results](testing.md). The review describes 0.1.10 behavior before the Property Tracker branding and floating-title update.

The most valuable work is improving stream memory control and avoiding unnecessary decoder restarts. Small Java allocation reductions come later. None of the findings below establishes the exact cause of the earlier Fire TV terminations.

## Findings in implementation order

### 1. P1 — Load-control targets do not bound RTSP memory

Location: `CameraTile.java:99–101`, `PlaybackLimits.java:8–9`.

Each player has a 2 MiB or 8 MiB allocation target, but the configured TCP RTSP path has additional buffering outside that target. In the exact Media3 1.6.1 source, the incoming packet channel uses an unbounded queue, and RTSP loading continues independently of the usual load-control decisions. These targets also do not bound native decoder or graphics memory. Sources: [TCP packet channel](https://github.com/androidx/media/blob/1.6.1/libraries/exoplayer_rtsp/src/main/java/androidx/media3/exoplayer/rtsp/TransferRtpDataChannel.java#L35), [RTSP loading](https://github.com/androidx/media/blob/1.6.1/libraries/exoplayer_rtsp/src/main/java/androidx/media3/exoplayer/rtsp/RtspMediaPeriod.java#L347).

**Offline evidence:** instantiated the dependency's packet channel without opening a socket. It accepted 4 MiB of synthetic packets despite the application's 2 MiB grid target. The probe confirms an unbounded buffering path, not that this queue grew during the real Plex incident.

**Recommended change:** retain the conservative stream count and substream defaults, add per-stream buffer/frame-progress measurements, and recover an individual stream when it accumulates excessive backlog. A strict packet-memory bound requires a suitable RTSP source implementation or a narrowly maintained upstream change; lowering `setTargetBufferBytes` alone cannot provide it. Do not drop arbitrary compressed-video packets or block the shared RTSP control receiver as a quick fix. Budget decoded resolution and native memory as well as Java buffers.

### 2. P2 — Saving one camera restarts every visible feed

Location: `MainActivity.java:438`; removing a camera follows the same pattern at line 421.

The save handler unconditionally calls `releaseTiles()`. Adding a hidden camera, copying a camera outside the current grid, or editing an unrelated entry tears down every visible RTSP session and decoder. This causes avoidable buffering, connection traffic, and decoder allocation churn.

**Recommended change:** compare saved camera settings and invalidate only affected tiles. Preserve unchanged visible players. A label/zone edit should update metadata; changing a visible camera's connection or stream settings should restart that camera. Preserve the current pinned selection when it remains valid.

**Regression checks to add:** adding/editing/removing a hidden camera leaves existing player instances running; changing one visible URI restarts only that player; disabling/removing the pinned camera selects a valid replacement.

### 3. P2 — The watchdog does not detect a frozen player that stays READY

Location: `CameraTile.java:111–120`, `130–142`.

The watchdog is armed during startup and buffering. The first frame or a READY event cancels it. There is no ongoing check of video frame progress. If a decoder stops producing frames without reporting BUFFERING or an error, the window can remain labelled Live indefinitely.

**Offline evidence:** delivered a first-frame event and READY to the actual listener using a fake player, then advanced the host test clock 25 seconds without another frame. No watchdog or retry remained scheduled, and the label stayed Live. No real player or network connection was created.

**Recommended change:** check a lightweight decoded/rendered-frame counter with monotonic elapsed time. Recover only the affected stream after a sustained absence of progress. A motionless scene remains valid video; image changes must not be used to detect a freeze. Keep first-frame detection as the initial Live criterion.

### 4. P2 — One good frame resets the retry backoff too early

Location: `CameraTile.java:113`, `118`, `145–152`.

Both first-frame and READY callbacks reset `attempts` to zero. A failing stream that briefly produces a frame can therefore reconnect every two seconds indefinitely. Several cameras recovering together also use identical retry delays, concentrating connection and decoder-start work.

**Offline evidence:** seeded four previous failures, delivered one first-frame event, then a synthetic playback error. The next retry returned to two seconds.

**Recommended change:** reset failure history only after sustained frame progress, and add small bounded retry jitter. Keep camera failures independent. Classify unsupported main-stream formats separately so they can fall back to the configured substream instead of repeatedly rebuilding the same unsupported source.

### 5. P2 — MQTT reports success before the subscription is acknowledged

Location: `MqttBridge.java:33–34`.

`MqttAsyncClient.subscribe()` returns before the broker acknowledges the subscription. The code immediately reports connected and only catches synchronous exceptions. A later subscription rejection can leave the screen reporting connected while camera activity/location commands never arrive. Paho documents asynchronous completion through the returned token or an action listener. [Paho subscription API](https://eclipse.dev/paho/files/javadoc/org/eclipse/paho/client/mqttv3/MqttAsyncClient.html#subscribe-java.lang.String-int-).

**Recommended change:** report control readiness only after successful SUBACK/granted QoS validation, and handle asynchronous failure explicitly. Use a bounded initial-connect retry policy while the viewer is foregrounded; retain the existing stopped-instance guards. Add a fake-client seam so these cases can be tested without a broker.

This is future Home Assistant reliability work; it is not evidence about the current Plex disappearance.

### 6. P3 — Repeated view calculations allocate even when nothing changes

Location: `MainActivity.java:62–69`, `184–206`; `ViewSelector.java:80–111`.

Every 250 ms, the selector prunes maps, sorts event lists, builds ordered sets/lists, and returns copied selections. The render signature avoids rebuilding unchanged tiles, but only after those calculations and status/signature strings have been created.

**Recommended change:** cache unchanged selections and schedule reevaluation at the next meaningful deadline: activity expiry, five-second location stability, sixty-second location staleness, eight-second dwell expiry, or temporary-layout expiry. User actions and MQTT input should still trigger immediate reevaluation.

This is a lower-priority CPU/allocation improvement. The tick is removed in `onStop()`, so it does not keep running behind Plex. No Fire TV performance gain was measured during this review.

## Further improvements worth preparing

- **Reduce peaks during automatic view changes.** `MainActivity.java:216–227` upgrades streams in the new display order. A newly featured camera can start its main stream before another still-visible camera is demoted. Apply removals/downgrades first, then upgrades. This is a peak-memory opportunity inferred from call order; its device impact needs later measurement.
- **Use decoder capabilities and pressure, not only tile count.** `PlaybackLimits.java:18` limits AFTMM/low-RAM devices to four streams, but four small H.264 streams and four large HEVC streams have different costs. Add explicit main-to-sub fallback for unsupported formats and a conservative response to memory pressure. The operating system may still kill a process before a memory callback can help.
- **Bound incoming command work.** The MQTT path limits individual payloads to 8 KiB but posts one UI task per message. A bounded dispatcher/coalescing policy would prevent a burst from creating an unlimited pending queue. Preserve activity deduplication and location ordering when designing it.
- **Move encrypted settings I/O off the UI thread.** `ConfigStore.java:31,42–46` performs keystore work and synchronous persistence from Activity/service callbacks. Use one serialized worker, keep the old configuration active until saving succeeds, and apply results on the UI thread. No current ANR was reproduced; this is responsiveness work.
- **Expand passive diagnostics.** Add low-frequency per-process memory measurements for Fieldview, Plex, `media.codec`, and SurfaceFlinger, plus frame progress and a common timestamp. Current passive monitoring captures global memory/swap, PIDs, focus, and the floating window. Missing ADB data must remain distinct from a confirmed process death.
- **Evaluate a dependency update separately.** Google's release page lists Media3 1.11.1 as stable, including newer RTSP teardown/state fixes. However, its [TCP packet queue](https://github.com/androidx/media/blob/1.11.1/libraries/exoplayer_rtsp/src/main/java/androidx/media3/exoplayer/rtsp/TransferRtpDataChannel.java#L46) remains unbounded; upgrading alone is not a memory-limit fix. Review API compatibility and test API 25/actual hardware before adopting it. No dependency was changed. [Official release notes](https://developer.android.com/jetpack/androidx/releases/media3).

## Existing choices to preserve

SurfaceView is already the efficient path that the user visually confirmed. Audio tracks are disabled. Only visible cameras have wall players, and floating mode uses a single substream after releasing wall playback. Player release clears the old instance before detaching it, preventing reentrant double release. Hidden Activity ticks and MQTT connections are stopped. Keep those behaviors while making the changes above; a general framework rewrite is unnecessary.

## Local validation and scope

- Normal suite: **49 tests passed**, zero failures/errors. Offline Gradle build successful. Lint: **zero errors, 16 existing warnings**.
- Three isolated characterization probes passed against the current implementation: packet queue size, READY-without-frame-progress behavior, and immediate retry reset. These demonstrate existing behavior; they are not tests of a completed fix.
- Probe source/results: ignored `.tools/review/tests/com/fieldview/tv/OfflineEfficiencyReviewTest.java`, `characterization-results.xml`, and `offline-probes.log`. The temporary Gradle init script includes these probes without changing application source sets. The normal suite was run again afterward without that init script.
- Runtime source, dependencies, APK version, installed application, saved camera credentials, and live devices were not changed. Only review/handoff documentation and ignored local review artifacts were written.

Suggested first implementation batch: targeted camera updates, sustained-frame retry reset, and ongoing frame-progress detection, each with offline regression tests. Then address RTSP backlog/memory policy as a separate change with clear instrumentation. Keep live Plex stability and deliberate recovery tests separate when testing is permitted again.
