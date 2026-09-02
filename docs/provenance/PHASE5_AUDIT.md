# Phase 5 — Tunnel Adapter Audit

Audit date: 2026-09-02. Branch: `rewrite/independence`.
Baseline reference: `main@779ec73`. Companion doc: `docs/provenance/REPLACEMENTS.md`.

This document records the actual per-protocol tunnel architecture discovered by
inspection. It is **not** a claim that any protocol has been migrated: Phase 5
requires (a) extracting each protocol's wiring out of `VpnTzService` into a
clean `TunnelAdapter`, and (b) validating connectivity per-protocol. Step (b)
cannot be executed in the current environment (no emulator/adb/device; see
"Blockers"). Do **not** treat any row below as "migrated".

## How tunnels are wired today

The protocol implementations are **third-party engines exposed through
pre-built native artifacts**:

| Artifact | Engine(s) | Mechanism |
|---|---|---|
| `app/libs/golibs-full.aar` / `golibs-lite.aar` | DNSTT, NoizDNS, VayDNS, VLESS (+reality), Hysteria2, DoH, snowflake/WebRTC | Go → gomobile bindings (`mobile.Mobile`, `mobile.DnsttClient`, …) |
| `app/src/main/full/jniLibs/*/libnaive.so`, `libobfs4proxy.so`, `libtor.so` | NaiveProxy, obfs4, Tor | Native `.so` loaded over JNI |
| `app/src/main/cpp/hev-socks5-tunnel-src` | hev-socks5-tunnel (TUN/tun2socks) | C + lwip, JNI |
| `app/src/main/rust/slipstream-rust{,-plus}` | tz-kitonga (slipstream) | Rust via JNI/`NativeSocket` |

Each protocol already has a Kotlin **bridge** in `com.vpntz.app.tunnel`
(e.g. `DnsttBridge`, `SlipstreamBridge`, `VaydnsBridge`, `VlessBridge`,
`DohBridge`, `NaiveBridge`, `TorSocksBridge`, `SshTunnelBridge`,
`Hysteria2Bridge`) plus a SOCKS bridge / instance that fronts it to the
proxy port. The profile → bridge configuration translation currently happens
inside `VpnRepositoryImpl` (`startDnsttProxy`, `startVaydnsProxy`,
`startSlipstreamProxy`, `startNaiveSocksBridge`, `startDohProxy`,
`startSnowflakeProxy`, `startTun2Socks`, …), and the **ordered orchestration**
(establish `VpnService` interface → start proxy → start SOCKS bridge → start
`tun2socks` → `finishConnection`) lives in `VpnTzService`.

## Per-protocol audit

### dnstt
- Android adapter: `DnsttBridge` + `DnsttSocksBridge`
- Native implementation: gomobile from `golibs-full.aar` (`mobile.DnsttClient`)
- Current entry point: `VpnRepositoryImpl.startDnsttProxy` → `DnsttBridge.startClient`
- Current caller: `VpnTzService.connectDnstt` (~L1647)
- Legacy dependency: profile → config mapping in `VpnRepositoryImpl`; orchestration in `VpnTzService`
- Adapter: `DnsttTunnelAdapter` (alias of the bound `TunnelAdapter`), backend wired as `BridgeTunnelLifecycleBackend` in `com.vpntz.app.tunnel.adapter`
- Configuration mapping: `ServerProfile` (domain, public key, noiz/stealth flags, resolver mode, RR spread, SOCKS5 proxy chain) + prefs (ports, host, DNS worker pool)
- Start: `startClient(...)`; Stop: `stopClient`/`stopClientBlocking`; Ready: `isClientHealthy`; Cleanup: `stopClient` + `DnsttBridge.setVpnService(null)`
- Tests: none (existing coverage is device smoke, not JVM); `DnsttBridgeArgs` JVM-tested for config→args
- Migration status: **adapter wired; device verification pending** — `VpnRepositoryImpl.startDnsttProxy` now builds a `TunnelAdapterConfig.Dnstt` via `TunnelConfigMapper` (runtime DNS-address/auto-tune copied in) and calls `tunnelAdapter.start(config)`, which delegates to `DnsttBridge.startClient` through `BridgeTunnelLifecycleBackend`. `VpnTzService.connectDnstt` orchestration is unchanged. NoizDNS (`startNoizdnsProxy`) still calls the bridge directly and can reuse this backend next.

