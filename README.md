# Bulletin for Android 1.37

Standalone source project for the offline Android Bulletin app. Minimal (Low) energy uses a 7-minute quest and a 2-minute rest.

## Build

Open this folder in Android Studio and let it sync. Install Android SDK Platform 36 and SDK Build-Tools 35.0.0 when prompted. Use JDK 17 or Android Studio's compatible Gradle JDK. The Gradle wrapper downloads Gradle 8.13; the first build needs internet to download dependencies.

Android Studio creates local.properties for your SDK location. For a command-line build, set ANDROID_HOME to your Android SDK directory and JAVA_HOME to your JDK, then run:

Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

macOS/Linux:

```sh
sh ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The APK is generated at app/build/outputs/apk/debug/app-debug.apk. Application ID: com.bulletin.app. Version code: 38. Minimum Android API: 26.

## Contents

- app/src: Java source, Android manifest/resources, offline emoji assets and their Unicode license, and unit tests.
- app/build.gradle and root Gradle files: Android build configuration and dependencies.
- gradle/wrapper, gradlew, gradlew.bat: Gradle wrapper, including its required JAR.
- .gitignore: excludes build outputs, caches, local SDK paths, APKs and signing keys.

Upload the contents of this folder as your GitHub repository root. No downloaded SDK/JDK, generated files or existing APKs are needed in GitHub.

## Signing and updates

This source export intentionally excludes signing keys. A new computer normally generates a different debug signing key, so an APK built there cannot update an existing installation signed with the original key. Keep the original signing key privately if you need updates that preserve existing app data. Do not commit it to GitHub.
Verified from this standalone folder: debug APK build, all 211 unit tests, and Android lint (no errors).

Version 1.34 corrects column in the heatmap description, starts analytics categories expanded with independent saved collapse states, and adds a small color-only reset symbol beside the Settings hue slider.

Version 1.35 adds separate quest Pause/Cancel actions, Cancel Quest in paused menus, midnight reset for paused attempts, and Today/Week/Month/3 Months/Year range buttons for both quest and law pie/bar charts.

Version 1.36 includes the supplied OGG law alarm, Cancel Quest label, a saved two-handle balance date range with five-year retention, maximum-compression saves compatible with legacy JSON, and 30 dated backups on opening/backgrounding and every two continuous foreground hours. Settings supports confirmed backup restores and copy/paste export/import. A current-state backup is required before a restore or import; invalid saves are rejected before replacement. Exports contain gameplay data, not the separate backup archive.

Version 1.37 selects the shortest of B1 and 24 DEFLATE/LZMA/LZMA2 combinations, optionally using lossless string dictionary/integer delta packing and a second compressor. B3 uses denser printable encoding, length validation and CRC32; plain JSON/B1/B2 saves remain supported. Regular saves commit immediately and refine in the background with protection against stale overwrites. Exports and snapshots use the full search. The test save shrank from 1,554 to 1,018 characters; actual savings depend on data. Backups/Export/Import now sit above Balance, and the dropdown says Backups without a count.

Additional dependency: org.tukaani:xz:1.12 (pure Java, 0BSD), https://tukaani.org/xz/java.html . Gradle downloads it on the first build.
