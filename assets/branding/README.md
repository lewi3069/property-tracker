# Property Tracker artwork

The mark combines a property outline with a camera, using the application's dark background, sage green, and mint accent. The square icon omits text for small launcher sizes; the TV banner includes the app name.

- `property-tracker-icon.svg`: editable vector master for the legacy square icon.
- `property-tracker-icon.png`: 512 × 512 preview/export.
- `property-tracker-tv-banner.svg`: vector banner with an outlined wordmark.
- `property-tracker-tv-banner.png`: 1280 × 720 preview/export.

The APK uses PNG launcher icons at all five densities, from 80 × 80 (mdpi) through 320 × 320 (xxxhdpi). A single bitmap resource name, `@mipmap/ic_property_tracker`, is declared on both the application and launcher activity. Version 0.1.14 removes runtime vector/adaptive icon alternatives so icon extraction does not depend on XML drawable support. Editable vector geometry remains in the SVG masters and generation script.

Fire TV can retain old artwork independently of Android's current icon resource. On the tested device, Android Package Manager and the installed-app library rendered the new artwork while Home was blank and the app grid retained the original placeholder. Refreshing the local library entry restored Home; an Appstore cache-only refresh and a brief app disable/re-enable subsequently restored the app grid. See [the device results](../../docs/testing.md#version-0114-fire-tv-icon-compatibility).

The static TV banner is exported at all five densities from 160 × 90 (mdpi) through 640 × 360 (xxxhdpi), matching [Android's TV icon guidelines](https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines). Baking the wordmark into these images avoids expensive font-outline paths in the launcher. The SVG master retains the outlines for future exports.

Regenerate all files on Windows with:

```powershell
.\scripts\generate-branding.ps1
```

The script uses Windows' Segoe UI Semibold to create the banner wordmark as paths. No font file is distributed or required at runtime. Icon, banner, and previews share the same geometry and colors. The manifest references `@mipmap/ic_property_tracker` and `@drawable/tv_banner`; the notification keeps its separate monochrome camera glyph.