### noizdns
- Android adapter: `DnsttBridge` (invoked with `noizMode=true`, `setDeviceManufacturer`, optional `setStealthMode`) + `DnsttSocksBridge`
- Native: gomobile `mobile.DnsttClient` with noiz flags
- Entry point: `VpnRepositoryImpl.startNoizdnsProxy`; caller: inside `VpnTzService.connectDnstt` (branch)
- Adapter: reuses `BridgeTunnelLifecycleBackend` over `DnsttBridge` + `DnsttBridgeArgs` (same backend as DNSTT; differences are `noizdns=true` + `noizStealth` carried in the config)
- Migration status: **adapter wired; device verification pending** — `VpnRepositoryImpl.startNoizdnsProxy` now builds a `TunnelAdapterConfig.Dnstt` (noizdns=true, noizStealth copied, runtime DNS-address/auto-tune copied in) and calls `tunnelAdapter.start(config)` → `BridgeTunnelLifecycleBackend` → `DnsttBridge.startClient(noizMode=true)`. `VpnTzService` orchestration unchanged.

### vaydns
- Android adapter: `VaydnsBridge` (+ SOCKS bridge)
- Native: gomobile `golibs-full.aar`
- Entry point: `VpnRepositoryImpl.startVaydnsProxy` → `VaydnsBridge.startClient`
- Caller: `VpnTzService.connectVaydns` (~L2496)
- Adapter: added `TunnelAdapterConfig.Vaydns` (with `effectiveDnsServer`), `VaydnsBridgeArgs` (pure config→bridge-args), and a `TunnelAdapterConfig.Vaydns` branch in `BridgeTunnelLifecycleBackend` (`startVaydns`); health/stop/cleanup now dispatch on the active config.
- Migration status: **adapter wired; device verification pending** — `VpnRepositoryImpl.startVaydnsProxy` now builds a `TunnelAdapterConfig.Vaydns` (static fields from `TunnelConfigMapper`; runtime DNS-address/auto-tuned qname+rps copied in; `maxPayload=0`) and calls `tunnelAdapter.start(config)` → `BridgeTunnelLifecycleBackend` → `VaydnsBridge.startClient`. `VpnTzService.connectVaydns` orchestration unchanged.

### slipstream (tz-kitonga)
- Android adapter: `SlipstreamBridge` + `SlipstreamSocksBridge` (JNI `loadLibrary("slipstream")`)
- Native: Rust `slipstream-rust`/`slipstream-rust-plus`, socket fd via `NativeSocket`
- Entry point: `VpnRepositoryImpl.startSlipstreamProxy` → `SlipstreamBridge.startClient`; caller: `VpnTzService.connectSlipstream` (~L1286); `connectSlipstreamSsh`
- Adapter: added `TunnelAdapterConfig.Slipstream` fields (congestionControl, keepAliveInterval, gsoEnabled, idlePollIntervalMs, idleTimeoutMs) + `Sni`-free; `SlipstreamBridgeArgs` (pure config→bridge-args incl. `ResolverConfig` dedup); a `TunnelAdapterConfig.Slipstream` branch in `BridgeTunnelLifecycleBackend` (`startSlipstream`).
- Migration status: **adapter wired; device verification pending** — `VpnRepositoryImpl.startSlipstreamProxy` now builds a `TunnelAdapterConfig.Slipstream` (static fields from `TunnelConfigMapper`; listen address + debug-log flag runtime) and calls `tunnelAdapter.start(config)` → `BridgeTunnelLifecycleBackend` → `SlipstreamBridge.startClient`. `VpnTzService.connectSlipstream` orchestration unchanged; the legacy `startSlipstreamClient` helper is still used by a separate direct-start path and was left in place. Rust `.so` not rebuilt (see `PHASE5_NATIVE_BUILD.md`).

### naive
- Android adapter: `NaiveBridge` + `NaiveSocksBridge`
- Native: `libnaive.so`
- Entry point: `VpnRepositoryImpl.startNaiveSocksBridge`; caller: `VpnTzService.connectNaive`
- Migration status: **not migrated**

### vless
- Android adapter: `VlessBridge` (+ `VlessRealityBridge`)
- Native: gomobile `golibs-full.aar` (Xray-core adaptation)
- Entry point: `VlessBridge.start`; caller: `VpnTzService.connectVless` (~L3047)
- Migration status: **not migrated**

