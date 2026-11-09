# Overnight reliability review — November 9, 2026

Development source is now **0.2.6 / code 27**. The installed and downloadable TV APK remains **0.2.5**; the phone download remains 0.2.0. No APK was installed or published during this review. Google Play work remains paused.

## Changes verified on the host

- **Enforce the native-engine profile at playback time.** A saved `denseGrid` flag previously selected the native renderer even when the device's effective budget excluded the experiment. The UI disabled the option, but restored/transferred settings could still select that renderer. The renderer now also requires TV mode, API 26+, grid output and the eligible playback profile. Ineligible TV and phone settings retain their ordinary decoder and enlarged-stream preference. Regression cases cover an excluded model, the older-stick profile, Android 6 and both tested phone API levels.
- **Release the last native runtime on the worker.** Individual native players already stopped on a serial worker, but the final shared `LibVLC.release()` still ran on the UI thread. It now uses that worker too. The last tile's completion callback returns to the UI only after disposal, preserving the existing admission barrier. Tests cover shared references, deferred completion, replacement isolation and a disposal exception. This removes a potentially blocking UI call; no measured device responsiveness improvement is claimed yet.

All **109 TV/core tests** and **14 phone tests** passed. Both debug APKs build. TV and phone lint report zero errors, with 34 and 47 warnings respectively. The new native lifecycle code has not yet been exercised on physical hardware.

## Passive observation of the installed version

The AFTKM viewer was already awake and displaying Property Tracker. A read-only 117-second observation captured four screenshots; all 13 camera crops changed between every sample. The app, codec, graphics and system process IDs and boot identity matched the earlier final-install observation. Recent displayed-picture counters were advancing. This is evidence of a still-running process and current playback, not continuous observation throughout the intervening hours.

Memory warrants further investigation: RSS was approximately 414–421 MiB during this sample, and native allocation counters ranged from approximately 530–535 MiB. Earlier short-run native counters were around 344 MiB. One memory dump reported approximately 515 MiB total PSS and 65 MiB swap PSS; these measurements are different accounting views and should not be added together. A short CPU sample was about 2.5 of four cores. The cause of the higher native allocation is not established; allocator retention, camera replacement and continued growth require separate measurement. The native engine remains experimental and default device limits are unchanged.

The exact pinned SDK JNI sources were inspected for the statistics and current-track calls. The statistics wrapper uses a stack statistics structure and returns a Java sample; the current-track wrapper calls the core track-ID accessor. No retained native allocation was identified in those wrappers. This does not rule out a leak or buffering growth elsewhere in the SDK/core.

The user confirmed all 13 physical-TV pictures looked normal after the original test. That point-in-time confirmation is recorded in the 0.2.5 documentation and release notes. Neither it nor this passive sample establishes an overnight presentation or latency result.

## Remaining work

Continue passive memory/counter trends while the viewer is already open. Investigate sustained growth before raising default budgets or making CPU/frame-quality changes. Validate the 0.2.6 candidate's native shutdown and unsupported-profile behavior on hardware during an authorized interactive test. Longer uninterrupted presentation, live latency, fault recovery, Float/Plex coexistence and other hardware remain separate checks.

No TV was woken or navigated, and no camera encoder, firewall, broker, Home Assistant automation, camera catalog or saved credential was changed by this review.
