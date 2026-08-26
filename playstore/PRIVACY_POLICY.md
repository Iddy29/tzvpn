# VPN-TZ Privacy Policy

_Last updated: 2026-08-27_

VPN-TZ ("we", "the app") is a free, open-source VPN client. This policy explains exactly what data the app handles. The short version: **we operate no servers, no accounts, and collect nothing.**

## 1. What we do NOT do
- We do not collect, transmit, sell, or share any personal data
- The app contains **no analytics, advertising, crash-reporting, or tracking SDKs**
- We do not require registration or any personal information

## 2. Information stored on your device only
- **Server profiles** you create (addresses, credentials, settings) are stored in the app's private encrypted database on your device and never leave it unless you export them yourself (QR code, clipboard, or backup file).
- An **optional app-lock password/PIN** — stored only locally as a salted hash.

## 3. Device fingerprint (one-way hashed)
When you export or share configuration profiles between your own devices, the app includes a device identifier derived by applying keyed **HMAC-SHA256** to your device's ANDROID_ID. The raw ANDROID_ID is never read out, stored, or transmitted; only the non-reversible hash result is included inside the configuration files **you choose to share**. It cannot be linked back to your identity or used to track you across services.

## 4. Network connections made by the app itself
The app contacts only the following destinations, always initiated by its own features:
- **Your configured VPN/DNS server(s)** — all tunneled internet traffic goes to servers *you* configure. We do not provide, monitor, or log those servers.
- **GitHub API** (`api.github.com`) — solely to check whether a newer app release exists (approximately every 12 hours). Your IP address is visible to GitHub as with any web request. You can disable this in the app's settings.

## 5. Permissions and why
| Permission | Purpose |
|---|---|
| INTERNET / ACCESS_NETWORK_STATE | Core connectivity |
| FOREGROUND_SERVICE | Keep the VPN connection alive while connected |
| POST_NOTIFICATIONS | Connection status notification (Android 13+) |
| RECEIVE_BOOT_COMPLETED / WAKE_LOCK / BATTERY exemptions | Optional auto-connect on startup and stable tunneling |
| QUERY_ALL_PACKAGES | To show your installed apps in the **split-tunneling** chooser — app names stay on your device |
| CAMERA | Only to scan configuration QR codes — no photos taken or stored |

## 6. Children
The app is not directed at children under 13 and does not knowingly collect any data from anyone.

## 7. Changes
Any change to this policy will be published in the app's public source repository with an updated date.

## 8. Contact
Questions about this policy: *(add your contact email)* · Source code: https://github.com/vpntz/vpn-tz
