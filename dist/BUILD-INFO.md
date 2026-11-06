# Property Tracker 0.2.0

Two native targets share Java sources and dependency/signing configuration. Version code21, target API35. TV retains com.fieldview.tv / minimum API22; phone uses com.fieldview.mobile / minimum API26. No native .so libraries are packaged.

Validation:87 TV/core tests and3 Android15/API35 phone UI tests passed (90 total). Lint:0errors;28 TV warnings,40 phone warnings. TV passed v1/v2 signatures and phone passed v2 signature validation with certificate SHA256 `c453e50d5489f8c6a9fe40355d0107db44a13d30d35a6ed16df093ac775e93a9`.

- `property-tracker-0.2.0-debug.apk`: 6740709 bytes; SHA256 `d82af6ec73729179caaf2db678162182be7768da394a5c1de42eb1b288e234d9`
- `property-tracker-phone-0.2.0-debug.apk`: 5556314 bytes; SHA256 `c49e0acd26fe011b5997a8720387e9ba37e8b3d5895a5e5b106f32d37c66dcec`

No physical Pixel is connected; phone decoding/network/battery/overlay operation remains unverified. No live TV installation, navigation or Plex test was performed for0.2.0. Current TV installations remain0.1.19. Prior HEVC/Plex freezes, unbounded RTSP packet/native memory, synchronous configuration saves, and stale Fire TV Home artwork remain documented limitations.

See [phone setup](../docs/MOBILE.md) and [review](../docs/CODE-REVIEW-2026-11-06.md). Camera and broker credentials are not embedded in either APK.
