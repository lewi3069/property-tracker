# Playback validation

## November 3, 2026 comparison

Fieldview 0.1.10 (the development name preceding Property Tracker) was tested on a Fire TV Stick 4K 2018, model AFTMM, Fire OS 6 / Android API 25.

| Phase | Duration | Observed result |
| --- | --- | --- |
| Plex alone | 5 minutes | Same Plex process throughout; foreground at every sample |
| Plex plus one Amcrest IP2M-853E substream in Float | 15 minutes | Same Plex and camera-app processes; floating window present in all 60 samples |

Plex used the hardware AVC/H.264 decoder. Its source resolution and direct-play/transcoding status were not verified. There was a brief buffering state after switching apps to start Float; Plex subsequently reported playing. The operator confirmed that both pictures looked normal. Camera compositor timestamps continued advancing through the final sample. No intentional process termination, reboot, or installation occurred during this comparison; no process death, crash, or ANR was recorded for either app.

Available system memory varied from approximately 113 to 214 MiB during the combined phase. A background Amazon process became active during the run. These observations do not prove the cause of earlier terminations or establish longer-term stability, HEVC Plex coexistence, multiple-camera capacity, or compatibility with other hardware.

The 0.1.11 update changes the visible app name to Property Tracker and hides the camera-name bar only in Float. It preserves package identity, signing identity, stored camera data, stream selection, and the video surface. Its host checks include 49 unit/Robolectric tests and Android lint; the floating-title visibility assertion covers fullscreen styling changes as well.

## Follow-up freeze observed in 0.1.11

After installing 0.1.11 and restoring Float, the operator confirmed the name bar was gone but reported that playback froze. The camera process remained alive while its compositor presentation timestamps stopped advancing. No matching Java/native crash report was found. Property Tracker was then intentionally force-stopped to isolate the camera playback; Plex's process was left running. The precise cause and recovery behavior remain unverified. This follow-up means the successful 15-minute comparison above must not be treated as proof of reliable sustained Plex coexistence.

The ongoing frame-progress watchdog gap described in [the code review](CODE-REVIEW-2026-11-03.md) is relevant: the 0.1.11 watchdog monitors startup/buffering, but can miss a frozen stream that remains READY. This is a confirmed detection gap, not proof of the underlying freeze's cause. Version 0.1.12 adds continuing frame-submission monitoring; its limits are described below.

## Isolation follow-up

A later camera-only run kept the same Amcrest substream floating over a static Fire TV Settings screen for five minutes. Camera presentation timestamps advanced at every 15-second sample, the app process remained running, and sampled resident memory decreased rather than growing steadily. Plex had no active video session during this phase.

During reopening of the viewer after the previous freeze, the shared Android `media.codec` process crashed and restarted. Its native tombstone reports SIGSEGV in the MediaTek H.265 decoder (`libvcodecdrv.so`, including `Vdec_Drv_H265_decodeEx`). This establishes a platform decoder failure in this session. The crash timestamp is later than the reported freeze; it does not identify which client/stream caused it or prove the earlier freeze had the same cause. The isolated run began after the codec-service restart, which also limits direct comparison with the earlier run.

The operator reported that the TV interface remained responsive and manually restarted the video. This was a video-playback failure rather than a confirmed complete device/UI lockup. Further comparison should leave the floating camera running while starting Plex, identify the active codec paths, and avoid returning through the multi-camera overview during that phase.

## Resumed Plex comparison

With the isolated camera kept running, the operator unlocked Plex and resumed a video. Property Tracker 0.1.11 then completed a 15-minute comparison (901 seconds, 61 samples at approximately 15-second intervals):

- Both the camera and Plex compositor presentation timestamps advanced at every interval.
- The camera, Plex, shared codec service, and SurfaceFlinger each retained the same process ID throughout.
- Plex was foreground and reported playing at every sample; the floating window remained present.
- Camera resident memory ranged from approximately 29 to 37 MiB; available system memory ranged from approximately 112 to 180 MiB.
- Plex used the hardware AVC/H.264 decoder. The operator confirmed that both pictures looked normal.
- No intentional app restart, installation, or reboot occurred during the phase. Both apps were left playing afterward.

