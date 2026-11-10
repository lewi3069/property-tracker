# Native output experiment

**Normal builds retain their existing renderer.** An optional debug build tests direct Android video output with SurfaceView on the observed Amazon AFTKM / Android API 30 TV. It is a memory workaround experiment, not a released fix. No phone, older-stick or other-model output policy changes.

## Why this experiment exists

During the November 9, 2026 controlled test, the original 0.2.6 thirteen-camera wall retained about **13.6 MiB in Fire OS frame-timing tables over 15.3 minutes**, with camera sessions and tile dimensions held fixed. The recorded allocation stacks included `android::Surface::saveDequeueTime` and `android::Surface::uploadFrameTimestamps`, through C++ map node/bucket allocation. This is approximately the allocation-growth rate seen in the earlier four-hour observation. It identifies a dominant allocation owner in this device's graphics stack; it does not establish the cause of the earlier SIGKILL.

The same process reclaimed most native allocations when the players were closed: approximately **367.7 MiB playing to 11.1 MiB paused**. The final reading used the foreground setup-only screen because a background memory dump timed out. The allocation-profiler client disconnected during background release, so the trace must not be described as capturing every final free.

Two local diagnostic builds used the original signing identity, codecs, camera streams and identical native libraries:

- **Direct output with TextureView:** pictures and counters advanced, but the trace still retained Fire OS timing allocations. Changing only `--vout=android-display` did not remove the observed retention.
- **Direct output with SurfaceView:** the first trace recorded no retained allocation in those timing-table paths. A live log capture confirmed advancing counters for all thirteen feeds. Recent-ring snapshots sometimes missed health records because compositor messages occupied the ring; an empty filtered snapshot was not treated as a playback failure.

The repeated SurfaceView trace covered **fifteen minutes of fixed thirteen-feed playback plus a brief Auto-mode transition**, with 3,623 allocation rows over a 1,009-second dump span. Both timing-table paths again had zero retained sampled bytes at the endpoints, with no profiler error/loss/disconnection reported. Other codec allocations were present, so this was an active allocation capture rather than an empty profile. A separate live capture observed all thirteen camera counters during the Auto transition.

In the same SurfaceView process, native heap allocated was **326.9 MiB at 7:10 PM and 327.9 MiB at 7:33 PM Eastern**, approximately twenty-three minutes apart. These fixed-layout endpoints support the specific retention workaround; they do not prove a long-term plateau. Other-stack allocations increased by about 8.7 MiB during the repeat, which included toolbar/dialog transitions. Their ownership and later reclamation still need investigation; zero retained timing-table bytes does not mean zero allocation growth everywhere.

The user reported normal thirteen-camera pictures during the test window. These are point checks, not continuous presentation or an overnight soak. Sampling overhead and changing image content prevent a CPU-performance claim from the brief resource readings.

## Remaining limits

The two-feed SurfaceView screenshot showed a narrow overlap at a tile edge. The thirteen-layer run also produced frequent Fire OS compositor limitation/fallback messages. Neither behavior has been resolved by this prototype. Clipping, reordered/featured tile transitions, fullscreen/return, sustained system resources and uninterrupted multi-hour playback still need hardware acceptance checks. Ordinary and floating Media3 playback retain their existing paths; this experiment does not establish Float/Plex compatibility.

No TV-wide profiler property was changed. Permission for a temporary firmware-profiler switch was conditional on SurfaceView retaining the same metadata; the SurfaceView traces did not meet that condition. Diagnostic APKs and raw captures remain private.

The original **0.2.6 APK was restored at 7:36 PM Eastern**, with its installed SHA-256 matching the original artifact. The canonical encrypted files were restored byte for byte before relaunch; camera catalog, identities and the thirteen-camera Auto/fullscreen profile were preserved. A 7:39 PM check found all thirteen native feeds advancing with no recent playback errors. All test profilers were stopped and their named remote temporary files removed. No other TV, camera encoder, firewall, broker or Home Assistant rule was changed. This is a rollback to the original renderer, not a claim that the installed 0.2.6 memory issue is fixed.

## Host validation

The source prototype passed **117 TV/core tests, 14 phone tests and eight Python checks**. Both default packages built and lint reported zero errors. The normal TV version remains `0.2.6`; the opt-in debug package correctly uses `0.2.6-surface-trial`, with unchanged phone identity and the expected six TV native libraries. The generated release configuration remained disabled when the trial property was supplied. CI checks both debug package identities; it publishes reports, not experimental APKs.

The [complete CI run for `17d3e89`](https://github.com/lewi3069/property-tracker/actions/runs/42730626169) passed tests, builds, lint, script checks and both default/trial package guards. An earlier workflow indentation error was corrected before this successful run; no app-test failure was involved.

## Build explicitly

Pass `-PpropertyTrackerNativeSurfaceTrial=true` to the existing build wrapper or Gradle. Only the TV **debug** variant receives the flag and a `-surface-trial` version suffix. Release builds retain `false`, even if that property is supplied. The runtime also requires TV mode, manufacturer Amazon, model AFTKM and API 30; otherwise the original output remains selected. The requested layout/native-engine admission rules still apply.

For example, build into a separate disposable output directory:

```powershell
./scripts/build-android.ps1 -Tasks @(
  '-PpropertyTrackerNativeSurfaceTrial=true',
  ':app:testDebugUnitTest', ':app:assembleDebug', ':app:lintDebug',
  ':mobile:assembleDebug'
) -BuildOutputRoot 'C:/path/to/disposable/trial-build'
python scripts/verify-debug-apks.py --aapt .tools/android-sdk/build-tools/35.0.0/aapt.exe `
  --build-root 'C:/path/to/disposable/trial-build' --native-surface-trial
```

The build property accepts only `true` or `false`. It is not a saved app setting and does not migrate or rewrite camera/broker credentials. The public source prototype reproduces the tested output choices through a restricted opt-in; its newly packaged build still requires its own hardware validation. No experimental APK is published by CI.

The [memory investigation](MEMORY-INVESTIGATION-2026-11-09.md) records the preceding source pass and process-kill evidence. The [native-memory protocol](NATIVE-MEMORY-VALIDATION.md) remains the multi-hour acceptance plan; a restart or a single lower heap value is not a pass.
