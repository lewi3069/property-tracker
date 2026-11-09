# Overnight reliability review — November 9, 2026

Development source is now **0.2.6 / code 27**. The installed and downloadable TV APK remains **0.2.5**; the phone download remains 0.2.0. No APK was installed or published during this review. Google Play work remains paused.

## Changes verified on the host

- **Enforce the native-engine profile at playback time.** A saved `denseGrid` flag previously selected the native renderer even when the device's effective budget excluded the experiment. The UI disabled the option, but restored/transferred settings could still select that renderer. The renderer now also requires TV mode, API 26+, grid output and the eligible playback profile. Ineligible TV and phone settings retain their ordinary decoder and enlarged-stream preference. Regression cases cover an excluded model, the older-stick profile, Android 6 and both tested phone API levels.
- **Release the last native runtime on the worker.** Individual native players already stopped on a serial worker, but the final shared `LibVLC.release()` still ran on the UI thread. It now uses that worker too. The last tile's completion callback returns to the UI only after disposal, preserving the existing admission barrier. Tests cover shared references, deferred completion, replacement isolation and a disposal exception. This removes a potentially blocking UI call; no measured device responsiveness improvement is claimed yet.
- **Reduce steady-state native health polling.** Startup still samples every 500 ms so that staged camera admission remains responsive. After the first advancing displayed-picture count, health sampling uses a two-second interval: 75% fewer steady-state sampling callbacks and statistics-sample allocations. This changes health sampling, not video frame rate or camera encoding. A regression test checks sustained health and the existing 20-second stall threshold with two-second samples. No device CPU reduction is claimed before testing the candidate.
- **Add numeric native diagnostics.** Health events now include demuxed bytes and the SDK's media-time counter alongside input bytes and displayed-picture counts. RTSP input-byte counts were zero in the observed installed logs. The extra counters help investigate clock and buffering behavior; media time is not a measurement of live-edge delay. The log summarizer accepts these fields without copying raw exception text or stream addresses.

All **110 TV/core tests**, **14 phone tests** and **three diagnostic-summary tests** passed. Both debug APKs build. TV and phone lint report zero errors, with 34 and 47 warnings respectively. The TV candidate retains API-22 installation support and ARMv7/ARM64 libraries; the phone candidate has no native video libraries. The new native lifecycle and sampling code has not yet been exercised on physical hardware.

## Repeatable build verification

The repository now has an Android CI workflow using pinned action commits, Java 21, Gradle 8.11.1 and the required Android SDK packages on Ubuntu 24.04. Its [first complete Linux run passed](https://github.com/lewi3069/property-tracker/actions/runs/42580463373) for source commit `35a6ea1`: TV/phone tests, both APK builds and lint tasks, three diagnostic-summary checks, Windows script syntax and artifact compatibility checks. Test/lint reports are retained for seven days. It uses read-only repository permissions and a disposable debug signing key; it uploads reports rather than APKs or keys.

The new `scripts/verify-debug-apks.py` also passed against the local candidate builds. It checks package/version identities, API levels, ZIP integrity, the six expected TV ARM libraries and the absence of native libraries in the phone target. The Windows bootstrap now downloads Java 21 directly, avoiding the previous Java 17 download followed by a separate Java 21 setup. The build wrapper fails clearly if that required test runtime is absent. PowerShell parsing and an invocation of the updated wrapper passed locally; a complete fresh Windows toolchain download was not repeated.

## Passive observation of the installed version

The AFTKM viewer was already awake and displaying Property Tracker. A read-only 117-second observation captured four screenshots; all 13 camera crops changed between every sample. The app, codec, graphics and system process IDs and boot identity matched the earlier final-install observation. Recent displayed-picture counters were advancing. This is evidence of a still-running process and current playback, not continuous observation throughout the intervening hours.

Memory warrants further investigation: RSS was approximately 414–421 MiB during this sample, and native allocation counters ranged from approximately 530–535 MiB. Earlier short-run native counters were around 344 MiB. One memory dump reported approximately 515 MiB total PSS and 65 MiB swap PSS; these measurements are different accounting views and should not be added together. A short CPU sample was about 2.5 of four cores. The cause of the higher native allocation is not established; allocator retention, camera replacement and continued growth require separate measurement. The native engine remains experimental and default device limits are unchanged.

The exact pinned SDK JNI sources were inspected for the statistics and current-track calls. The statistics wrapper uses a stack statistics structure and returns a Java sample; the current-track wrapper calls the core track-ID accessor. No retained native allocation was identified in those wrappers. This does not rule out a leak or buffering growth elsewhere in the SDK/core.

A later 114-second passive sample again showed all 13 crops changing and the same monitored processes. RSS was approximately 423–427 MiB and native allocation counters approximately 549–555 MiB, above the earlier sample. This strengthens the case for further native-memory profiling; it does not identify which allocations account for the difference.

A separate three-second, single-frame RTSP probe of a wider 1536 × 576 feed returned a current image on the host. Camera clocks were read without changing them, and the TV's visible timestamps were close to the current reference during a subsequent screenshot. These point checks did not demonstrate a large accumulated playback delay; they are not a continuous or precisely synchronized latency benchmark. No encoder settings or TV views were changed.

The pinned [VLC core decoder implementation](https://code.videolan.org/videolan/vlc/-/blob/84177b2273abc5c4300234e6e18b55a4d2dd5a03/src/input/decoder.c#L2036) has a 400 MiB FIFO reset threshold for unpaced input and a separate count-based pacing branch. This is not an application-wide memory limit, and source inspection does not establish which branch or queue sizes were present in the observed sessions. A 500 ms network-cache setting must not be described as a hard native-memory bound. No decoder-FIFO patch or arbitrary compressed-packet dropping has been introduced.

The user confirmed all 13 physical-TV pictures looked normal after the original test. That point-in-time confirmation is recorded in the 0.2.5 documentation and release notes. Neither it nor this passive sample establishes an overnight presentation or latency result.

## Remaining work

Continue passive memory/counter trends while the viewer is already open. Investigate sustained growth before raising default budgets or making CPU/frame-quality changes. Validate the 0.2.6 candidate's native shutdown and unsupported-profile behavior on hardware during an authorized interactive test. Longer uninterrupted presentation, live latency, fault recovery, Float/Plex coexistence and other hardware remain separate checks.

No TV was woken or navigated, and no camera encoder, firewall, broker, Home Assistant automation, camera catalog or saved credential was changed by this review.
