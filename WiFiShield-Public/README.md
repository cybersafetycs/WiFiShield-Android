# WiFiShield for Android

**Open-source developer security research.** WiFiShield is a WiFi
security scanner for Android  built to study, demonstrate and teach
real-world wireless risks on everyday networks. It is a companion of
the desktop WiFiShield.

**Contributions are welcome.** Found a bug, have an idea for a new
security check, better visualizations or clearer reporting? Open an
issue or a pull request  this project is meant to grow through the
community making it better.

- Package: `com.cybersafetycs.wifishield` · minSdk 26 (Android 8.0) ·
  targetSdk 34
- Screens: **MITANDAO** (network list + detail), **UCHAMBUZI** (analysis),
  **CAPTURE** (live signal graph), **GRAPHI** (all networks' signal
  curves in distinct colors  tap a curve or a name for the full-screen
  graph + details + back), **RIPOTI** (reports + in-app preview; each
  row offers VIEW / SHARE / DEL and the preview bar has DELETE  both
  ask for confirmation before removing the PDF),
  **HISTORIA** (raw scanner samples: time · dBm), **MAWASILIANO** (about)
- **Only scanner data**: every value on screen comes from
  `WifiManager.getScanResults()` (SSID, BSSID, dBm, frequency,
  capabilities → security/WPS/PMF, Wi-Fi standard, channel width) plus
  analysis computed from those fields. No ping, no DHCP/link info, no
  guessed values.
- **Firebase (all free features, silent in background)**: Analytics,
  Cloud Messaging (topic `wifishield`), Crashlytics, Remote Config,
  App Check, In-App Messaging, A/B Testing, App Distribution. Nothing
  Firebase-related is shown in the UI.

## Install (ready-to-install APK)

Grab the APK from [`releases/`](releases/) and install it on your
phone:

1. Copy `releases/WiFiShield.apk` to the phone (USB cable, cloud link,
   etc.).
2. On the phone open the APK → if asked, allow **Install unknown apps**
   for your file manager/browser → **Install**.
3. Launch **WiFiShield**. On first start grant **Location** (required
   by Android for Wi-Fi scanning), **Nearby devices**, and
   **Notifications** (for FCM pushes) if prompted. If scanning looks
   empty: Settings → Location → On (app location permission = "Allow
   all the time" or "While using the app").

## Source layout

```
src/com/cybersafetycs/wifishield/   all app code (Java 8)
src/cpp/prebuilt/<abi>/libwifishield.so   analysis engine (compiled)
app/proguard-rules.pro              release build rules
build.sh                            Gradle-free fallback build (no Firebase SDK)
```

The scoring engine ships as a compiled native library
(`libwifishield.so`, arm64-v8a / armeabi-v7a / x86_64). The app talks
to it through a thin JNI bridge (`NativeAnalyzer` → JSON), and
`Analyzer.java` parses that JSON into the same `Result` /
`ChannelResult` shapes the UI uses. Prebuilt `.so` files are checked in
 you do **not** need the NDK to build the APK.

## Build (Gradle  includes Firebase SDK)

```bash
export ANDROID_HOME=$HOME/Android/Sdk   # no local.properties is committed
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
G=$(echo ~/.gradle/wrapper/dists/gradle-8.9-bin/*/gradle-8.9/bin/gradle)
$G -p . assembleRelease --no-daemon -Djava.net.preferIPv4Stack=true
# → app/build/outputs/apk/release/app-release.apk  (unsigned  see below)
```

Notes:

- **Signing:** no keystore is committed here, so the Gradle release APK
  is unsigned. To get an installable APK either run `./build.sh`
  (auto-generates a throwaway keystore and signs), or drop your own
  keystore in and configure a `signingConfig`.
- `app/google-services.json` is the Firebase project config required by
  the Google Services plugin.
- Source uses the shared layout: `src/`, `res/`, `assets/`,
  `AndroidManifest-gradle.xml` (manifest without the `package` attr).

## Build (build.sh  hand-rolled, no Gradle, no Firebase SDK)

```bash
./build.sh        # aapt2 → javac → d8 → inject dex+native libs → apksigner
```

Output: `dist/WiFiShield.apk`, self-signed with a keystore that is
auto-generated on first run (no signing key is stored in this repo).
`build.sh` skips the `fb/` package (Firebase SDK sources), so no SDK
jars are needed; every Firebase call degrades to a no-op through
`FbKit` reflection. The native libs are injected from
`src/cpp/prebuilt/`.

## Contributing

This is an open-source developer security research project  ideas and
hands-on help are equally welcome:

- **Bug reports**  something crashes, a reading looks wrong, a screen
  misbehaves? Open an issue with your device model + Android version.
- **New security checks**  the analysis engine evaluates encryption,
  signal, WPS, PMF and channel congestion. Have a check worth adding?
  Propose it in an issue first, then send a PR.
- **UI / UX**  clearer visualizations, accessibility, translations.
- **Reports**  better PDF layout, more comparison data.

Please keep contributions focused, explain the *why* behind a change,
and make sure a normal Gradle build (`assembleRelease`) still succeeds.

## Research & responsible use

WiFiShield is meant for **your own networks and authorized testing
only**. Scanning reveals nearby networks' broadcast information  use
the knowledge to harden your own WiFi (strong encryption, WPS off,
unique passwords), not to harm anyone else's.
