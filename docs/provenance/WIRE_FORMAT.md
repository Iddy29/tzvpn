# VPN-TZ Profile Wire Format — Spec v1 (behavioral freeze for Phase 1)

Derived as a *specification* from observable behavior of baseline `779ec73`.
This document is the single source of truth for `com.vpntz.app.config` and its tests.
The Go side (`tzgate/internal/clientcfg`) already implements positions 0–59 of this
format independently; it serves as the cross-language oracle for fixtures.

## URI schemes

| Scheme | Payload | Emitted by | Read by new layer |
|---|---|---|---|
| `vpntz://b64` | pipe record (below), base64(STD,NOPAD) | yes (v43) | ✔ |
| `vpntz-enc://b64` | EnvelopeDeviceKey(record) | locked single | ✔ (needs device key) |
| `vpntz-bundle-enc://b64` | EnvelopePassword(line-list) | multi export | ✔ |
| `tzvpn://` & `tzvpn-enc://` & `tzvpn-bundle-enc://` | identical payloads | legacy releases | read-only, aliased |
| `vless://…`, `hysteria2://…` | community URIs | third-party tools | ✔ |

Base64 payload rules: encoder emits STD alphabet without padding/wrapping.
Decoder: trim ASCII whitespace → try STD → fall back to URL-safe → error otherwise.
Multi-profile plain text: one URI per line, blank lines ignored, unknown lines produce
typed warnings (never abort whole import).

## Pipe record — exactly 88 positions, `\u007C`-joined

F0="43" (version constant emitted today).
Booleans `"1"/"0"`. User-text fields are sanitized by REMOVING any `'|'` before joining.
b64(x)=base64(STD,NOPAD) of UTF-8 bytes, **not** sanitized further.

| # | Name | Default | Notes |
|---|---|---|---|
|0|version|"43"| emitter constant |
|1|tunnelToken|dnstt| see token map |
|2|name|""| sanitized |
|3|domain|""| sanitized |
|4|resolvers|"host:port:auth" CSV| auth flag 0/1; empty allowed |
|5|authoritativeMode|0| |
|6|keepAliveInterval|5000| int ms |
|7|congestionControl|bbr| bbr\|dcubic |
|8|tcpListenPort|1080| |
|9|tcpListenHost|127.0.0.1| sanitized |
|10|gsoEnabled|0| |
|11|dnsttPublicKey|""| sanitized |
|12|socksUsername|""| nullable→"" |
|13|socksPassword|""| nullable→"" |
|14|sshChainEnabled|0| derived true iff type ∈ {ssh,dnstt_ssh,slipstream_ssh,naive_ssh,vaydns_ssh} on EMIT; stored bit on PARSE |
|15|sshUsername|""| |
|16|sshPassword|""| |
|17|sshPort|22| |
|18|forwardDnsThroughSsh|"0"| deprecated, emit always 0 |
|19|sshHost|127.0.0.1| |
|20|useServerDns|"0"| removed, emit always 0 |
|21|dohUrl|""| |
|22|dnsTransport|udp| udp\|dot\|doh |
|23|sshAuthType|password| password\|key\|keyagent…(opaque tokens preserved) |
|24|sshPrivateKey|b64("")| |
|25|sshKeyPassphrase|b64("")| |
|26|torBridgeLines|b64("")| multiline content inside b64 (safe) |
|27|dnsttAuthoritative|0| |
|28|naivePort|443| |
|29|naiveUsername|""| |
|30|naivePassword|b64("")| note: b64 unlike 29 |
|31|isLocked|0| |
|32|lockPasswordHash|""| format `hex16(salt):hex32(sha256(salt‖pwd))` (legacy lock hashing kept verbatim for cross-version verification) |
|33|expirationDate|0| epoch ms |
|34|allowSharing|0| |
|35|boundDeviceId|""| HMAC device fingerprint |
|36|resolversHidden|0| |
|37|hiddenResolvers|""| same CSV grammar as F4 |
|38|noizdnsStealth|0| |
|39|dnsPayloadSize|0| 0=auto/full |
|40|socks5ServerPort|1080| |
|41|vaydnsDnsttCompat|0| |
|42|vaydnsRecordType|txt| txt/cname/a/aaaa/mx/ns/srv/null/caa |
|43|vaydnsMaxQnameLen|101| |
|44|vaydnsRps|"0.0"| DOUBLE string (locale-independent %g) |
|45|vaydnsIdleTimeout|0| s |
|46|vaydnsKeepalive|0| s |
|47|vaydnsUdpTimeout|0| ms |
|48|vaydnsMaxNumLabels|0| 0=unlimited |
|49|vaydnsClientIdSize|0| 0=default2 |
|50|sshTlsEnabled|0| |
|51|sshTlsSni|""| |
|52|sshHttpProxyHost|""| |
|53|sshHttpProxyPort|8080| |
|54|sshHttpProxyCustomHost|""| |
|55|sshWsEnabled|0| |
|56|sshWsPath|"/"| |
|57|sshWsUseTls|1| default true |
|58|sshWsCustomHost|""| |
|59|sshPayload|b64("")| DPI pre-handshake prefix |
|60|resolverMode|roundrobin| roundrobin\|fanout |
|61|rrSpreadCount|3| ≥1 |
|62|vlessUuid|""| |
|63|vlessSecurity|tls| tls/reality/none |
|64|vlessTransport|ws| ws/tcp/grpc… |
|65|vlessWsPath|"/"| |
|66|cdnIp|""| |
|67|cdnPort|443| |
|68|sniFragmentEnabled|1| default true |
|69|sniFragmentStrategy|micro| micro/multi/sni_split/fake/disorder |
|70|sniFragmentDelayMs|300| |
|71|legacyVlessSni|""| ALWAYS empty since v28 (kept for position stability) |
|72|chPaddingEnabled|0| |
|73|wsHeaderObfuscation|1| default true |
|74|wsPaddingEnabled|0| |
|75|sniSpoofTtl|8| |
|76|fakeDecoyHost|""| |
|77|tcpMaxSeg|0| 0=auto |
|78|vlessSni|""| current SNI slot |
|79|vlessRealityPubKey|""| |
|80|vlessRealityShortId|""| |
|81|vlessRealityFp|chrome| |
|82|hy2Password|""| |
|83|hy2Sni|""| |
|84|hy2Insecure|0| |
|85|hy2Obfs|""| salamander |
|86|hy2ObfsPassword|""| |