This is a successful observed coexistence run after the codec-service restart, with the same camera instance carried into Plex playback. H.264 here identifies Plex; subsequent decoder inspection identified the floating camera as HEVC. It does not resolve the earlier native H.265 failure, prove which stream triggered it, or validate transitions through the multi-camera overview. No playback-code fix was deployed for this retest. Longer sessions and targeted stream/transition checks remain necessary; sub-interval glitches are not ruled out by periodic frame samples.

## Later freeze and 0.1.12 compatibility trial

Playback froze again after the resumed comparison had ended, more than 30 minutes into coexistence. Both compositor timestamps stopped advancing while the camera app, Plex, codec service, and SurfaceFlinger retained their process IDs. No new matching crash was found. Camera resident memory had increased to approximately 98 MiB; this is an observation, not proof of which queue or component caused the freeze. Decoder/resource inspection identified the floating camera's secondary stream as HEVC at 640 × 480, while Plex used hardware H.264.

During a later viewer startup, SurfaceFlinger crashed in native graphics-buffer handling and the Android framework restarted. Subsequent players initialized decoders but produced no frames. The new 0.1.12 watchdog detected those 20-second stalls and retried; this did not restore the wedged platform. The device was intentionally rebooted before further comparison. Version 0.1.12 now stops after three unrecovered frame stalls rather than rebuilding players indefinitely.

Version 0.1.12 includes 61 passing host tests, zero lint errors, and 14 lint warnings. Its changes are:

- Monitor frame-submission progress after READY and require an actual rendered first frame during startup. Retry after 20 seconds without progress; reset retry history after 10 seconds of sustained frame progress.
- Stop automatic frame-stall recovery after three consecutive unrecovered stalls and ask the viewer to reopen the camera.
- Offer **Layout → Prefer software H.264 for floating video**, off by default. Only floating H.264 changes decoder preference; other formats and wall playback retain normal selection. Hardware fallback remains available if software initialization is unsupported.
- Log low-frequency frame counters, buffering, media-buffer allocation, and heap use without stream addresses or credentials.
- Allow an ADB setup launch with playback paused, so changing configuration or starting a single Float does not first initialize the overview: `am start -n com.fieldview.tv/.MainActivity --ez com.fieldview.tv.SETUP_ONLY true`.

These app changes cannot repair a wedged system codec or compositor. Frame metadata indicates renderer submission, which can continue even if the physical display has stopped; external compositor timestamps and operator confirmation remain necessary. The media-buffer metric excludes RTSP packet queues and native allocations.

The operator changed only the camera's secondary stream to H.264 for comparison. After reconnecting, 0.1.12 logged **OMX.google.h264.decoder** and **video/avc 640 × 480**; compositor timestamps advanced during the initial camera-only check. Plex initialized **OMX.MTK.VIDEO.DECODER.AVC**. The main recording stream was not changed.

The software-H.264 Float / hardware-H.264 Plex comparison completed **45 minutes** (2,701 seconds, 181 samples at approximately 15-second intervals):

- Camera and Plex compositor timestamps advanced at every sampled interval. Plex reported playing and the floating window was present at all 181 samples.
- Both apps, the codec service, and SurfaceFlinger retained their process IDs throughout. No intentional restart, installation, or app switch occurred during collection.
- No camera playback errors, watchdog stalls, or decoder-dropped frames were recorded.
- Camera resident memory ranged from approximately 53 to 67 MiB, ending at 53 MiB versus 66 MiB initially. Available system memory ranged from approximately 141 to 197 MiB.
- Reported camera buffering stayed between 943 and 1,032 ms. Media-buffer allocation was 192–256 KiB, and sampled Java heap use was approximately 13–17 MiB. These metrics exclude other RTSP queues and native allocations.
- The operator confirmed both pictures were normal at the beginning and again after 35 minutes. Both apps were left playing after collection ended.

This is one successful 45-minute run on the tested AFTMM, beyond the earlier failure window. Camera encoding, decoder preference, application version, and the platform restart differ from earlier runs; this comparison does not isolate the effect of each change or establish indefinite stability. It does not validate hardware-decoded H.264 Float, HEVC coexistence, other Plex encodings/resolutions, multi-camera transitions, or other devices. Periodic samples cannot rule out shorter glitches. Real watchdog recovery from a deliberate connection loss remains a separate check.

## Repeating a passive comparison

With ADB connected to the intended device, start the monitor and play a video normally:

```powershell
python .\scripts\monitor-firetv.py --serial DEVICE_ADDRESS:5555 --minutes 25
```

