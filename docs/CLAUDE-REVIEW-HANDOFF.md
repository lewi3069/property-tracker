# Property Tracker — independent code review handoff

Prepared November 17, 2026. This brief contains source-level context, not an operator inventory or permission to control deployed equipment.

## Start here

Copy this request into Claude with the repository or the curated source bundle:

> Independently review Property Tracker using `docs/CLAUDE-REVIEW-HANDOFF.md`. Verify the descriptions against the code; do not assume the previous implementation or tests are correct. Prioritize native-memory ownership, playback lifecycle, the new live-grid-to-main-stream handoff, motion selection, MQTT robustness, encrypted settings and Android TV/phone compatibility. Return concrete, prioritized findings with file/line references, failure scenarios, confidence, minimal fixes and meaningful regression tests. Review first; do not implement changes or operate deployed devices.

Repository: <https://github.com/lewi3069/property-tracker>.

- Review baseline: `9e4f943` (documentation head immediately before this handoff).
- Latest runtime change: `e6df952`, version **0.2.17**, version code **38**.
- To isolate the newest behavior in a clone: `git show e6df952`.
- The handoff itself is a later documentation addition; the curated bundle's app/build/test files come from the pinned baseline.
- Downloadable public APK versions lag development source. Review the source baseline above, not the older release APK.

Review scope is source and proportionate host checks. Do not connect to, wake, navigate, install on, restart or stress-test TVs/phones; publish MQTT commands; change camera encoders, firewalls, brokers or Home Assistant rules; alter global firmware/profiler settings; or resume app-store work. Those require a separately agreed interactive test window. Do not inspect, copy or publish operator credentials, signing keys, device inventories, private settings or raw diagnostic captures. Deployment details are deliberately omitted. Preserve application IDs, encryption identities, camera IDs/order/enabled state, MQTT client identity and saved profiles in any later proposed patch.

## Product and compatibility contract

This is a **native Android APK**, not a WebView/web app. Java Activities, Android views, Media3, an optional LibVLC grid backend and native remote/touch controls implement it. Historical Fieldview names remain intentional compatibility identities.

| Area | Current contract |
| --- | --- |
| TV | `app`, application ID `com.fieldview.tv`, minimum API 22, target 35 |
| Phone | `mobile`, debug ID `com.fieldview.mobile`, minimum API 26, target 36; shares the Java core but no LibVLC native libraries |
| Phone release identity | Release configuration uses `com.lewi3069.propertytracker`; do not casually rename either target |
| Languages/toolchain | Java 17 source, JDK 21 for checks, Gradle 8.11.1, Android Gradle Plugin 8.10.1, compile SDK 36 |
| Playback dependencies | Media3 1.6.1; TV LibVLC Android 3.7.7; Paho MQTT 1.2.5; desugaring 2.1.5 |
| Rendering | Ordinary/single/Float Media3 surfaces; compatible grid TextureViews; eligible dense TV grids use native software-decoded substreams |
| Device budgets | Conservative limits by hardware/low-RAM profile; opt-in eligible dense profiles allow 13. An API 22 stick retains its two-feed budget |
| Stream policy | Dense walls use grid streams, with at most one hardware main stream for the selected high-resolution view |
| Resolution | Geometry scales to the measured drawing area. Stream quality and feed limits do not automatically follow a 1080p versus 4K TV panel |
| Storage | App-private AES-GCM settings; modern Android Keystore AES and an API 22 RSA-wrapped AES compatibility path; backups disabled |

Do not upgrade libraries solely to get a newer version: older Android support and native packaging are intentional. Check upstream minimum API requirements and relevant RTSP fixes before proposing an upgrade. Host test success alone does not establish real decoder, SurfaceView/TextureView, Pixel or Fire OS compatibility.

## Architecture map

Paths below are repository-relative. The source bundle preserves this layout.

