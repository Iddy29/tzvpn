# Data Safety form — pre-filled answers

Google Play Console → App content → **Data safety**. Answer using this sheet (it mirrors what the code actually does).

## Q: Does your app collect or share any of the required user data types?
**No** — but you must still complete the flow and declare the items below that are *transmitted* by app functionality:

## Declarations to select
| Data type | Collected? | Shared? | Ephemeral? | Purpose |
|---|---|---|---|---|
| Device or other IDs | Yes | No | No | App functionality (included only inside configuration exports the user creates) |
| App interactions | No | — | — | — |
| Approximate/precise location | No | — | — | — |

> Note: the device ID is a one-way HMAC-SHA256 fingerprint of ANDROID_ID, created on-device and never sent anywhere by the app itself; it travels only inside config files the user exports. There is no backend receiving it.

## Required "yes" follow-ups
- Is data collected... encrypted in transit? → **Yes** (any export is at the user's discretion; app's own release-check uses HTTPS)
- Can users request data deletion? → **N/A — we don't operate servers. Uninstalling removes all local data.**
- Data deletion URL → not applicable (no accounts)

## Security practices
- ✔ Data stored locally in private app storage
- ✔ Config backups can be password-encrypted (AES) by the user
- ✔ No security vulnerabilities known / report via SECURITY.md

## VPN declaration (separate from Data safety)
Play Console → declare usage of **VpnService**:
- Purpose: *"VPN-TZ routes the device's traffic through tunnels to user-configured servers (DNS tunneling, SSH, Tor bridges) to bypass censorship."*
- Confirm: data is **not** collected through the VPN tunnel by the developer.
