# Play submission preparation

The release bundle and listing are preparation artifacts. Console acceptance, track choice, app access, support details, declarations, and final release still need completion and verification in the owner's account.

## Build and signing

Run `scripts/bootstrap-play-tools.ps1`, then `scripts/initialize-play-signing.ps1` once, then `scripts/build-play-release.ps1`. A separate upload key is stored under ignored `.tools/signing`; its random password is protected by the current Windows account using DPAPI. No private key or signing password belongs in Git. Google Play App Signing manages the distribution signing key; selecting that service and accepting its terms require the owner's review.

The production release ID is `com.lewi3069.propertytracker`. Only the release variant uses this ID. Existing TV (`com.fieldview.tv`) and historical debug-phone (`com.fieldview.mobile`) packages are unchanged. The latter ID is already registered to [an unrelated Field View app](https://play.google.com/store/apps/details?id=com.fieldview.mobile), so it cannot be used for our new Play app. The production app has independent local configuration and can coexist with the debug app.

Android 16/API 36 is the current new-phone-app target requirement as of November 6, 2026. Phone UI tests cover API 35 and 36, and Android 16's Robolectric runtime needs Java 21. TV keeps its API 22 minimum and API 35 target. Release output must be signed, non-debuggable, and validated with Google's bundletool. The validator already supplied by the Android Gradle plugin is used through `:mobile:validatePlayBundle`; no unverified validator download is required.

Use `-BuildOutputRoot` with a writable directory outside a synchronized workspace when needed. This is supported by both build scripts. `:mobile:dumpPlayManifest` prints the release bundle's actual manifest for verification.

The candidate has passed 87 TV/core, 12 phone debug and 12 phone release test executions; the phone cases cover Android 15/16. Lint has no errors. Both privacy and open-source notices are included as offline documents under More. The notices identify resolved runtime components and include Apache 2.0, Eclipse Distribution License 1.0, and the desugared Java library's GPL 2/ClassPath and assembly exception texts. Review and regenerate this inventory whenever runtime dependencies change.

## Privacy and Data safety facts to review

- No developer backend, advertising, analytics, or remote crash-reporting SDK is configured.
- Camera and broker details are stored encrypted with Android Keystore protection. The phone build explicitly excludes data from cloud and device-transfer backup.
- The app sends authentication/connection information to the camera and optional broker chosen by the user. It receives live video and control messages. Those endpoints can retain their own data.
- RTSP and `tcp://` MQTT are not necessarily encrypted. Do not select a blanket claim that all transmitted data is encrypted. `ssl://` MQTT uses certificate validation.
- The app does not record streams to files or read the phone's location, microphone, contacts, or camera.
- Do not infer Google's final Data safety classification merely from the absence of a developer backend. Review user-directed endpoint traffic, ephemeral processing, third-party library behavior, and the chosen distribution track against Google's definitions.
- No account is created with this app's developer. Deleting app data/uninstalling removes local settings; it does not erase footage or logs on the user's camera/broker.

Internal-only testing has different Data safety requirements from closed/open/production distribution. New personal developer accounts can have additional production-access testing requirements. Verify what applies in the actual Console; do not infer eligibility from a signed-in Google account.

## Permissions and reviewer access

The foreground-service type is media playback for the user's chosen live floating stream. The service is non-exported, silent, user-started, visibly overlays another app, and stops on return, close, or screen-off. Google may require a declaration and a demonstration video. Android notification permission is optional; denial does not block viewing. Display-over-other-apps permission is requested separately when Float is chosen. No full-screen-intent permission is requested.

Reviewers should not receive the owner's private camera/broker credentials or LAN addresses. Provide clear setup instructions and a suitable non-private review/demo access plan before public review. No physical Pixel playback, overlay, or reviewer-access demonstration has been completed yet.

## Official references

- [Target API requirement](https://support.google.com/googleplay/android-developer/answer/11926878)
- [Create and set up an app](https://support.google.com/googleplay/android-developer/answer/9859152)
- [Signing and Play App Signing](https://developer.android.com/studio/publish/app-signing)
- [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Personal-account testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465)
- [Foreground service declarations](https://support.google.com/googleplay/android-developer/answer/13392821)
