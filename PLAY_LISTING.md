# NEXUS — Google Play submission kit

Everything you paste into Play Console. Fill the `[…]` blanks.

## App details
- **App name:** NEXUS
- **Package (applicationId):** `md.gavrilencudev.toolkit`
- **Category:** Tools
- **Content rating target:** 18+
- **Contains ads:** No · **In-app purchases:** No · **Free**

## Short description (≤ 80 chars)
> On-device security & recon toolkit for authorized testing — 40+ pro tools.

## Full description (≤ 4000 chars)
> NEXUS is a professional security-testing toolkit that runs entirely on your
> Android device. It brings together 40+ tools for reconnaissance, web-app
> testing, OSINT, cryptography and mobile analysis in one clean, fast interface.
>
> FOR AUTHORIZED USE ONLY. Use NEXUS exclusively on systems, devices, apps and
> networks you own or have explicit written permission to test.
>
> Recon & mapping: domain recon, DNS dig, subdomain finder, port scanner, IP
> tools, content discovery, web fingerprint, WHOIS/RDAP.
> Web app testing: API lab, CORS scanner, HTTP methods, TLS/SSL analyzer,
> security headers, web-vuln checks, fuzzer, JS recon, exposed files, GraphQL,
> subdomain takeover, WebSocket tester.
> OSINT & vulnerabilities: person/username search, Have I Been Pwned, CVE lookup,
> CT-log enumeration, Wayback URLs, EXIF extractor, dork builder, Shodan lookup.
> Crypto & tokens: hash/encoder, hash cracker, JWT lab.
> Mobile & RE: APK inspector, APK security audit, deep-link tester, Firebase
> checker, DEX API scanner.
> Traffic: on-device HTTP/HTTPS capture (MITM/proxy) and a live network monitor.
> System: a real Linux (Ubuntu) shell in your pocket.
>
> Privacy first: no accounts, no analytics, no ads. Your inputs and captured
> traffic stay on your device; saved keys are encrypted with the Android Keystore.

## Graphics you must provide
- **App icon:** 512×512 PNG (already have `ic_launcher` — export at 512).
- **Feature graphic:** 1024×500 PNG.
- **Phone screenshots:** at least 2 (use the redesigned Dashboard + a tool screen).

## Data safety form (answers)
- Does your app collect or share user data? **No data is collected or shared with
  the developer.** (Third-party lookups are user-initiated and sent directly to
  those services.)
- Is data encrypted in transit? **Yes** (HTTPS to third-party services).
- Can users request deletion? Not applicable — nothing is stored off-device.

## Sensitive-permission declarations (Console will ask)
- **QUERY_ALL_PACKAGES** — Justification: the APK Inspector / APK Security Audit
  tools let the user select any installed app to analyze its package; enumerating
  installed packages is required to present that picker. (Core functionality of a
  security-analysis tool.)
- **VpnService** — Justification: on-device HTTPS traffic capture that the user
  explicitly starts, for testing traffic of apps on their own device. No traffic
  leaves the device; capture is shown to the user only.
- **FOREGROUND_SERVICE_SPECIAL_USE** — Justification: keep a user-started capture
  or the local Linux shell alive while backgrounded, with an ongoing notification.
- **ACCESS_FINE_LOCATION** — Justification: required by Android to read Wi-Fi
  network details in the Wi-Fi monitor. Location is not collected or shared.

## Privacy policy
Host `PRIVACY_POLICY.md` at a public URL and paste it into Store listing →
Privacy policy. (GitHub Pages works: put it in a repo, enable Pages.)

## ⚠️ Reality check (read RELEASE.md)
The MITM/VPN capture, QUERY_ALL_PACKAGES and the embedded Linux shell are
high-risk under Play's Device & Network Abuse policy and may be rejected. Start
with the **Internal testing** track to validate signing/upload, and be ready for
review questions or to distribute the signed APK directly if Production is refused.