The monitor reads app/codec/compositor PIDs, process and global memory/swap, camera and Plex compositor timestamps, Plex playback state, foreground package, floating-window presence, and selected lifecycle logs. It never launches, stops, kills, installs, or reboots anything. Timestamped reports are written under ignored `.tools/diagnostics`. Use Ctrl+C to end early. Missing ADB responses must be distinguished from process death; use lifecycle evidence as well as samples. The named Plex surface is specific to the observed TV activity; a missing/differently named surface is not proof of a freeze.

Establish a Plex-only baseline, then repeat with one floating substream using the same video and playback settings. Record the time and affected component if a window disappears, playback freezes, an app exits, or the device restarts. Keep deliberate process-recovery tests separate and clearly announce any intentional interruption.

## Version 0.1.13 artwork update

Version 0.1.13 replaces the placeholder launcher art with a property-and-camera icon and a named TV banner. The icon has legacy and adaptive resources, including a monochrome foreground. The static banner is supplied at all five standard densities. SVG masters and PNG previews use the same geometry as the app resources; the banner wordmark is baked into the PNGs to avoid long vector paths at launcher render time.

Host checks passed: 61 unit/Robolectric tests, Android resource packaging, zero lint errors / 13 warnings, and APK signature verification with the maintained certificate. The manifest's icon/banner references and version code 14 were verified in the APK. No playback Java code changed. This APK was not installed or live-playback-tested during the artwork update; the 45-minute result above belongs to 0.1.12.

## Version 0.1.14 Fire TV icon compatibility

Version 0.1.13's installed-package checks passed, but they did not verify the actual Home tile. The operator subsequently reported missing artwork. On the tested AFTMM / Fire OS 6 / API 25 device, Home showed a blank tile and the app grid retained the original placeholder despite the newer APK being installed.

Version 0.1.14 ships only PNG launcher-icon variants, uses a new bitmap resource name, and explicitly declares the icon, label, and banner on the launcher activity. The application ID, signing certificate, encrypted-settings alias, and playback Java remain unchanged.

Final host validation: all 61 unit/Robolectric tests passed; resource packaging and APK signature verification passed; lint reported zero errors and 19 warnings. Five IconLauncherShape warnings concern the deliberately opaque square TV bitmaps, and one RedundantLabel warning concerns the explicit activity label. The other 13 warnings are unchanged.

Device validation confirmed the installed APK's SHA-256 matches the release and the encrypted settings file stayed byte-for-byte unchanged throughout the updates and registration refresh. The saved two-camera layout and software-H.264 Float preference remained selected. A separate read-only Package Manager probe loaded a BitmapDrawable from `res/mipmap-xhdpi-v4/ic_property_tracker.png` and rendered the new mark successfully.

The icon remained stale in Fire TV's catalog after in-place installation and an initial restart. Opening **Appstore → App Library → Installed on this TV** displayed the new artwork; launching that entry and returning to Home made the new Home icon visible. The app grid still held the old thumbnail after another restart. A cache-only Appstore refresh and a brief disable/re-enable of Property Tracker were then performed. Final captured checks confirmed the new mark in both Home and **Your Apps & Channels**, with the original six-app order preserved. Appstore data and camera settings were not cleared, and Property Tracker was enabled at completion.

These observations establish that Android could render the current icon while Fire TV retained older catalog artwork. They do not isolate the contribution of each bitmap/metadata/cache change. The tested refresh behavior is specific to this device; other Fire OS versions and NVIDIA Shield still need checks. No new sustained playback comparison was performed. The earlier 45-minute result belongs to 0.1.12.

## Version 0.1.15 full-screen camera layouts

Version 0.1.15 removes camera-name bars from ordinary grids as well as single-camera and floating views. Names remain available in the camera pickers and accessibility descriptions. **Layout → Full screen layout → Save** expands the current layout to the display, hiding the toolbar, footer, outer margins, and Live badges. Connection/error messages remain visible. Multi-camera layouts keep remote-focus borders and normal directional focus; pinned single-camera views retain Up/Down catalog browsing.

Back from a pinned camera restores its previous Auto/Grid mode and full-screen layout state. Back from an unpinned full-screen layout restores controls without recreating unchanged players. Full-screen presentation does not increase the selected feed count or switch ordinary grid tiles to main streams. The full-screen choice lasts for the viewing session and is retained through Activity state restoration.

