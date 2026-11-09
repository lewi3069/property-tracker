# Native-memory investigation — November 9, 2026

**0.2.6 still has substantial native-allocation growth.** The same thirteen-camera process grew by 217.9 MiB over 4.08 hours and subsequently received SIGKILL. The allocation owner and the reason for that kill remain unresolved. No runtime memory fix or new APK is claimed by this investigation.

## Installed 0.2.6 evidence

The two passive measurements below came from the same AFTKM Fire TV, app version/code, boot, PID and process-start tick. Property Tracker was already awake and foreground when measured. Times are Eastern on November 9.

| Measure | 12:15 PM | 4:20 PM |
| --- | ---: | ---: |
| Native heap allocated | 510.3 MiB | 728.2 MiB |
| Java heap allocated | 2.65 MiB | 2.83 MiB |
| Process RSS | 392.1 MiB | 559.6 MiB |
| Process swap | 51.5 MiB | 136.4 MiB |
| Threads | 76 | 85 |
| Native camera attempts in recent capture | 13 | 13 |
| Attempts with advancing displayed counters | 13 | 13 |

The native allocation increase averages **53.4 MiB/hour** between these endpoints. Allocation, RSS and swap are separate accounting measures and must not be added together. Allocator accounting alone cannot distinguish retained live buffers, allocator caches and a leak. Advancing displayed counters are not continuous physical presentation or a live-edge latency measurement.

Twelve camera/session/attempt keys matched across the two short captures; one camera had a different session. Those matching keys accumulated **1,435 parent-tile size changes** between their last captured records. At the later capture, eleven attempts had unchanged sampled geometry throughout their roughly three-minute windows; two changed size. There were no recognized playback/stall/stopped/cleanup-failure events in either supplied recent capture. The gaps between captures were not continuously logged.

This evidence makes camera replacement an insufficient explanation on its own. It does not isolate resizing from steady playback: size changes occurred, the resize counter does not measure native-buffer allocations, and the native-heap counter is process-wide rather than attributable to the camera logging it.

## Confirmed process termination

Retained Android exit history records the measured 0.2.6 process ending at **4:43:11 PM**, with `REASON_SIGNALED` and status **9 / SIGKILL**, while its recorded importance was foreground. At the 5:15 PM post-mortem check, the app had no process and the TV's kernel boot identity was unchanged. This confirms a process termination without a kernel reboot.

SIGKILL does not establish the killer or its motive. Android documents that some devices report memory-pressure kills as `REASON_SIGNALED`/SIGKILL when explicit low-memory reporting is unsupported. That is a possible explanation here, not a demonstrated one. The retained record had no description or trace, and the available log ring did not retain the target's kill message. [Android ApplicationExitInfo reference](https://developer.android.com/reference/android/app/ApplicationExitInfo#REASON_LOW_MEMORY)

The retained history also records the earlier 0.2.5 observation process receiving SIGKILL at **8:15:54 AM**, after the overnight review's final sample. The older compositor tombstone in the crash buffer predates these incidents and is not evidence of their cause. Historical exit-record memory fields are not substituted for the timed measurements above.

## Source ownership review

The review used the installed dependency, LibVLC Android **3.7.7**, its JNI revision **`0b8dc65efb203c86a0476bc337ddd99ecf1c0ef6`**, and its pinned VLC core **`84177b2273abc5c4300234e6e18b55a4d2dd5a03`**. The following are source observations, not allocation traces proving those paths were active on this device:

