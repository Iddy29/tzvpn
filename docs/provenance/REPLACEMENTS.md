# Replacement Registry — SlipNet-derived → VPN-TZ independent

Each entry records: replaced component, new location, independence basis, and
the evidence that behavior is preserved. Baseline reference: `main@779ec73`.

| Phase | Replaced | New implementation | Independence basis | Equivalence evidence |
|---|---|---|---|---|
| 1 | `data/export/ConfigExporter+ConfigImporter` logic (URI codecs, record codec, encrypted containers) | `com.vpntz.app.config.*` (ProfileDocument, ProfileRecordCodec, CommunityUriCodec, WireBase64, crypto/VaultCrypto, crypto/Envelopes, ConfigGateway, DeviceKeyCipher) | Re-designed from the frozen behavioral spec `WIRE_FORMAT.md`; new model taxonomy (immutable document + tolerant parse outcome), new error model, new vault container; no source structure copied | 130 JVM tests: 88-field golden vector, 200-doc property roundtrip, community URI tables, AEAD tamper/wrong-password, short-tail defaults, legacy `tzvpn://` aliases, Go cross-language fixture contract (`tzgate/internal/clientcfg/crosslang_test.go`), both-flavor compile+test runs |
| 1 | bundle encryption writer (app-side) | `vpntz-vault://` container (opt-in) + legacy-compatible envelope emitter | PBKDF2(700k)+AES-GCM with AAD context binding; legacy envelope re-derived from spec (framing [01\|salt16\|iv12\|ct‖tag], PBKDF2 600k) | deterministic framing vectors + wrong-password/tamper/truncate tests |

Intentional divergences (documented, non-breaking):
- import "newer version" notice threshold = own emit constant (43) — fixes baseline's spurious self-import warning (it compared against 28 while emitting 43).
- `vpntz-enc://` single-profile payloads REQUIRE the device-key port; without it the line is skipped with a warning instead of crashing the import.
- hysteria2 `m`/`pinSHA256` params remain accepted-and-ignored (as in baseline).

Still on the derived path (NOT yet replaced — tracked for later phases):
`data/local/*`, `datastore`, `domain/*`, `service/*`, `tunnel/*`, `presentation/*`, `di/*` (except the two new providers), Go `cli/`, Go `tzgate/` (business logic), `docs/USER_GUIDE.md`.
