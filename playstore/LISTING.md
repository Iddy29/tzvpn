# VPN-TZ — Google Play Listing (copy-paste ready)

## App title (max 30 chars)
```
VPN-TZ: Fast Secure VPN Tunnel
```

## Short description (max 80 chars)
```
Fast anti-censorship VPN with DNS tunneling, SSH chaining and Tor bridges.
```

## Full description (max 4000 chars)
```
VPN-TZ is a fast, modern VPN client built for open internet access in censored networks. It tunnels your traffic over DNS, QUIC, HTTPS, SSH or the Tor network — so your connection keeps working even when VPN protocols are blocked.

WHY VPN-TZ
• Works where regular VPNs are blocked — thanks to DNS tunneling technology
• Free and open source (AGPL-3.0) — auditable, no hidden components
• No account, no registration, no tracking — connect with your own server config
• Modern Material You interface with dark & AMOLED themes

TUNNEL TYPES
• DNSTT — reliable DNS tunneling (KCP + Noise encryption)
• NoizDNS — DPI-resistant DNS tunneling
• VayDNS — optimized DNS tunneling with configurable wire format
• tz-kitonga — high-performance QUIC tunneling
• SSH — standalone SSH tunnel, plus variants with TLS, WebSocket and HTTP CONNECT wrapping
• NaiveProxy — Chromium-style HTTPS camouflage
• VLESS / VLESS REALITY — CDN-friendly tunnels with TLS fingerprinting
• Hysteria2 — blazing fast QUIC proxy
• DoH — encrypted DNS only mode
• Tor — via Snowflake, obfs4 or Meek bridges
• Chain any DNS tunnel with SSH for extra protection against DNS leaks

SMART FEATURES
• Built-in DNS server scanner — finds working resolvers automatically
• Split tunneling — choose which apps use the VPN
• Multiple profiles with QR import/export and encrypted backups
• Home-screen widget and Quick Settings tile
• Auto-connect on boot
• Bandwidth limiting
• Debug log viewer for troubleshooting
• Share the APK directly — handy when internet shutdowns make app stores unreachable

SERVER
VPN-TZ works with your own server. Set one up in minutes with TzGate, our one-command server installer supporting every protocol listed above.

TRANSPARENT & PRIVATE
VPN-TZ has no analytics, no ads and no tracking SDKs. See our privacy policy for details.

Open source software under AGPL-3.0. Third-party notices: see the repository.
```

## Category
- App category: **Tools**
- Tags: Security, Privacy

## Contact details (fill in yours)
- Website: https://github.com/vpntz/vpn-tz  ← replace with your domain if you have one
- Email: *(your support email)*

## Assets checklist
| Asset | Spec | Status |
|---|---|---|
| App icon | 512×512 PNG | ✅ `imgs/icon.png` |
| Feature graphic | 1024×500 PNG | ✅ `playstore/feature_graphic.png` |
| Phone screenshots | min 2, 16:9 or 9:16, PNG/JPEG | ⬜ capture per SCREENSHOT_SHOTLIST.md |
| Privacy policy URL | hosted page | ⬜ publish PRIVACY_POLICY.md |

## Critical declarations during submission
1. **VPN service declaration** — in Play Console you must declare you use VpnService (there is a dedicated VPN app disclosure form). State clearly: *user-configured servers only; no proxy provider relationship.*
2. **QUERY_ALL_PACKAGES permission** — permitted for split-tunneling app selection, but it triggers manual review. In the declaration write: *"Used exclusively to present the installed-apps list for the user's per-app VPN (split tunneling) selection."* Consider recording a short screen video of the feature for reviewers.
3. **Default handler / sensitive permissions** — none besides above.
4. **Data safety section** — follow DATA_SAFETY.md answers.
5. **Content rating questionnaire** — "no user-generated content", "no sharing of personal info", category Tools → expected rating: Everyone.
6. **Release format** — Play requires `.aab`: run `./gradlew bundleFullRelease` (signing via keystore.properties already wired).
