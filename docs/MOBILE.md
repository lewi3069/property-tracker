# Property Tracker for Pixel and Android phones

The native phone APK is `property-tracker-phone-0.2.0-debug.apk`, package `com.fieldview.mobile`, Android 8/API 26 or newer. It shares camera playback, encrypted settings, and MQTT command handling with the TV app. The TV package, signing identity, landscape interface, and rectangular launcher artwork remain separate.

The Play release candidate is 0.2.1, targets Android 16/API 36, and uses its own production ID `com.lewi3069.propertytracker`. The legacy debug ID is already registered to an unrelated Play app. The production app can be installed alongside that debug build and has its own settings; existing debug/TV installations are not replaced. No Play publication has occurred yet. See [submission preparation](../store/play/submission-notes.md).

Install the phone APK from the [0.2.0 release](https://github.com/lewi3069/property-tracker/releases/tag/v0.2.0). Open **More → Cameras** to add cameras. This APK contains no camera addresses, passwords, or broker credentials. Existing TV settings are not automatically copied to the phone. Reach private camera addresses through the appropriate local network or VPN.

- **Auto / Grid:** follow Home Assistant activity or keep the overview fixed.
- **View** or tapping a tile: enlarge a selected camera. Portrait uses stacked or compact grids; landscape uses the wider layouts.
- Tap fullscreen video to reveal **Back / Previous / Next / More**. Controls hide after five seconds. Back returns to the previous layout. Android Back gestures are supported.
- **More → Layout:** choose the feed count and stream settings. Start with fewer feeds when testing a new phone.
- **More → Float camera:** uses the shared silent overlay service after Android's display-over-other-apps permission is granted. Return to Property Tracker to close it; screen-off also ends the session. This is an overlay, and native Android picture-in-picture is not implemented. Phone overlay playback and notifications still need physical-device validation.
- **More → Home Assistant:** optional MQTT settings. Use a unique phone screen ID; its initial default is `phone`. TV control buttons are not included in this direct-viewing app.

The app supports both orientations, restores manual camera selection after Activity recreation, handles Android 15 system-bar/cutout insets, and keeps form actions within a narrow display. Android 15/API 35 Robolectric tests cover phone layout, touch browsing, restoration, and edit actions. These checks do not demonstrate physical Pixel 9 Pro video decoding, camera-network access, battery consumption, overlay permissions, or sustained playback. A Pixel was not connected during this build.

Build only the phone target with `scripts/build-android.ps1 -Tasks :mobile:assembleDebug`. The default build checks both app modules.

In 0.2.1, **More → Privacy policy** works offline. Notification permission is requested once when starting Float on Android 13+, and declining it does not block viewing. Explicit phone backup exclusions cover cloud backup and device transfer. Phone UI regressions run on Android 15 and 16 using Java 21.
