# Phase 5 — Device/Emulator Smoke-Test Plan

Audit date: 2026-09-02. Branch: `rewrite/independence`.

This checklist is the **final verification gate** for Phase 5 and is to be run
once an emulator/device and the native toolchain are available. None of it has
been executed here. Each protocol must pass its row before its adapter is called
"device verified".

Common per-connect steps (independent of protocol):
1. Import a valid profile for the protocol (from the configured `tzgate` server).
2. Ensure the Go AAR (`golibs-full.aar`) / relevant `.so` is present for the build flavor.
3. Tap connect → observe `Connecting` → `Connected` in `VpnConnectionManager` state.

| Protocol | Build artifact required | Install requirement | Config required | Connect test | Traffic test | Disconnect test | Reconnect test | Failure test | Cleanup test | Expected result |
|---|---|---|---|---|---|---|---|---|---|---|
| dnstt | `golibs-full.aar` (`noizdns/mobile`) | VPN permission, foreground service | profile domain + public key + a reachable DNSTT server | state → Connected | DNS via tunnel resolves; e.g. `8.8.8.8` reachable | state → Disconnected, ports released | reconnect after drop succeeds | bad key/domain → `Error`, no crash | `stop()`/`cleanup()` release listener/port | SOCKS bridge on listen port; traffic routes through DNS |
| noizdns | `golibs-full.aar` (`noizdns/mobile`) | same | domain + key + noiz flags | Connected | DNS-through-tunnel works | clean disconnect | reconnect | failure → Error | cleanup frees port | noiz-encoded queries reach server |
| vaydns | `golibs-full.aar` (`vaydns-mobile/vaydns`) | same | domain + key + vaydns options | Connected | traffic works | clean disconnect | reconnect | failure → Error | cleanup | vaydns SOCKS proxy reachable |
| slipstream | `libslipstream*.so` (Rust JNI) | VPN permission | domain + resolvers | Connected | traffic works | clean disconnect | reconnect | failure → Error | cleanup releases fd | tz-kitonga proxy on listen port |
| naive | `libnaive.so` | VPN permission | host + naive port + creds | Connected | HTTP/HTTPS through tunnel | clean disconnect | reconnect | bad creds → Error | cleanup | naive SOCKS reachable |
| vless | `golibs-full.aar` (`vlessreality-mobile`) | VPN permission | uuid + transport + CDN | Connected | traffic works (WS/CDN or reality) | clean disconnect | reconnect | bad uuid/expired → Error | cleanup | vless bridge on listen port |
| tor | `libtor.so` + `libobfs4proxy.so` | VPN permission | bridge lines (or built-in snowflake) | Connected (`Tor ready`) | web via Tor; DNS resolution | clean disconnect | reconnect | bad bridges → Error | cleanup | tor SOCKS reachable |
| ssh | none (pure-Java JSch) | VPN permission | host + port + auth (key/pass) | Connected | traffic through SSH | clean disconnect | reconnect | bad auth → Error | cleanup | SSH tunnel works |
| doh | `golibs-full.aar` | VPN permission | DoH URL | Connected | DNS-over-HTTPS works | clean disconnect | reconnect | bad URL → Error | cleanup | DoH proxy reachable |
| hysteria2 | `golibs-full.aar` (`hysteria2-mobile`) | VPN permission | server + password (+obfs/sni) | Connected (QUIC handshake) | UDP/TCP traffic works | clean disconnect | reconnect | bad server → Error | cleanup | hy2 QUIC proxy reachable |
| snowflake | `golibs-full.aar` + `libtor.so` | VPN permission | native/bridged snowflake | Connected | web via Tor | clean disconnect | reconnect | failure → Error | cleanup | snowflake bridge reachable |

Every row must assert:
- **No crash / no leaked coroutine or native handle** across connect→disconnect→reconnect loops.
- **Idempotent stop** (repeated `stop()`/`cleanup()` is safe).
- **Error state** is surfaced (not a blank disconnect) after a connect-time failure.
- **Ports released** after disconnect (no "port already in use" on the next connect).

## Executing the gate

Once the toolchain + device are ready:

```bash
# 1. Build both native flavors
cd gomobile-build && make build       # Go AARs
# Gradle builds Rust (slipstream) + C (hev-socks5-tunnel) via the normal assemble
gradlew :app:assembleFullDebug :app:assembleLiteDebug

# 2. Install & run per protocol against the tzgate dev server
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
# Script each protocol row above (connect → traffic → disconnect → reconnect → failure → cleanup)
```

Record a pass/fail line per protocol in `docs/provenance/REPLACEMENTS.md` when done.