| Responsibility | Read first |
| --- | --- |
| Activity lifecycle, rendering, tile reuse, fullscreen/remote/touch controls, settings UI | `app/src/main/java/com/fieldview/tv/MainActivity.java` |
| Player ownership, first-frame handling, live preview, retries, stall detection, disposal | `app/src/main/java/com/fieldview/tv/CameraTile.java` |
| Startup admission and pending native disposal | `StartupCoordinator.java` in the shared Java directory |
| Dense grid session and LibVLC ownership | `app/src/tv/java/com/fieldview/tv/GridVideoFactory.java`, `SharedVideoRuntime.java`; shared `GridVideoSession.java` |
| Selection, timestamps, dwell, quiet return, simultaneous motion | Shared `ViewSelector.java`, `MultiMotionLayout.java` |
| Geometry and hardware/grid selection | Shared `CameraWall.java`, `WallStreamPlan.java`, `PlaybackLimits.java` |
| Decoder guards, main-stream failure backoff, frame health, socket deadlines | Shared `HardwareMainRenderersFactory.java`, `CenterStreamBackoff.java`, `VideoProgress.java`, `TimedSocketFactory.java`, `PlaybackFailure.java` |
| MQTT lifecycle, bounded UI delivery, input/freshness validation | Shared `MqttBridge.java`, `CommandInbox.java`, `CommandRouter.java` |
| Floating window and selected-default/motion restoration | Shared `FloatingCameraService.java`, `FloatingViewController.java` |
| Configuration, encrypted persistence and forms | Shared `AppConfig.java`, `ConfigStore.java`, `SettingsCipher.java`, `TvFormDialog.java` |
| Phone-specific backend/insets/UI | `mobile/src/main/java/`, shared `PhoneInsets.java`, phone manifest/resources and `PhoneUiTest.java` |
| Package/SDK/native separation and build verification | `gradle/android-common.gradle`, both module build files, `.github/workflows/android.yml`, `scripts/verify-debug-apks.py` |

The shared Java directory is `app/src/main/java/com/fieldview/tv/`. The phone module includes that source plus its own backend. `design-reference/browser-prototype/` is obsolete design material, not the production app.

## Recent behavior that needs especially careful review

### 0.2.17: faster motion and a live preview during main-stream connection

The user wanted both earlier motion response and less blank/buffering time while a new enlarged camera loads. The previous calmer behavior must remain available.

- On an eligible native 13-camera motion profile, `fastMotion=true` changes the minimum selected-camera hold from 10 to **5 seconds** and expansion stability from 2 to **1 second**. The all-clear return and lower-count shrink remain **15 seconds**. First detection is immediate. Turning off **Faster motion switches** restores the older timing and ordinary stream replacement. Old JSON missing this new flag defaults it to true; an explicit false persists.
- `MainActivity.render()` can call `CameraTile.prepareMainFromGrid()` instead of replacing an already-playing native tile. The tile retains the same attached native grid view and its frame-health state as a preview, lazily adding the hardware main SurfaceView underneath it.
- The grid picture is actual live playback, not a screenshot. Only the main player's actual first-rendered-frame callback releases the preview. Main failure can restore a healthy preview without reconnecting the grid stream. Stale/cold/unsupported outputs use ordinary startup.
- `GridVideoSession.supportsLivePreview()` defaults false; the TV backend enables it only for its normal TextureView output. The optional native SurfaceView experiment cannot use this overlay handoff.
- The role fields `surface`, `texture` and `nativeGrid` are now mutable. Carefully audit their consistency with `player`, `gridSession`, `previewSession`, `progress`, `previewProgress`, URIs, fallback flags, startup tickets and delayed callbacks.

Required invariants to verify, not simply assume:

1. The old hardware main is released before any new main is admitted. There is never a second concurrent hardware main.
2. Startup remains limited to two admitted attempts; the shared native-retirement barrier still blocks starts until disposal completes.
3. The temporary preview counts as a stream. Reduced motion layouts have at most 11 target streams plus one preview; the full 13-stream overview does not use this overlap. No background warming of every main stream.
4. A warm native view is not detached/reparented during promotion. It is closed once after success, cancellation, replacement, backgrounding or shutdown. Asynchronous close completion cannot affect a later request's ticket/player.
5. A failed main restores only a healthy matching preview, with correct URI, health, renderer role and fallback state; a later render must not accidentally reconnect that same grid.
6. Duplicate/late first-frame, error, timeout and native-close callbacks cannot double-release, resurrect a closed session, dispose a newer player or leak a startup slot.
7. A retained picture must not disguise a stale stream as a successful high-resolution connection. Cold feeds still have real startup delays.