### tor
- Android adapter: `TorSocksBridge`
- Native: `libtor.so` + `libobfs4proxy.so` + snowflake (WebRTC) gomobile
- Entry point: `VpnRepositoryImpl.startSnowflakeProxy`; caller: `VpnTzService.connectSnowflake` / `connectSnowflakeSmart`
- Migration status: **not migrated**

### ssh
- Android adapter: `SshTunnelBridge` + `SshTunnelInstance`
- Native: pure-Java JSch (no native binary)
- Entry point: `startSsh…` in `VpnTzService` (via `configureSshBridge`/`configureSshInstance`)
- Callers: `connectSsh`, `connectDnsttSsh`, `connectNaiveSsh`, `connectVaydnsSsh`
- Migration status: **not migrated**

### doh
- Android adapter: `DohBridge`
- Native: gomobile `golibs-full.aar` (Go DoH, uTLS)
- Entry point: `VpnRepositoryImpl.startDohProxy`; caller: `VpnTzService.connectDoh`
- Migration status: **not migrated**

### hysteria2 / snowflake (extra, discovered)
- Android adapters: `Hysteria2Bridge`, `TorSocksBridge`/snowflake
- Native: gomobile `hysteria2-mobile`, snowflake
- Callers: `VpnTzService.connectHysteria2`, `connectSnowflakeSmart`
- Migration status: **not migrated**

## Existing adapter-like abstractions already present

- `VpnStateMachine` (Phase 4) owns connection lifecycle state.
- `VpnConnectionManager` is the service-layer facade the UI/domain talk to.
- The per-protocol Kotlin `*Bridge` objects are effectively the current
  adapters over native code (they were **not** replaced in Phase 3/4).
- `VpnRepositoryImpl` concentrates all profile→bridge configuration mapping
  and owns `startTun2Socks` / `startWithFd` (native fd handoff).

## Required new architecture (Phase 5 target)

```
Domain / Use cases
       ↓
VpnConnectionManager (orchestration facade)
       ↓
TunnelAdapter (uniform lifecycle: configure → start → isRunning → health → stop → cleanup)
       ↓
ProtocolAdapter (per protocol, e.g. DnsttTunnelAdapter)
       ↓
Existing Kotlin *Bridge → FFI / native artifact (gomobile / .so / Rust / JNI)
```

`VpnTzService` should keep only Android `VpnService` responsibilities
(foreground lifecycle, VPN interface establishment, service start/stop,
Android cancellation) and should no longer contain the ordered
proxy/SOCKS/tun2socks knowledge or per-protocol config mapping.

## Blockers to completing/verifying Phase 5 in this environment

1. **No emulator/device.** `adb` is not on PATH; no connected device. The plan's
   Phase-5 gate is "per-protocol smoke on emulator + server pairing with a
   tzgate instance", which cannot be run here. Tunnel connectivity can only be
   judged on-device, so any re-wiring is unverifiable now.
2. **No gomobile / Android cross-build setup.** The Go engines are consumed via
   committed `golibs-full.aar` / `golibs-lite.aar`. `gomobile` is not
   configured in module context and there is no `ANDROID_NDK_HOME`/cross target
   to rebuild the mobile bindings. (An NDK at
   `%LOCALAPPDATA%\Android\Sdk\ndk\29.0.14206865` exists but is not wired for
   gomobile.) Regenerating/verifying FFI shims is not possible here.
   (Note: Go 1.25 and Rust 1.98 toolchains are installed; the Go CLI/server
   and Rust workspaces could be compiled, but that does not validate tunnel
   connectivity inside the app.)
3. **Service coupling.** Each `connectX` method interleaves protocol config with
   Android `VpnService` mechanics (interface establishment, `tun2socks` fd
   handoff, `waitForProxyReady`, kill-switch, auto-reconnect, chains). Moving
   this into adapters touches the 5.9k-line `VpnTzService` and can only be
   safely validated on-device.

## Safe, verifiable work already done for Phase 5

- Phase 3 canonicalized the byte-level **network kit** (`com.vpntz.app.network`)
  reused by the bridges; the network codecs (`DnsUtils`, `IpPacketParser`,
  `TcpPacketBuilder`, `ProtocolSniffer`, `TlsPacketFragmenter`) are no longer
  embedded in the bridges.
- Phase 4 centralized connection **lifecycle state** in `VpnStateMachine` and
  `VpnConnectionManager`, so `VpnTzService` no longer owns the connection
  state machine.

Combined, these leave Phase 5 to be the adapter extraction + per-protocol
migration, which needs a device/emulator and the mobile build toolchain to do
honestly.
