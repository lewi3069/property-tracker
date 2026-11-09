# LibVLC Android 3.7.7

The TV target uses the unmodified `org.videolan.android:libvlc-all:3.7.7` Maven Central artifact for its optional dense-grid player. The phone target does not package this dependency. ARMv7 and ARM64 libraries are included in the TV APK.

- [Published artifact and version metadata](https://central.sonatype.com/artifact/org.videolan.android/libvlc-all/3.7.7)
- [Exact-version Java source archive](https://repo.maven.apache.org/maven2/org/videolan/android/libvlc-all/3.7.7/libvlc-all-3.7.7-sources.jar)
- [SDK source and native build scripts](https://code.videolan.org/videolan/libvlcjni)
- [VLC core source](https://code.videolan.org/videolan/vlc)
- [License](COPYING.LIB) and [upstream POM](libvlc-all-3.7.7.pom)

VideoLAN and its contributors retain their copyrights. LibVLC is licensed under LGPL 2.1. Its native build contains additional upstream components identified by the upstream sources/build scripts. Property Tracker's own source and Gradle configuration are available for rebuilding/relinking, including against a modified library. The app's About screen includes the third-party notice and license text.

The upstream Android release tag [`libvlc-3.7.7`](https://github.com/videolan/vlc-android/tree/libvlc-3.7.7) pins the VLC 3.x SDK revision `0b8dc65efb203c86a0476bc337ddd99ecf1c0ef6` in `buildsystem/compile.sh`. Its [SDK source and native build scripts at that revision](https://code.videolan.org/videolan/libvlcjni/-/tree/0b8dc65efb203c86a0476bc337ddd99ecf1c0ef6) identify and fetch the VLC core and contributed sources. Use the upstream build instructions for source dependencies and native rebuilds; the Java sources archive alone does not contain the VLC core.