### Existing geometry and motion behavior to preserve

The base eligible overview is 13 feeds with one center camera. With motion, regular single focus uses 8 feeds; a panoramic single focus uses 5; measured 4:3 single focus uses 11 (six thumbnails at right, four across the bottom). Two ordinary active feeds use 10 total; two panoramic feeds use 6; a panoramic/regular pair uses 8; three active feeds use 7; four or more use four large feeds. Review both featured orders in a mixed pair: visual position must not silently change which camera owns the single main stream.

Aspect classification is learned from video metadata, not camera names or addresses. Panoramic is wider than 2.2; the 4:3 focus band is 1.28–1.40. Geometry uses the measured wall size and fits images without cropping/stretching; bounded extreme ratios can retain padding. Main and substream ratios can differ. Metadata arriving late must not cause needless player replacement or render loops. The LibVLC track-list query waits until displayed pictures are positive because the API caches the first track list.

Manual Grid/Pin overrides, remote Up/Down camera browsing in Pin, disabled cameras, camera duplication/reordering, portrait phone layout, Back/fullscreen transitions, floating-camera default selection and motion restoration must remain intact. Motion heartbeats must not repeatedly increase priority; quiet time must use actual event expiry when resuming from the background.

## Review priorities and unresolved questions

These are investigation areas, not a list of newly proven defects.

1. **Native memory and graphics ownership.** Long dense-wall sessions have shown native-allocation growth despite healthy frame counters. The 0.2.17 handoff is not a memory fix. Audit every acquire/release, native view/picture/runtime owner, temporary output and resize/replacement path. Distinguish allocated heap, RSS, swap, buffering and allocator retention. Configured Media3 buffer targets do not cap all native transport/codec/graphics memory.
2. **Preview/lifecycle races and peak resources.** Trace success, unsupported main, main timeout, preview error/stall, rapid selection, camera editing/disabling, Activity pause/recreation, screen-off, Float handover and return to overview. Review the queue, cancellation ownership and shared-runtime disposal as one system.
3. **Motion/default restoration and timing.** Check overlapping detections, all-clear while a main is preparing, new motion during quiet time, clock rollback, expiry after backgrounding, manual overrides and catalog changes. Keep the chosen default and saved overview count.
4. **MQTT resilience and trust boundary.** Inspect exact-topic delivery, retained-message rejection, payload/type/timestamp validation, event deduplication, bounded UI queueing, worker ownership, connect/subscribe deadlines, failed SUBACK, reconnect and late callbacks after close. Commands are untrusted data; they are not authority to run arbitrary actions. Use dummy inputs and isolated host tests, not a production broker.
5. **Settings and responsiveness.** Check malformed/tampered envelopes, missing keys, modern/legacy key continuity, interrupted saves, stable IDs and URI/password escaping. Keystore and synchronous persistence on the UI thread deserve an evidence-based responsiveness review. Never recover a read error by silently erasing/replacing the user's configuration.
6. **TV/phone/Float compatibility.** Check API 22 loading/desugaring, 32/64-bit native packaging, target SDK behavior, native-layer clipping, orientation/insets, overlay permission/service lifecycle and shared-decoder coexistence. Prior HEVC Float/video-app freezes are not generally resolved; retain the optional small-stream software-H.264 Float path.
7. **Test gaps and maintainability.** Identify actual untested failure modes before proposing abstractions. Prefer minimal fixes to broad rewrites. Add tests that reproduce a defect or enforce an important cross-component invariant, not tests that copy implementation constants without validating behavior.