Parsing tolerances (required): record MAY be shorter than 88 (Go generator emits 60) —
missing tail ⇒ defaults above. Unknown NEWER versions (F0 > emitted 43 accepted too,
no hard reject — forward compatibility precedent). Extra fields beyond 88 ignored.
Non-numeric where number expected ⇒ field default, per-field recovery (record still loads).
Boolean parse: "1"/"true" = true, everything else false.

### Tunnel token map (EMIT / PARSE)

Parse is case-insensitive and accepts historical aliases:

| Token(s) | Enum |
|---|---|
| ss, slipstream | SLIPSTREAM |
| slipstream_ssh | SLIPSTREAM_SSH |
| dnstt | DNSTT |
| dnstt_ssh | DNSTT_SSH |
| sayedns | NOIZDNS |
| sayedns_ssh | NOIZDNS_SSH |
| ssh | SSH |
| doh | DOH |
| snowflake | SNOWFLAKE |
| naive | NAIVE |
| naive_ssh | NAIVE_SSH |
| socks5 | SOCKS5 |
| vaydns | VAYDNS |
| vaydns_ssh | VAYDNS_SSH |
| vless | VLESS |
| hysteria2 | HYSTERIA2 |

## Envelopes

### EnvelopePassword (`vpntz-bundle-enc://`) — ver byte 0x01
```
[0x01][salt 16][iv 12][ciphertext ‖ GCM tag 16]
KDF: PBKDF2-HMAC-SHA256(pw, salt, 600000 iters, 256-bit)
AEAD: AES-256-GCM, no AAD, 12-byte IV
plaintext: newline-joined vpntz:// records (+ optional inner locked profiles encoded
as PLAIN records carrying their own lock hash — reader applies each line's flags)
```
### EnvelopeDeviceKey (`vpntz-enc://`) — ver byte 0x01
```
[0x01][iv 12][ciphertext ‖ tag 16], key = 32B device key supplied by KeyProvider port
plaintext: ONE pipe record (v43)
```
### New native container (this implementation only)
Scheme `vpntz-vault://b64`: `[magic "VTZ1"][salt16][iv12][ct‖tag]` +
PBKDF2-HMAC-SHA256(700_000) — AEAD binds context via AAD `"vpntz-vault\x01"`;
emitted instead of vpntz-bundle-enc going forward; readers keep full old support.

## Lock hash (field 32 emission)
`hexLower(16B random salt) + ":" + hexLower(SHA-256(salt ‖ UTF-8(password)))`
Verification: recompute & constant-time compare hex strings.

## Community URIs

### vless://uuid@host:port?params#fragment(name)
Mapped: encryption/security, sni, host, path, type(network), flow, fp, pbk, sid,
headerType+Host(ws),serviceName(grpc), alpn(ignored→warning). Unknown keys → warnings.
### hysteria2://auth@host:port?sni&insecure&obfs&obfs-password#name
auth→hy2Password; host→domain (IPv6-bracket aware); port→cdnPort slot (fallback 443);
sni→hy2Sni; insecure(true/1/yes)→flag; obfs:"none"→"", salamander accepted,
ANY other value REJECTS the line with a warning; obfs-password→hy2ObfsPassword;
auth/host blank → hard error.
⚠ AMBIGUITY LEDGER: `m`(ports-hopping), `pinSHA256` and other extras are silently
accepted-and-ignored (observed baseline keeps them unread).

## Observed quirks mirrored intentionally

1. Record-level blank fields fall back differently than URI defaults:
   - F69 strategy: blank → "sni_split" (records) vs absent → "micro" (URI)
   - F70 delay:    blank → 100       (records) vs absent → 300   (URI)
2. Fragment enabled is forced OFF when security == reality regardless of F68.
3. Baseline warned `version > 28` while emitting version 43, producing a spurious
   notice on every self-import. The new layer raises the notice threshold to its own
   emit constant (43) — recorded as an intentional divergence (bug fix).
4. Hysteria2 has no dedicated port slot; position 67 (cdnPort) carries it.