Host validation on November 4, 2026: all 64 unit/Robolectric tests passed; lint reported zero errors and the existing 19 warnings. APK signature and package/version metadata were verified with the maintained signing certificate. The new behavior checks exercise 2-, 4-, and 9-camera placement across the full display, player reuse when restoring controls, pin/Back round trips, unconsumed grid-direction keys plus directional focus geometry, and the AFTMM four-camera limit. The tests replace video initialization and encrypted storage with local doubles; they do not open camera connections or demonstrate simultaneous decoder capacity. The existing floating-service test now checks the video/status-only child layout and retained camera accessibility description.

No Fire TV connection, installation, remote navigation, or live playback test was performed for this update because the operator requested that the current TV screen be left alone. Physical-device remote navigation and playback transitions for this version remain to be checked. The earlier 45-minute comparison belongs to 0.1.12.

## Home Assistant MQTT deployment — November 5, 2026

The existing Home Assistant MQTT entry was reconfigured through its supported UI to use the local Mosquitto app. A SolarAssistant bridge forwarded discovery/state traffic and retained the existing integration and entity identities. After the switch, all 216 active MQTT entities were available with known states.

The installed AFTMM viewer authenticated with a separate broker login. A non-retained QoS 1 command published by Home Assistant temporarily changed the saved two-camera Auto layout to one camera for twenty seconds. Native UI observations confirmed the one-camera temporary layout and its return to two cameras with MQTT still connected. The catalog showed 18 cameras; this setup changed no camera entries. No nine-camera playback, power-control actions, motion automation, horse-location automation, or Plex coexistence comparison was performed during this check.

The broker uses stock authenticated permissions rather than a topic-restricted TV ACL, and this LAN deployment uses TCP rather than TLS. Float does not currently receive MQTT commands. This short functional test does not establish sustained playback reliability.

## Version 0.1.18 older Fire TV support and artwork

Version 0.1.18 supports Android 5.1/API 22. AFTT installations are capped at two simultaneous cameras. Settings on API 22 use a random AES key wrapped by an RSA private key held in AndroidKeyStore; API 23+ retains the original AES Keystore alias and settings envelope. Both 96-bit and legacy 128-bit GCM nonces are accepted with a 128-bit authentication tag. Missing keys or failed authentication do not fall back to plaintext. Older API calls in form colors and overlay permission checks are guarded.

All 70 host unit/Robolectric tests passed, including authenticated-envelope tamper/wrong-key rejection, legacy nonce compatibility, nonce freshness, and the AFTT two-feed budget. Lint reported zero errors and 27 warnings. APK v1/v2 signatures and the maintained signing certificate were verified.

The final APK was installed on an AFTT/API 22 and an AFTMM/API 25. Each received seven direct cameras with credentials transferred through a temporary encrypted payload. Native setup checks verified the saved camera order, two-camera preference, distinct screen identity, and settings read/save/read persistence across processes. Existing encrypted settings remained unchanged during APK updates. Every grid stream rendered its first frame individually on each device. The AFTT uses grid streams for enlarged views as well. Temporary helpers and transfer files were removed. Both displays were asleep at completion and were left sleeping, so final foreground UI and physical-TV picture quality were not visually confirmed. These are short functional checks, not sustained or seven-camera simultaneous playback tests. The additional screens initially had standalone viewing only; MQTT was connected and tested afterward, as described below.

Option B is packaged at five icon/banner densities, with the rectangular bitmap declared directly as both app and launcher icon/banner. The original launcher component and application ID are retained. On the existing AFTMM installation, Home showed the older geometric artwork despite the new bitmap resources, then a placeholder after a registration refresh. Restarting the protected Fire TV launcher was denied by the OS. Home artwork remains unresolved; no launcher-data reset, app-data reset, root workaround, or uninstall of the main app was performed. Artwork in the App Library had previously rendered the selected design. Do not describe this release as a verified Home-icon fix.

## Additional-screen MQTT validation

The API 22 and API 25 installations were configured with the existing app-specific broker login and distinct screen/client IDs. Camera catalogs and unrelated settings were verified unchanged. Temporary native observers launched the actual main Activities and read their real layout/connection state while Home Assistant published non-retained QoS 1 commands. One screen changed 1→2→1 and the other 2→1→2 with fifteen-second expiries; each command left the other screen's saved layout unchanged. Both observers reported successful connection, temporary-layout delivery, and restoration. Helpers were removed after testing.

