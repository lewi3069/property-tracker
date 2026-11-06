# Property Tracker artwork

The selected design is **option B**: a small camera mounted on the house, with a pine tree, dog, and horse. [Prompts and alternatives](camera-icon-alternatives.md).

- `property-tracker-option-b-house-camera.png`: selected master.
- `property-tracker-icon.png`: 512px square export.
- `property-tracker-tv-banner.png`: 1280 × 720 rectangular TV tile with the app name.
- `property-tracker-property-scene-preview.png` and `property-tracker-property-scene-banner.png`: current matching previews.
- The original scene and option A are retained as earlier concepts. The `.svg` files describe the earlier geometric design, not the selected raster artwork.

Run `scripts/generate-branding.ps1` on Windows. Its exporter resizes option B and composes the banner using Windows Segoe UI Semibold; no font is bundled. All resources are opaque PNGs at five densities:

| Resource | Dimensions, mdpi through xxxhdpi |
| --- | --- |
| `mipmap-*/ic_property_tracker.png` | 80², 120², 160², 240², 320² |
| `mipmap-television-*/ic_property_tracker.png` | 160×90, 240×135, 320×180, 480×270, 640×360 |
| `drawable-*/tv_banner.png` | Same five rectangular sizes |

The manifest points both `android:icon` and `android:banner` directly to the rectangular `tv_banner` artwork on the application and the existing `MainActivity` launcher component. This also supplies a rectangle when Fire TV resolves the icon from a context that does not apply the `television` resource qualifier. Square and TV-qualified mipmaps are retained as source assets, but are not the active manifest icon. See [Android resource qualifiers](https://developer.android.com/guide/topics/resources/providing-resources) and [TV banner guidance](https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines).

Fire TV caches thumbnails separately; actual Home rendering needs verification after installation. Earlier refresh observations are in [testing](../../docs/testing.md#version-0114-fire-tv-icon-compatibility). Notification artwork retains its separate monochrome glyph. `-LegacyArtwork` regenerates the older SVG design and is not the current export path.
