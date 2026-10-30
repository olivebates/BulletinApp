# Bulletin for Android 1.33

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

The APK is generated at app/build/outputs/apk/debug/app-debug.apk. Application ID: com.bulletin.app. Version code: 34. Minimum Android API: 26.

## Contents

- app/src: Java source, Android manifest/resources, offline emoji assets and their Unicode license, and unit tests.
- app/build.gradle and root Gradle files: Android build configuration and dependencies.
- gradle/wrapper, gradlew, gradlew.bat: Gradle wrapper, including its required JAR.
- .gitignore: excludes build outputs, caches, local SDK paths, APKs and signing keys.

Upload the contents of this folder as your GitHub repository root. No downloaded SDK/JDK, generated files or existing APKs are needed in GitHub.

## Signing and updates

This source export intentionally excludes signing keys. A new computer normally generates a different debug signing key, so an APK built there cannot update an existing installation signed with the original key. Keep the original signing key privately if you need updates that preserve existing app data. Do not commit it to GitHub.
Verified from this standalone folder: debug APK build, all 181 unit tests, and Android lint (no errors).