One sleeping screen authenticated then disconnected with its stopped Activity. It was briefly woken to complete the foreground test and returned to its original sleep state. MQTT remains foreground-only; the saved connection does not add background or floating-service control. No broker restart or new motion/location automation was required. All 216 active solar MQTT entities remained available with known states.

## Version 0.1.19 floating-view MQTT and motion

The floating service now owns an MQTT listener while its user-started session runs. The selected camera is the default; activity temporarily switches to another camera, and clear or expiry restores the default when no other activity remains. Competing motion waits while the current override remains active. Fresh heartbeats update expiry without restarting an unchanged player. Explicit MQTT commands select a new session default or close the window. Layout and horse-location commands retain their main-viewer scope. No TV wake, boot listener, or stopped-overlay opening was added.

All 81 unit/Robolectric tests passed; lint reported zero errors and the existing 27 warnings. New checks cover motion clear/expiry, concurrent activity, heartbeat stability, disabled/unknown cameras, duplicate/retained/stale commands, default selection, redelivery, late callbacks, and session closure. Service tests verify reuse of the same CameraTile and SurfaceView and separation of the viewer/service MQTT client identities. APK v1/v2 signatures and package/version/minimum API were verified with the maintained certificate.

The APK was installed in place on three Fire TVs; encrypted settings were byte-for-byte unchanged. On AFTMM/API 25 and AFTT/API 22, Home Assistant MQTT commands switched real H.264 grid streams, restored defaults, and closed the session. Native observation recorded first-frame callbacks and advancing rendered-buffer counters while retaining the same tile and SurfaceView. On AFTMM, a new remotely selected default was restored after an activity timeout. On AFTT, motion-clear returned to the default before the command's timeout. This is a short functional check; decoder counters do not establish physical-TV picture quality or renewed Plex stability.

Three existing screen automations were updated for the owner's selected motion sources. On/clear messages and ten-second continuing-motion refreshes use non-retained QoS 1 commands with thirty-second expiry. Native Core configuration validation passed. Only these three automation IDs were reloaded; the existing PTZ focus loop remained running with its original trigger time. The other 34 YAML automation rows were preserved byte-for-byte, and active MQTT entities had no unavailable/unknown states afterward. No broker restart was needed.

A subsequent floating session used one camera as default while an actual Home Assistant motion sensor was on. With no manual MQTT injections during that run, the live automation selected the matching camera, then restored the default when motion cleared. Video frame counters advanced in both views and the floating window/surface remained the same. All three automation entities were enabled. Temporary native helpers were removed; the older stick was returned to sleep, and the other sleeping installation was left asleep. Older-device long-running playback and the third screen's live floating playback remain unverified.

## Remaining validation

- Complete isolated foreground-service recovery and pending-restart cancellation on physical hardware.
- Longer Plex coexistence sessions, HEVC/high-resolution Plex, and other streaming apps.
- Additional Fire TV models and NVIDIA Shield.
- Broader camera-activity coverage, horse-location automations, TLS, and topic-restricted broker permissions.
- Resolution-aware stream budgets and RTSP backlog behavior under sustained decoder/network pressure.

ADB video captures can be green even when the physical TV picture is normal on the tested device. A first-frame callback or screenshot alone is insufficient visual confirmation.

## Version 0.2.0 review and native phone build

All87 TV/core tests and3 phone tests passed, with zero failures. Phone UI tests run against Android15/API35 and cover two-camera portrait stacking, touch fullscreen browsing, pinned-camera restoration after recreation, and narrow-screen edit actions. Core additions cover subscription acknowledgement/rejection, initial-connect backoff, missing-callback deadlines, idempotent shutdown and late callbacks, bounded1000-message bursts, stopped inboxes, and catalog edits preserving unchanged players/manual pins. Both signed APKs build successfully. Lint reports0errors,28 TV warnings and40 phone warnings (including existing artwork/style/backup guidance and compatibility guards shared with the older-TV code).

The current Home Assistant automation rules were read read-only and use the existing activity/activity_end contract, including combined Reolink classifications. No production automation, camera, broker or TV runtime was modified during this review. No real Pixel was connected. No new physical-phone playback, TV stress, or Plex coexistence result is claimed. See the current reliability review for unresolved RTSP/native memory and synchronous settings work.
