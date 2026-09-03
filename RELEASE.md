# NEXUS — Release & Google Play guide

## Identity
- **applicationId:** `md.gavrilencudev.toolkit` (permanent once published)
- **Internal namespace:** `com.example.toolkit` (invisible to Play/users; not renamed on purpose)
- versionCode / versionName are set in [app/build.gradle.kts](app/build.gradle.kts). Bump `versionCode` on every upload.

## Signing
Release signing reads from `keystore.properties` at the repo root — **gitignored, never committed**.

```
storeFile=F:/nexus/nexus-release.keystore
storePassword=********
keyAlias=nexus
keyPassword=********
```

- Keystore: `nexus-release.keystore` (repo root, gitignored). RSA-2048, alias `nexus`, valid 10000 days.
- **Back up the keystore file AND its password offline.** If lost, you can never push an update to the same Play listing.
- Without `keystore.properties` the release build still assembles, but **unsigned** (for CI / local checks).

## Build
```bash
# JAVA_HOME must point at a JDK 17+ (e.g. Android Studio's JBR)
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"

./gradlew :app:bundleRelease     # -> app/build/outputs/bundle/release/app-release.aab  (upload this to Play)
./gradlew :app:assembleRelease   # -> signed APK for side-loading / direct distribution
```
R8 (minify + resource shrink + obfuscation) is on for release; keep rules live in [app/proguard-rules.pro](app/proguard-rules.pro).
The ProGuard mapping (`app/build/outputs/mapping/release/mapping.txt`) should be uploaded to Play for deobfuscated crash reports.

## ⚠️ Play Store policy — read before submitting
NEXUS's core features are **high-risk** under Google Play policy. Expect elevated review and a real chance of rejection. The main flags:

| Feature | Permission / API | Play requirement |
|---|---|---|
| MITM HTTPS capture | `VpnService`, MITM proxy | Device & Network Abuse policy; VpnService apps get manual review. HTTPS interception + credential capture is frequently rejected. |
| Installed-app APK inspector | `QUERY_ALL_PACKAGES` | Requires a Permissions Declaration + an approved use case; most categories are refused. |
| Wi-Fi / SIM monitor | `ACCESS_FINE_LOCATION` | Prominent-disclosure + runtime consent; sensitive-permission review. |
| Foreground services | `FOREGROUND_SERVICE_SPECIAL_USE` (×4, incl. Linux shell) | Console declaration justifying why no standard FGS type fits; the embedded Ubuntu shell is unlikely to be accepted. |
| Cleartext traffic | `usesCleartextTraffic=true` | Needed for the tool's own HTTP targets; flagged but generally allowed with justification. |

**Realistic distribution options for a tool like this:**
1. **Direct APK / your own site** — `assembleRelease` gives a signed APK. No policy review. Most common for pentest tooling.
2. **Play Internal testing / Closed testing track** — usable with a small tester list, lighter scrutiny than production.
3. **F-Droid or similar** — fits open-source security tooling.
4. **Play Production** — only after the declarations above; be ready for back-and-forth or rejection.

Ship it only for **authorized** security testing on devices/networks you own or have written permission to test.
