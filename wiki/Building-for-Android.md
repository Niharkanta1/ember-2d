# Building for Android

How to build, install, test, sign and ship an Ember2D game for **Android 6.0 (API 23) and newer**. Specs F-020, F-022.

---

## 1. Prerequisites
| Tool | Version | Notes |
| --- | --- | --- |
| JDK | 21 | Gradle and the Android Gradle plugin (9.5.2) run on it |
| Android SDK | platform 36, build-tools 36.0.0 | `sdkmanager "platforms;android-36" "build-tools;36.0.0"` |
| SDK location | `ANDROID_HOME`, or `local.properties` with `sdk.dir=...` | never commit `local.properties` |
| adb | platform-tools | install, logs, screenshots |
| A device or emulator | API 23+ | the demos were checked on an API 23 x86_64 emulator image |

The build doesn't download SDK parts by itself (`android.builder.sdkDownload=false` in `gradle.properties`). Install them with `sdkmanager`.

**An emulator for testing:**
```sh
sdkmanager "system-images;android-23;default;x86_64" "emulator"
avdmanager create avd -n ember-api23 -k "system-images;android-23;default;x86_64"
emulator -avd ember-api23 -no-snapshot-load
```

## 2. How the Android module is set up
`demos/<game>/android/build.gradle`, from the demos:

| Setting | Value | Notes |
| --- | --- | --- |
| `namespace`, `applicationId` | `dev.ember2d.demo.<game>` | change both for your game |
| `minSdk` / `targetSdk` / `compileSdk` | 23 / 36 / 36 | from `gradle/libs.versions.toml` |
| `versionCode` | `MAJOR*10000 + MINOR*100 + PATCH` | `1.2.3` → 10203; a `-SUFFIX` is ignored; MINOR and PATCH below 100 |
| `versionName` | the Gradle version | |
| Assets | `assets.srcDirs += 'demos/<game>/core/assets'` | the same files as desktop |
| Natives | libGDX and FreeType `.so` for `arm64-v8a`, `armeabi-v7a`, `x86_64` | extracted by `copyAndroidNatives`; 32-bit x86 is not shipped |
| Java | source and target 17, `coreLibraryDesugaring` on | |
| Release | `minifyEnabled true` (R8), `proguard-rules.pro` + generated keep rules | |
| Signing | the upload key from `ember.signing.*` properties, else the debug key | section 6 |

**The manifest** (`android/src/main/AndroidManifest.xml`):
```xml
<uses-feature android:glEsVersion="0x00020000" android:required="true" />
<uses-permission android:name="android.permission.VIBRATE" />
<application android:allowBackup="true" android:appCategory="game" android:isGame="true"
    android:label="@string/app_name" android:theme="@style/EmberTheme">
  <activity android:name=".AndroidLauncher" android:exported="true"
      android:screenOrientation="sensorLandscape"
      android:configChanges="keyboard|keyboardHidden|navigation|orientation|screenSize|screenLayout|smallestScreenSize|uiMode">
    <intent-filter> <action android:name="android.intent.action.MAIN" />
                    <category android:name="android.intent.category.LAUNCHER" /> </intent-filter>
  </activity>
</application>
```
- **Permissions:** only `VIBRATE`. The engine needs nothing else; saves use app-private storage. Add permissions only for features you add.
- **Orientation:** landscape with sensor rotation. Change `screenOrientation` for portrait games, and set the virtual resolution to match (for example 180 × 320).
- **App icon:** the demos have **none** (the default system icon). Add `res/mipmap-*/ic_launcher.png` (or an adaptive icon) and `android:icon="@mipmap/ic_launcher"` before you publish.
- **Name:** `res/values/strings.xml` → `app_name`.

**The launcher:**
```java
public class AndroidLauncher extends EmberAndroidLauncher {
    @Override
    protected EngineConfig config(boolean devMode) {      // devMode = the APK is debuggable
        EngineConfig config = MyGame.config(devMode, versionName());
        config.debugOverlayVisibleAtStart = devMode;      // phones have no F3 key
        return config;
    }
}
```
`EmberAndroidLauncher` handles suspend and resume, GL context loss, safe areas (cut-outs) and vibration.

## 3. Debug builds
```sh
./gradlew :demos:my-game:android:assembleDebug     # → demos/my-game/android/build/outputs/apk/debug/android-debug.apk
./gradlew :demos:my-game:android:installDebug      # build + install on the connected device
adb shell am start -n com.example.mygame/.android.AndroidLauncher
```
Debug builds run in dev mode: `DEBUG` logs and the F3 overlay from the start. There are no dev tools or hot reload on Android.

## 4. Test and debug on a device
```sh
adb devices
adb install -r demos/my-game/android/build/outputs/apk/debug/android-debug.apk
adb logcat -c
adb shell am start -n com.example.mygame/.android.AndroidLauncher
adb logcat -s core:* scene:* assets:* AndroidRuntime:E          # your game's categories too
adb exec-out screencap -p > screen.png
adb shell input tap 960 540                                       # scripted taps for smoke tests
adb shell input swipe 300 850 450 850 1500                        # drag the joystick for 1.5 s
adb shell am force-stop com.example.mygame                        # simulate the system killing the app
adb shell pm clear com.example.mygame                             # wipe saves and settings
```
- Start apps with `am start`. `adb shell monkey` injects a random touch.
- **Suspend and resume check:** press HOME, then reopen. The log shows `Suspended` and `Resumed`, and the game state is untouched.
- **Save and kill check:** save, `force-stop`, start again and Continue. The demos pass this.
- Logs use the engine's categories as tags; `AndroidRuntime:E` shows crashes.

