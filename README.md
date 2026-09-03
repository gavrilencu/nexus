# NEXUS — Security Toolkit (Android)

A Jetpack Compose Android app bundling ~40 offensive/defensive security modules:
domain recon, port scanning, DNS/WHOIS/CT-log enumeration, subdomain & content
discovery, TLS/header/CORS analysis, fuzzing, JWT/hash labs, APK inspection & audit,
OSINT lookups, a local MITM/VPN HTTPS capture proxy, and an embedded Ubuntu shell
(via proot).

## ⚠️ Authorized use only
NEXUS is for **authorized security testing and education only**. Use it exclusively on
systems, devices, apps, and networks you **own or have explicit written permission** to
test. MITM interception and traffic/credential capture on networks or people without
consent is illegal in most jurisdictions. You are solely responsible for how you use it.

## Architecture
- **UI:** Jetpack Compose + Material 3, single-Activity, Navigation-Compose. MVVM.
- **Per module:** `ui/<m>/<M>ViewModel` (state) · `ui/screens/<M>Screen` (Compose) · `data/<m>/<M>Engine` (logic).
- Engines do all blocking/network work on `Dispatchers.IO` and return a result carrying an `error` field (they don't throw to the UI).
- Shared OkHttp clients in `data/network/HttpClients`.

## Security of the app's own data
- MITM root-CA private key and saved API keys are encrypted at rest with a non-exportable
  **AndroidKeyStore** AES-256-GCM key (`security/KeystoreCipher`).
- The LAN MITM proxy requires **Basic auth** for non-loopback clients; on-device/VPN capture is exempt.
- Upstream MITM TLS is validated with hostname verification.
- `allowBackup=false`; `FLAG_SECURE` blocks screenshots and the recents thumbnail.

## Build & run
```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:bundleRelease        # signed AAB (needs keystore.properties — see RELEASE.md)
```
Requires Android SDK 37 (compileSdk), JDK 17+. targetSdk 36, minSdk 24.

## Releasing / Google Play
See [RELEASE.md](RELEASE.md) — signing setup and the Play-policy caveats (several core
features carry a high rejection risk; direct-APK or a testing track is often the realistic path).

## License
No license is set yet — add one deliberately (the code is otherwise "all rights reserved").