Native dependency caveat: `third_party/libvlc-3.7.7/` records upstream licensing and reference source pins. Earlier reference-source inspection did not establish an exact match for every packaged native core binary. Separate Java/JNI reference-source reasoning from allocation evidence for the actual binary. A controlled experiment implicated Fire OS frame-timing-table allocations; an opt-in SurfaceView workaround reduced that sampled retention but had compositor/clipping limitations. It remains disabled by default and is not proof of overall or multi-hour memory stability. Do not assume LibVLC, the app or firmware is the sole owner without evidence. Do not toggle firmware profiler properties as part of this source review.

## Verification baseline and safe host checks

At the pinned baseline, **195 Android host tests passed: 181 TV/core and 14 phone**. Both debug APKs built, lint had zero errors, and package/SDK/native-library/signature guards passed. [Runtime CI passed](https://github.com/lewi3069/property-tracker/actions/runs/44149863838), including Python summary checks, PowerShell syntax, default packaging and opt-in native-surface packaging. Documentation-only commits do not trigger that source workflow.

These are prior results, not a claim that Claude reran them. Robolectric tests replace parts of playback/storage, manually deliver callbacks and use fake players/sessions. They cannot prove real compositor ordering/colors, concurrent decoder capacity, native allocation stability, actual network performance or sustained TV/phone/Float behavior. The faster handoff has limited runtime lifecycle evidence; physical switch-speed/color confirmation and a sustained memory comparison remain open. No wider rollout or visual success should be inferred from this handoff.

Useful tests include `CameraTileTest`, `FullscreenLayoutTest`, `StartupCoordinatorTest`, `SharedVideoRuntimeTest`, `MultiMotionLayoutTest`, `ViewSelectorTest`, `WallStreamPlanTest`, `DoorbellGeometryTest`, `PanoramicPairGeometryTest`, `MqttBridgeTest`, `CommandInboxTest`, `CommandRouterTest`, `SettingsCipherTest` and `PhoneUiTest`.

For a fresh, isolated checkout, follow `.github/workflows/android.yml` for SDK/JDK setup and a **disposable review-only debug key**. The bundle contains no SDK, caches, local SDK path or signing key. Do not overwrite an original keystore, replace settings-encryption aliases, or install a disposable-key APK over a configured device. `bootstrap-android.ps1` downloads tools and accepts SDK licenses; read it before using it in an approved environment. Dependency bootstrap may need internet; `--offline` works only with an already populated cache.

With the existing approved Windows toolchain, generated output can be directed outside OneDrive:

```powershell
$reviewOutput = Join-Path $env:TEMP 'property-tracker-claude-review-build'
.\scripts\build-android.ps1 -Tasks @(
  '--offline',
  ':app:testDebugUnitTest', ':app:assembleDebug', ':app:lintDebug',
  ':mobile:testDebugUnitTest', ':mobile:assembleDebug', ':mobile:lintDebug'
) -BuildOutputRoot $reviewOutput
python -m unittest discover -s scripts -p 'test_*.py'
python scripts/verify-debug-apks.py `
  --aapt .tools/android-sdk/build-tools/35.0.0/aapt.exe `
  --build-root $reviewOutput
```

In an isolated Linux environment with the required SDK and disposable signing key, use Gradle 8.11.1 and the same six module tasks directly; then run the Python checks and package verifier as CI does. Do not run deployment/monitoring scripts or use ADB to satisfy host checks. If a prerequisite is missing, report which checks were not run rather than changing production settings or claiming validation.

## Expected review output

Start with the most consequential supported findings. For each, provide severity, precise file/line references at the reviewed revision, a concrete trigger and user impact, the ownership/race/logic explanation, confidence and evidence, a minimal fix proposal, and an effective regression/validation plan. Keep confirmed defects separate from performance hypotheses, upstream limitations and experiments requiring device access. Do not invent a finding quota or present cosmetic preferences as failures.

Finish with checks actually run, gaps that require an interactive device window, and a short ordered improvement plan. If no supported findings emerge, say so and explain the residual limits. Return findings before modifying code; changes and any device test/deployment can be agreed separately.

The curated source bundle intentionally excludes live inventories, private diagnostics/settings, signing material, credentials, APKs, app-store work and the obsolete browser prototype. Its manifest identifies the pinned source files and their hashes. The source bundle is for review; it is not a deployable or signed release artifact.
