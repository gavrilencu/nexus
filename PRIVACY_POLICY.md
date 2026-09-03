# NEXUS — Privacy Policy

_Last updated: 2026-09-03_

NEXUS ("the app") is a security-testing toolkit for Android. This policy explains
what the app does with data. **Short version: the app does not collect, sell, or
send your personal data to the developer. There are no accounts, no analytics,
and no advertising.**

## Who this is for
NEXUS is intended solely for **authorized security testing and education** — on
systems, devices, apps, and networks you own or have explicit written permission
to test.

## Data the app processes
All processing happens **on your device**. The app has no backend server operated
by the developer and does not transmit your data to the developer.

- **Inputs you type** (domains, IPs, URLs, hashes, tokens, e-mail addresses, etc.)
  are used only to perform the action you request and are held in memory for that
  session. They are not persisted or transmitted to the developer.
- **Third-party lookups.** When you run a tool that queries a public service, your
  input is sent directly to that service so it can answer. These include, depending
  on the tool you use: Shodan/InternetDB, Have I Been Pwned, crt.sh, NVD (CVE),
  the Wayback Machine, ip-api, and public DNS/RDAP/WHOIS endpoints. Their handling
  of that request is governed by their own privacy policies.
- **On-device traffic capture (MITM / VPN / monitor).** Captured requests and
  responses are shown to you and kept **in memory only**; they are not written to
  disk or sent anywhere. They are cleared when you stop the capture or close the app.
- **Saved settings.** An optional API key (e.g. for Have I Been Pwned) is stored
  locally and **encrypted with a hardware-backed key** in the Android Keystore. It
  never leaves the device and is excluded from device backups.

## Permissions and why they are used
- **Internet / network state** — to perform the network lookups you request.
- **Location / nearby Wi-Fi** — required by Android to read Wi-Fi network details
  in the Wi-Fi monitor tool. Location is not collected or shared.
- **VPN service** — to run the on-device HTTPS capture you explicitly start.
- **Query installed apps** — to let you pick an installed app to inspect in the
  APK tools.
- **Foreground service / notifications** — to keep a capture or the local Linux
  shell running while the app is in the background, with an ongoing notification.

## Data sharing and sale
The developer does **not** share or sell any personal data. The only data leaving
your device is what you explicitly send to a third-party service by running a tool.

## Security
Sensitive on-device material (the MITM certificate authority key and any saved API
key) is encrypted with a non-exportable Android Keystore key. The app blocks
screenshots of sensitive screens and disables system backups of its data.

## Children
NEXUS is not directed to children and is intended for users 18+.

## Changes
This policy may be updated; the "Last updated" date will change accordingly.

## Contact
Questions: **[add a dedicated contact e-mail before publishing]**

> Note to publisher: host this file at a stable public URL (e.g. GitHub Pages or
> your own domain) and paste that URL into Play Console → Store listing → Privacy
> policy. Replace the contact line with a real address (avoid a work e-mail).