## 5. Release builds
```sh
./gradlew :demos:my-game:android:assembleRelease   # minified APK → build/outputs/apk/release/android-release.apk
./gradlew :demos:my-game:android:bundleRelease     # AAB for Google Play → build/outputs/bundle/release/android-release.aab
```
- **R8:** release builds are minified with R8. The keep rules have three parts:
  - **libGDX's:** in `proguard-rules.pro`.
  - **Generated:** the `emberKeepRules` task runs your `ComponentSetup` on every build and writes `build/generated/ember-keep-rules.pro`. Registered components keep their names and fields, so prefabs and maps work after minification. **An unregistered component works in debug but breaks in release.**
  - **Line numbers:** `-keepattributes LineNumberTable,SourceFile` keeps stack traces readable with the mapping file.
- **Mapping file:** `build/outputs/mapping/release/mapping.txt`. Keep it for every build you publish; upload it to Google Play to de-obfuscate crash traces.
- `./gradlew build` also builds the release APKs, signed with the debug key unless an upload key is set.

**Verified:** the release (R8) APKs of the platformer, RPG and novel demos have run on the API 23 emulator:
- The platformer was checked through menus and pause.
- The RPG and novel demos were checked through their opening conversations, with blade-ink after minification.

The shooter's release APK builds but has not been run.

## 6. Signing
Without signing properties, release builds are signed with the **debug key**, and the build warns `no upload key`. That is fine for testing and side-loading, but Google Play rejects it.

**Create an upload key once**, and keep it out of the repository:
```sh
keytool -genkeypair -v -keystore ember-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```
**Tell Gradle** in `~/.gradle/gradle.properties` (your user folder, not the project):
```properties
ember.signing.storeFile=C:/keys/ember-upload.jks
ember.signing.storePassword=...
ember.signing.keyAlias=upload
ember.signing.keyPassword=...
```
On CI, pass them as environment variables: `ORG_GRADLE_PROJECT_ember.signing.storeFile` and so on. All four must be set, or the debug key is used.

**Google Play App Signing:** Play holds the app signing key; you sign uploads with your upload key. Back up the keystore and its passwords. Losing the upload key means asking Google to reset it.

## 7. Publish on Google Play
1. Create the app in the Play Console with your `applicationId`.
2. Raise the version in the root `build.gradle`, for example `1.0.0` → version code 10000. Every upload needs a higher version code.
3. `./gradlew :demos:my-game:android:bundleRelease` with the upload key set.
4. Upload `android-release.aab` and its `mapping.txt` to an internal testing track first.
5. Fill in the store listing, content rating and privacy details. Ember2D collects no data, and the only permission is `VIBRATE`.
6. Test the Play-delivered build on real devices (internal track), then promote it.

Other stores and side-loading: distribute the signed `android-release.apk` from `assembleRelease`.

## 8. CI
`.github/workflows/ci.yml` builds and tests everything on every push and pull request, and uploads the platformer's debug APK. On pushes to `main` it also builds the platformer's release AAB with its mapping file. No upload key is configured in CI, so that bundle is debug-signed and only for testing. To add your game, copy those steps with your module path.

## 9. Android-specific rules for your code
- **Java APIs on API 23:** shared code (your game's `core`, the kits, the engine) may only call Java APIs that exist on Android 6. `tools/android-api-check` fails the build otherwise; for example, `String.lines()` and `String.chars()` are rejected. New libraries used by shared code must be added to that check's classpath.
- **Files:** no `java.nio.file`, `java.awt`, threads or `System.exit`. Use assets and saves.
- **Saves:** use `config.saveFolder` (app-private storage) and `saves.setSuspendSave(...)`, because Android may kill a backgrounded app.
- **Touch:** see [Input](Input) for on-screen controls, and `ui.dp()` for finger-sized UI.

## 10. Troubleshooting
| Symptom | Cause | Fix |
| --- | --- | --- |
| `Failed to find Build Tools revision 36.0.0` | build-tools missing | `sdkmanager "build-tools;36.0.0"` |
| `requires ... compile against version 36` | platform 36 missing | `sdkmanager "platforms;android-36"` |
| `Call requires API level NN (current min is 23)` | a newer Java API in shared code | use an API 23 alternative |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | switching between debug and release signatures | `adb uninstall <applicationId>` first |
| The app closes at start | an exception | `adb logcat -d -s AndroidRuntime:E core:*` |
| A component's data is ignored only in release | not in your `ComponentSetup` (no keep rule) | register it |
| The release bundle is rejected by Play | debug-signed | set the `ember.signing.*` properties |
| Progress lost after the app was in the background | Android killed the process | save at checkpoints; `setSuspendSave` |
| The game shows under a camera notch | layout ignores safe areas | `ui.safeInsets()`; `renderer.safeArea()` |
| Black screen after returning to the app | a GL object you created yourself wasn't recreated | create GL resources through the engine (assets), or recreate them on resume |