- The statistics JNI wrapper uses a stack statistics structure and returns a Java sample. Media track arrays are cached, and the JNI track-array call releases its core track list. The current-video-track accessor uses the existing media reference; it does not acquire an unreleased `getMedia()` reference on every poll.
- The inspected JNI event callback deletes its temporary string reference, and attached native threads have a detach destructor. No per-poll retained native object was identified in the inspected wrappers.
- The software Android picture resource has a default destructor that frees its private picture data; an absent custom destructor is not a missing free. Picture-pool clone release returns the reference to the pool. The Android display-size control branch updates geometry rather than allocating a new picture pool directly. Native-window destruction releases its native window and Java global surface reference. These functions do not establish which output module the observed process selected. [Pinned picture ownership](https://github.com/videolan/vlc/blob/84177b2273abc5c4300234e6e18b55a4d2dd5a03/src/misc/picture.c), [pool ownership](https://github.com/videolan/vlc/blob/84177b2273abc5c4300234e6e18b55a4d2dd5a03/src/misc/picture_pool.c), [Android display](https://github.com/videolan/vlc/blob/84177b2273abc5c4300234e6e18b55a4d2dd5a03/modules/video_output/android/display.c), [native-window ownership](https://github.com/videolan/vlc/blob/84177b2273abc5c4300234e6e18b55a4d2dd5a03/modules/video_output/android/utils.c)
- Input derivative statistics retain two samples, replacing and freeing the previous third sample; they do not keep an unbounded measurement history. The Live555 receive buffer doubles only below its approximately 2 MB threshold. Neither that individual buffer rule nor the previously reviewed decoder FIFO threshold caps process-wide memory. [Pinned statistics](https://github.com/videolan/vlc/blob/84177b2273abc5c4300234e6e18b55a4d2dd5a03/src/input/stats.c), [RTSP receive buffer](https://github.com/videolan/vlc/blob/84177b2273abc5c4300234e6e18b55a4d2dd5a03/modules/access/live555.cpp)
- App shutdown still detaches views, serializes player/media retirement, and holds startup admission through shared-runtime disposal. The daytime captures provide no cleanup-failure evidence. They do not demonstrate complete reclamation after a controlled full shutdown.

No SDK fork, packet-dropping policy, decoder/encoder change, periodic restart or increased resource budget was introduced. A targeted source audit cannot exclude a leak in other SDK, codec, graphics-driver or allocator paths.

## Repeatable host comparison

`scripts/compare-native-memory.py BEFORE.json AFTER.json` compares the existing private passive-measurement receipts. It rejects mismatched screen/boot/PID/start-tick/version identities, nonadvancing times and unsupported heap columns. It reports native/Java allocation, RSS and swap separately, along with matching attempts and resize counts. Its output excludes device, camera, session and boot identifiers, stream addresses and arbitrary input text.

Eight Python checks passed: four existing playback-summary cases and four comparison cases covering growth accounting, restart/reboot/version rejection, malformed/time-mismatched inputs, privacy, and resize/replacement separation. Running the comparator on the two saved receipts reproduced the values above. CI now discovers both Python test files. The Android runtime is unchanged; the prior Android build/test results remain historical validation of 0.2.6, not evidence of a memory fix.

## Required interactive test window

The next useful step is a **60–90 minute dedicated window on the affected Fire TV**, with someone available to confirm pictures and let the screen be temporarily restarted or run with profiling. The current authorization permits retained crash reads and passive foreground observation; it explicitly excludes these disruptive tests. No profiler or new diagnostic APK has been installed or started.

1. Preserve encrypted settings, identities, APK/signing identity and the thirteen-feed profile. Check the device's actual available profiling components and 32-bit support before choosing a trace method. A Perfetto executable alone is insufficient. Android 11's documented inability to profile a 32-bit process on a 64-bit device is relevant if this device has that configuration. [Perfetto Android 11 limitations](https://perfetto.dev/docs/data-sources/native-heap-profiler#android-11)
2. Prefer a bounded sampled native-allocation trace if the shipped firmware supports it. If it does not, prepare a local-only, original-key debug build with an opt-in Android `wrap.sh` malloc-debug configuration, then verify packaging and startup before using it for attribution. This alternative is available from API 27 and requires a fresh process. Android warns that allocation backtracing can greatly slow allocations; start with a small feed count and stop if playback degrades. The instrumented run cannot serve as an ordinary performance benchmark. [Android wrap.sh guide](https://developer.android.com/ndk/guides/wrap-script), [Android 11 malloc-debug documentation](https://android.googlesource.com/platform/bionic/+/refs/tags/android-11.0.0_r48/libc/malloc_debug/README.md)
3. Compare fixed-camera/fixed-geometry playback with a recorded sequence of center-size changes on the same streams. Keep production Home Assistant rules unchanged, using an isolated test override. Take bounded allocation snapshots early and after 15–30 minutes to identify growing call sites/modules. Record which mechanism was actually supported and its overhead.
4. Test controlled player replacement, then full player/runtime disposal. Compare surviving allocation stacks and counts after release completion, keeping an intentional restart separate from reclamation evidence. Restore the original APK/settings/profile and retain raw traces only locally.

The detailed [native-memory validation protocol](NATIVE-MEMORY-VALIDATION.md) remains the acceptance plan. A subsequent fix needs a matched, unprofiled multi-hour run beyond the prior growth interval and separate lifecycle/compatibility checks. A restart or a single lower memory reading will not count as a fix.

All TVs were left untouched during this source/host pass. Rolling TV's profile and the explicitly skipped Colorado screens remain unchanged. No new APK or raw diagnostic capture was published; Google Play remains paused.
