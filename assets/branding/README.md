# Property Tracker artwork

The mark combines a property outline with a camera, using the application's dark background, sage green, and mint accent. The square icon omits text for small launcher sizes; the TV banner includes the app name.

- `property-tracker-icon.svg`: editable vector master for the legacy square icon.
- `property-tracker-icon.png`: 512 × 512 preview/export.
- `property-tracker-tv-banner.svg`: vector banner with an outlined wordmark.
- `property-tracker-tv-banner.png`: 1280 × 720 preview/export.

The APK uses native vector drawables for its square icon. The API 26+ launcher icon separates the foreground mark from its background and supplies the monochrome foreground for launchers that support theming; older devices use the complete square drawable. The foreground fits within the central safe area. See [Android's adaptive icon format](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive).

The static TV banner is exported at all five densities from 160 × 90 (mdpi) through 640 × 360 (xxxhdpi), matching [Android's TV icon guidelines](https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines). Baking the wordmark into these images avoids expensive font-outline paths in the launcher. The SVG master retains the outlines for future exports.

Regenerate all files on Windows with:

```powershell
.\scripts\generate-branding.ps1
```

The script uses Windows' Segoe UI Semibold to create the banner wordmark as paths. No font file is distributed or required at runtime. Icon, banner, and previews share the same geometry and colors. The manifest references `@mipmap/app_icon` and `@drawable/tv_banner`; the notification keeps its separate monochrome camera glyph.
