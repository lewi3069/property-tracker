# Overnight reliability review — November 9, 2026

**Main unresolved finding:** all 13 pictures continued advancing during sampled windows, but native allocation kept rising. Long-term memory stability has not been established, and none of the candidate improvements has been installed on the observed TV.

Development source is now **0.2.6 / code 27**. The installed and downloadable TV APK remains **0.2.5**; the phone download remains 0.2.0. No APK was installed or published during this review. Google Play work remains paused.

## Changes verified on the host

- **Enforce the native-engine profile at playback time.** A saved `denseGrid` flag previously selected the native renderer even when the device's effective budget excluded the experiment. The UI disabled the option, but restored/transferred settings could still select that renderer. The renderer now also requires TV mode, API 26+, grid output and the eligible playback profile. Ineligible TV and phone settings retain their ordinary decoder and enlarged-stream preference. Regression cases cover an excluded model, the older-stick profile, Android 6 and both tested phone API levels.
- **Release the last native runtime on the worker.** Individual native players already stopped on a serial worker, but the final shared `LibVLC.release()` still ran on the UI thread. It now uses that worker too. The last tile's completion callback returns to the UI only after disposal, preserving the existing admission barrier. Tests cover shared references, deferred completion, replacement isolation and a disposal exception. This removes a potentially blocking UI call; no measured device responsiveness improvement is claimed yet.
- **Reduce steady-state native health polling.** Startup still samples every 500 ms so that staged camera admission remains responsive. After the first advancing displayed-picture count, health sampling uses a two-second interval: 75% fewer steady-state sampling callbacks and statistics-sample allocations. This changes health sampling, not video frame rate or camera encoding. A regression test checks sustained health and the existing 20-second stall threshold with two-second samples. No device CPU reduction is claimed before testing the candidate.
- **Add numeric native diagnostics.** Health events now include demuxed bytes and the SDK's media-time counter alongside input bytes and displayed-picture counts. RTSP input-byte counts were zero in the observed installed logs. The extra counters help investigate clock and buffering behavior; media time is not a measurement of live-edge delay. The log summarizer accepts these fields without copying raw exception text or stream addresses.
- **Remove unused player views from native tiles.** The playback engine is now selected when constructing a tile. Native tiles create their native video view without first inflating and retaining a hidden Media3 `PlayerView` hierarchy. That avoids 13 unused player hierarchies in a 13-camera native wall. Ordinary and floating playback retain their existing Media3 views, including the floating `SurfaceView`. The new regression case checks both paths. This is a view-allocation improvement, not a demonstrated fix for the observed native-heap growth.
- **Contain cleanup exceptions on the release worker.** Runtime exceptions from final media or shared-runtime disposal could previously escape the worker. They now produce a fixed cleanup-stage diagnostic while still posting the existing UI completion after the disposal attempt. A regression test verifies that a simulated disposal failure is reported once, does not escape the worker and cannot release a replacement runtime. The summary script now counts unscoped cleanup failures separately from camera attempts, accepting only fixed stage names and copying no raw exception messages. No such failure was observed on the TV, and completing a failed disposal attempt does not establish that native resources were reclaimed.
- **Distinguish tile resizing from player replacement.** Native health records now include the tile's current pixel dimensions and a resize counter for that tile/session. The initial positive size counts once; moving a tile without changing its dimensions does not count. A host regression verifies the actual health output after sizing and repositioning. These fields help investigate allocation growth when player attempts remain alive but activity may change tile geometry. They describe the parent tile, not decoder resolution or native-buffer reallocations. No new sampling timer or playback policy was added.

All **112 TV/core tests**, **14 phone tests** and **four diagnostic-summary tests** passed. Both debug APKs build. TV and phone lint report zero errors, with 34 and 47 warnings respectively. The TV candidate retains API-22 installation support and ARMv7/ARM64 libraries; the phone candidate has no native video libraries. The new native lifecycle, sampling, view-allocation and sizing diagnostics have not yet been exercised on physical hardware.

## Repeatable build verification

