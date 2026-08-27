# VPN-TZ Independence Rewrite Plan

Working rules per audit (docs/provenance/AUDIT_2026_08_27.md):
- baseline `779ec73` untouched; work happens on `rewrite/independence`
- each phase = compile ✚ unit tests ✚ behavior-equivalence evidence ✚ provenance entry
- third-party engines stay; only SlipNet-derived glue/logic is replaced
- no new dependencies unless unavoidable and documented

## Phase ladder

| # | Scope | Why this order | Risk | Main evidence |
|---|---|---|---|---|
| 0 | Provenance registry (`docs/provenance/REPLACEMENTS.md`), add missing MPL-2.0 notice for Xray-core adaptation, fix `android.util.Base64` isolation checklist | zero-behavior housekeeping, unlocks testability planning | LOW | docs diff |
| 1 | **Configuration layer**: new `com.vpntz.app.config` — independent profile model + URI codecs (vpntz/vless/hysteria2) + AEAD encrypted container (new write format, legacy read path reimplemented independently) | single call-site consumer (MainViewModel:614); pure JVM; defines contract everything else sits on; enables killing android.util.Base64 blockers | MEDIUM | golden-vector fixtures incl. tzvpn:// legacy samples + roundtrip property tests |
| 2 | Domain model + use-cases (19 files): independent models implementing same repo interfaces | pure Kotlin, unblocks service/UI phases | LOW-MED | mapping tables + tests per model invariant |
| 3 | App-side network kit: DnsUtils, IpPacketParser, TcpPacketBuilder, ProtocolSniffer, SniFragmentForwarder logic (replace one by one behind current tunnel API) | self-contained byte-level components; vectors from RFCs + synthetic pcaps | MED | vector-driven JVM tests |
| 4 | Service architecture: re-author orchestrator as explicit state machine replacing VpnTzService internals behind the SAME public surface (Service class name kept for manifest/JNI stability until JNI adapters migrate) | highest coupling — done only after 1-3 give clean interfaces | HIGH | integration matrix on emulator + manual device script |
| 5 | Tunnel adapters regeneration per protocol (dnstt/vaydns/noizdns/slipstream/naive/vless/tor/ssh/doh), patching third-party JNI exports only where symbol paths change | engines themselves are third-party; adapters become thin independently-written FFI shims | HIGH | per-protocol smoke on emulator + server pairing with tzgate instance |
| 6 | UI/presentation rebuild screen-by-screen onto new domain/config layers | visuals after data truth | LOW-MED | screenshot diffs (baseline vs rewrite) |
| 7 | Go client `cli` re-implementation to same CLI contract; then `tzgate` installer final phase | server tool lowest urgency; binaries URL swap at completion | MED-HIGH | interop matrix app↔cli↔tzgate |

Out of scope forever: renaming/rebranding third-party C/Rust/Go upstream trees.

## Phase 1 (proposed first coding slice)

**Goal:** a from-scratch configuration subsystem that speaks every wire format the old one accepts, emits `vpntz://` and a NEW internal envelope, passes the full existing unit suite, and compiles into both flavors without touching UI code (adapter delegates kept on the same methods used by MainViewModel).

**New package layout**
```
com.vpntz.app.config
 ├─ ProfileDocument        (immutable model, builder)
 ├─ ProfileUriCodec        vpntz:// emit+parse
 ├─ StandardUriCodec       vless:// hysteria2:// parse (+emit for vless)
 ├─ LegacyUriCodec         tzvpn:// family → ProfileDocument (read-only)
 ├─ EncryptedEnvelope      AES-GCM(key) container, v2 format, NEW magic header
 ├─ LegacyEnvelopeReader   independently implemented reader for v1 payloads
 └─ ConfigGateway          facade exposing exactly the two entry points MainViewModel uses
```
Wire notes: java.util.Base64 (NO_WRAP) replaces android.util.Base64; JSON via Gson already in deps; PBKDF2-HMAC-SHA256 (210k iters, per-document salt) — platform JCE only, zero new dependencies.

**Explicit non-goals:** DB schema changes, Room entities, preference keys, tunnel-type string changes, tzgate/cli side edits.

**Exact tests required before "done" (all JVM)**
1. UriCodec roundtrip property test: generate ≥200 random valid ProfileDocuments → encode → decode → assert equality
2. Golden fixtures: hand-built expected base64 vectors (3 profiles × small/large field sets) asserted against spec — guards accidental format drift
3. `vless://` parser table test: ws/wss+tls+sni, reality pbk, flow strings, fragment-name decoding, %-encoding edge cases, malformed → typed error
4. `hysteria2://` parser: obfs params, ports-hopping syntax, up/down hints, malformed
5. Legacy `tzvpn://` acceptance: compat preserved (fixtures baked now from CURRENT importer behavior using Robolectric-free extraction — build fixture corpus by running current app export on emulator once and committing outputs)
6. `vpntz://` cross-compat: OLD exporter output decodes by NEW gateway (fixture captured same way)
7. Envelope v2: roundtrip, wrong-password fails cleanly, tampered ciphertext rejected (AEAD), salt uniqueness across encryptions
8. Legacy envelope read: decrypt sample bundle produced by current release build (user-provided or emulator-generated fixture file committed as resource)
9. Migration equivalence harness: property test that `LegacyUriCodec.parse(x)` == `newCodec.parse(LegacyUriCodec.migrate(x))`
10. Full suite regression: existing VpnTzCoreTest stays green; `compileFullDebugKotlin` + `testFullDebugUnitTest` + `compileLiteDebugKotlin`

**Evidence of independence:** design doc in package KDoc contrasting decisions (model immutability, error taxonomy, envelope framing) versus behavioral spec; no structural copying — file-level review recorded in REPLACEMENTS.md entry.

## Verification commands per phase
```
gradlew :app:compileFullDebugKotlin :app:compileLiteDebugKotlin --offline
gradlew :app:testFullDebugUnitTest --offline
go -C cli  build ./...      go -C tzgate build ./...   (GOOS=linux where relevant)
cargo metadata (both workspaces)
```

## Emulator/device gate (phases ≥4)
Manual smoke matrix committed as checklist: connect per protocol against local dockerized servers (tzgate dev mode), QR import/export between builds, split-tunneling toggle, boot auto-connect.
