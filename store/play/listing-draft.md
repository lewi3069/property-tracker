# Property Tracker — Google Play listing draft

Status: prepared for owner review; no Play submission or publication has occurred.

App name: Property Tracker

Short description (79 characters):

View your IP cameras with flexible layouts and Home Assistant motion switching.

Full description:

Property Tracker brings your compatible IP cameras into one native Android viewer. Connect directly to the RTSP cameras you configure, including compatible Reolink and Amcrest models, and choose a view that suits your screen.

Use a camera grid or enlarge one camera. Touch controls make it easy to browse cameras in fullscreen, and layouts adapt to portrait and landscape. Copy camera entries, set their default order, and enable or disable them without losing their saved details.

Optional Home Assistant control uses your MQTT broker. Activity can feature a camera in the active viewer. In an already running floating session, the camera you selected is the default: activity temporarily changes the view, and clearing activity or its timeout restores the default. Home Assistant automations and camera-to-sensor mappings must be configured separately.

Choose a floating camera to keep one silent live view over another app, with Android's display-over-other-apps permission. Returning to Property Tracker or turning the screen off ends that floating session.

Camera and broker settings are stored encrypted on your device. This app has no developer-hosted video cloud, advertising, or analytics service. It does not include camera accounts or discover your cameras automatically.

You need compatible cameras and network access to them, such as local Wi-Fi or a VPN. H.264 and H.265/HEVC support depends on your device's decoding capabilities and the camera stream's format. Start with fewer feeds when testing a new device. This is a viewer, not an NVR or recording service.

Suggested category: House & Home. Confirm final category, free/paid choice, countries, audience, and public support email in Play Console.

Proposed permanent Play package: `com.lewi3069.propertytracker`. The old debug phone package `com.fieldview.mobile` is already registered to an unrelated Field View app on Google Play and cannot be used for our new listing. Debug builds retain that old local package, and the TV package is unchanged. Confirm the new Play identity's availability in Console before first upload. Release candidate: 0.2.1 / code 22 / target API 36 / minimum API 26. The Play package installs separately from the debug phone app and starts with its own configuration; no existing installation must be uninstalled to test it.

Privacy page source: `docs/privacy.html`, generated from the policy included in the app. Intended hosted URL: `https://lewi3069.github.io/property-tracker/privacy.html`; verify it is publicly available before entering it in Play Console.

Artwork available: `assets/branding/property-tracker-icon.png` (512 × 512). A compliant feature graphic, real screenshots, and any foreground-service demonstration video remain to be prepared/verified for public release. Do not use private camera footage or credentials in review materials without the owner's explicit authorization.
