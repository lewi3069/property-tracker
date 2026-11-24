# Property Tracker for Pixel and Android phones

The native phone APK is `property-tracker-phone-0.2.0-debug.apk`, package `com.fieldview.mobile`, Android 8/API 26 or newer. It shares camera playback, encrypted settings, and MQTT command handling with the TV app. The TV package, signing identity, landscape interface, and rectangular launcher artwork remain separate.

The Play release candidate is 0.2.1, targets Android 16/API 36, and uses its own production ID `com.lewi3069.propertytracker`. The legacy debug ID is already registered to an unrelated Play app. The production app can be installed alongside that debug build and has its own settings; existing debug/TV installations are not replaced. No Play publication has occurred yet. See [submission preparation](../store/play/submission-notes.md).

Install the phone APK from the [0.2.0 release](https://github.com/lewi3069/property-tracker/releases/tag/v0.2.0). Open **More → Cameras** to add cameras. This APK contains no camera addresses, passwords, or broker credentials. Existing TV settings are not automatically copied to the phone. Reach private camera addresses through the appropriate local network or VPN.

- **Browse / Grid** opens on launch. In portrait, scroll through the camera list. In landscape, Grid keeps the overview fixed.
- **Auto** follows Home Assistant activity. In portrait, Auto gives 4:3 doorbells taller rows and panoramic Duo feeds shorter rows. It estimates 16:9 until it learns a camera's picture shape. Auto shows three cameras when their rows fit at full width. In a tighter space, it shows the two highest-priority feeds and scales them together to preserve their proportions.
- **View** or tapping a tile: enlarge a selected camera. Portrait uses stacked or compact grids; landscape uses the wider layouts.
- **Fullscreen quality:** with **Use the main stream in single-camera full screen** on, the one pinned camera plays its RTSP main stream through LibVLC with hardware decoding first. Browse, Auto, substreams, and Float stay on Media3. If the main stream fails to start, reports an error, or stalls, that camera switches once to its separate grid stream and shows **Grid stream · main unavailable** until it is opened again, which retries the main stream. The phone APK now packages LibVLC for four CPU types (ARMv7, ARM64, x86, x86-64).
- **One pinned camera:** Pinch to zoom from the 1× fit up to 4×. Drag with one finger to pan while zoomed. At 1×, swipe up for the next enabled camera or down for the previous one. Long-press for **More**. Android Back returns to the previous layout and scroll position. Zoom resets when you change cameras or leave the single-camera view. It carries over if the main stream falls back to the grid stream. A fullscreen multi-camera view shows **Back / More** on entry, and the bar hides after five seconds.
- **More → Settings** shows the viewer status and provides the layout and stream controls.
- **More → Float camera:** uses the shared silent overlay service after Android's display-over-other-apps permission is granted. Return to Property Tracker to close it; screen-off also ends the session. This is an overlay, and native Android picture-in-picture is not implemented. Phone overlay playback and notifications still need physical-device validation.
- **More → Home Assistant:** optional MQTT settings. Use a unique phone screen ID; its initial default is `phone`. TV control buttons are not included in this direct-viewing app.

The app supports both orientations, restores the selected camera after Activity recreation, and keeps form actions within narrow displays. Robolectric tests on Android 15/API 35 and Android 16/API 36 exercise browsing, zoom, pan, stream fallback, and selection restoration. Live video rendering and gesture behavior still need device verification.

Build only the phone target with `scripts/build-android.ps1 -Tasks :mobile:assembleDebug`. The default build checks both app modules.

In 0.2.1, **More → Privacy policy** works offline. Notification permission is requested once when starting Float on Android 13+, and declining it does not block viewing. Explicit phone backup exclusions cover cloud backup and device transfer. Phone UI regressions run on Android 15 and 16 using Java 21.