The repository now has an Android CI workflow using pinned action commits, Java 21, Gradle 8.11.1 and the required Android SDK packages on Ubuntu 24.04. Its [first complete Linux run passed](https://github.com/lewi3069/property-tracker/actions/runs/42580463373) for source commit `35a6ea1`: TV/phone tests, both APK builds and lint tasks, three diagnostic-summary checks, Windows script syntax and artifact compatibility checks. Test/lint reports are retained for seven days. It uses read-only repository permissions and a disposable debug signing key; it uploads reports rather than APKs or keys.

The native-view allocation change also passed a subsequent [complete Linux CI run](https://github.com/lewi3069/property-tracker/actions/runs/42586963080) for source commit `214b6db`.

The cleanup-exception handling and fourth summary test then passed another [complete Linux CI run](https://github.com/lewi3069/property-tracker/actions/runs/42590568814) for source commit `1812cd4`.

The sizing diagnostics passed a further [complete Linux CI run](https://github.com/lewi3069/property-tracker/actions/runs/42598332795) for source commit `9683b8e`, including 112 TV/core cases, 14 phone cases and the four diagnostic-summary checks.

The new `scripts/verify-debug-apks.py` also passed against the local candidate builds. It checks package/version identities, API levels, ZIP integrity, the six expected TV ARM libraries and the absence of native libraries in the phone target. The Windows bootstrap now downloads Java 21 directly, avoiding the previous Java 17 download followed by a separate Java 21 setup. The build wrapper fails clearly if that required test runtime is absent. PowerShell parsing and an invocation of the updated wrapper passed locally; a complete fresh Windows toolchain download was not repeated.

Both local candidate APK signatures verified with the original local debug certificate. The TV APK has valid v1 and v2 signatures, including the v1 signing path required by its API-22 minimum; the API-26-minimum phone APK has a valid v2 signature. The existing 0.2.5 download's SHA-256 was unchanged. No candidate APK was copied to the download directory, installed or published.

## Resource limits reviewed

The existing admission queue permits two concurrent startups and blocks new admission during native retirement. Default AFTT, affected AFTMM/AFTKM and low-memory profiles retain their conservative budgets; thirteen-feed native playback remains an explicit eligible-TV experiment. Media3's 2 MiB grid and 8 MiB enlarged buffer targets do not cap total process memory or the native backend.

Control-message handling retains its existing bounds: an 8 KiB MQTT payload check, 64 pending UI messages drained eight at a time, 256 recent event IDs and 128 recent location identities. Expired commands and retained control messages are rejected. These bound the application's command handoff and policy maps, not every allocation in the MQTT or video libraries. Existing host cases cover burst handling, late messages, startup admission and reconnect deadlines. No new playback or broker policy was introduced during this pass.

## Passive observation of the installed version

The AFTKM viewer was already awake and displaying Property Tracker for each observation. The app process and boot identity remained unchanged at the sampled endpoints. Short screenshot samples at approximately 01:15, 01:45, 03:22, 05:00, 06:00 and 07:00 Eastern showed all 13 camera crops changing at every interval; their monitored codec, graphics and system processes also remained unchanged. These are intermittent read-only observations, not continuous physical-TV observation throughout the intervening hours.

Native allocation continued upward in the installed **0.2.5** process:

| Eastern time | Approximate native allocation (MiB) | Evidence at that point |
| --- | ---: | --- |
| 01:15 | 530–535 | Four screenshots over 117 seconds; all 13 crops changed |
| 01:45 | 549–555 | Four screenshots over 114 seconds; all 13 crops changed |
| 02:16 | 575–577 | Resource and recent health-counter reading |
| 03:22 | 650–654 | Three screenshots over 90 seconds; all 13 crops changed |
| 04:00 | 692–693 | Resource and recent health-counter reading |
| 04:28 | 715–716 | Resource and recent health-counter reading |
| 05:00 | 737–739 | Four screenshots over 117 seconds; all 13 crops changed |
| 05:28 | 757–758 | Resource and recent health-counter reading |
| About 06:00 | 781–783 | Two screenshots over 65 seconds; all 13 crops changed |
| 06:28 | 802–803 | Resource and recent health-counter reading |
| About 07:00 | 836–838 | Two screenshots over 64 seconds; all 13 crops changed |
| 07:28 | 860–862 | Final resource and recent health-counter reading |

These counters describe native allocations, not resident RAM. At 04:00, process RSS was approximately 602 MiB and process swap 66 MiB. At 05:28, RSS was approximately 379 MiB and process swap 357 MiB. The adjacent 05:58 resource point reported approximately 385 MiB RSS, 375 MiB process swap, 256 MiB system available memory and 49 MiB free out of 800 MiB system swap. Reduced RSS with higher swap and native allocation does not establish recovery or a memory plateau. Native allocation, RSS, PSS and swap are different accounting views and must not be added together.

At 06:58, the lightweight resource point reported approximately 469 MiB RSS, 352 MiB process swap, 258 MiB system available memory and 84 MiB free system swap. Thread count remained 78. These variations in residency and swap did not stop the increase in native allocation; the cause remains unidentified.

The final lightweight reading at 07:28 found the same viewer process and boot identity, approximately 472 MiB RSS, 374 MiB process swap, 240 MiB system available memory and 42 MiB free system swap. Thread count was still 78. Its brief CPU sample was approximately 76% of four-core capacity; the supplied recent log contained no playback or cleanup failures. No additional screenshot or profiler was taken at closeout.

Brief CPU readings ranged around 2.5–3 of four cores. The 05:58 reading was approximately 66% of four-core capacity. The thermal service reported status 0 and current HAL temperatures of approximately 65.9°C CPU and 42.8°C GPU; cached temperatures were different. These are short resource readings, not matched performance benchmarks or evidence of a candidate CPU improvement.

The supplied recent logs contained no playback, stall, stopped or cleanup-failure events. All 13 camera/session/attempt keys matched across the 03:22, 04:00 and 05:28 resource logs. Matching player attempts at endpoints does not rule out motion-driven resizing between them; this is why the candidate adds tile dimensions and resize counts. The cause of the native growth remains unresolved. No default resource budget was raised.

The exact pinned SDK JNI sources were inspected for the statistics and current-track calls. The statistics wrapper uses a stack statistics structure and returns a Java sample; the current-track wrapper calls the core track-ID accessor. No retained native allocation was identified in those wrappers. This does not rule out a leak, retained allocator memory or buffering growth elsewhere in the SDK/core.

Screenshot capture bounds are now recorded separately from later diagnostic reads and file saves. In the latest timed sample, the screenshot request ran from 06:59:51.05 to 07:00:02.95 Eastern, while visible camera clocks read approximately 06:59:44–47. Capture duration and camera-clock offsets prevent an exact live-edge-delay claim. Earlier screenshots without capture bounds must not use their file modification time as the reference clock. The observer requires at least two screenshots before reporting picture-progress success.

A separate three-second, single-frame RTSP probe of a wider 1536 × 576 feed returned a current image on the host. Camera clocks were read without changing them. These point checks did not demonstrate a large accumulated playback delay; they are not a continuous or precisely synchronized latency benchmark. No encoder settings or TV views were changed.

The pinned [VLC core decoder implementation](https://code.videolan.org/videolan/vlc/-/blob/84177b2273abc5c4300234e6e18b55a4d2dd5a03/src/input/decoder.c#L2036) has a 400 MiB FIFO reset threshold for unpaced input and a separate count-based pacing branch. This is not an application-wide memory limit, and source inspection does not establish which branch or queue sizes were present in the observed sessions. A 500 ms network-cache setting must not be described as a hard native-memory bound. No decoder-FIFO patch or arbitrary compressed-packet dropping has been introduced.

The user confirmed all 13 physical-TV pictures looked normal after the original test. That point-in-time confirmation is recorded in the 0.2.5 documentation and release notes. Neither it nor these passive samples establish an uninterrupted overnight presentation or latency result.

## Remaining work

Investigate sustained native growth in a controlled test window before raising default budgets or making CPU/frame-quality changes. Validate the 0.2.6 candidate's native shutdown and unsupported-profile behavior on hardware during an authorized interactive test. Longer uninterrupted presentation, live latency, fault recovery, Float/Plex coexistence and other hardware remain separate checks. The overnight review ended with a host-verified candidate and an unresolved native-memory limit; no candidate APK was installed or published.

The [controlled native-memory comparison plan](NATIVE-MEMORY-VALIDATION.md) separates steady playback from camera-replacement and shutdown tests. It has not been run during the passive overnight review.

The source/lifecycle review also retained the existing behavior: hidden Activities stop their tick, MQTT inbox/connection and wall players; returning from Float closes its service before admitting wall playback; unchanged selection signatures avoid rebuilding the wall on each tick. Existing passing cases cover thirteen-feed default-center restoration, motion expiry and refresh, manual pin precedence, floating-default restoration, sleep/redelivery handling and phone API-35/36 layouts. These host checks do not establish live Home Assistant delivery or device playback behavior.

A read-only inventory identified a 32-bit ARM app runtime and a Perfetto executable on the observed device, but no `heapprofd` executable at its standard system path. The installed debug package permits `run-as` access. A native allocation trace therefore needs a suitable profiling method or another controlled test device; no profiler, heap dump or process signal was started during these observations.

No TV was woken or navigated, and no camera encoder, firewall, broker, Home Assistant automation, camera catalog or saved credential was changed by this review.
