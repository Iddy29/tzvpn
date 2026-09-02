# Phase 5 — Native / FFI Build Audit

Audit date: 2026-09-02. Branch: `rewrite/independence`.

Nothing below was **rebuilt** in this environment. These are the exact commands
and prerequisites identified from the repository so that, once the toolchain and
a device/emulator are available, the native artifacts can be regenerated and the
adapters verified against them. Do not treat this as a claim that the artifacts
were compiled here.

## Artifacts and how they are produced

| Artifact | Engine(s) | Build entry point | Prerequisites |
|---|---|---|---|
| `app/libs/golibs-full.aar` | DNSTT/NoizDNS (via `noizdns/mobile`), VayDNS, snowflake, Hysteria2, VLESS+reality | `gomobile-build/Makefile`: `make build-full` (`gomobile bind … -o ../app/libs/golibs-full.aar`) | Go + `gomobile` + Android NDK + `GOFLAGS`/`-target android/arm,arm64,amd64 -androidapi 21` |
| `app/libs/golibs-lite.aar` | subset (NozDNS, VayDNS, Hysteria2, VLESS+reality) | `make build-lite` | same as above |
| `app/src/main/rust/slipstream-rust{,-plus}` → `libslipstream*.so` | tz-kitonga (slipstream) | Gradle `cargo` task (`org.mozilla.rust-android-gradle`) for `cargoBuildArm/Arm64/X86_64`, target from `linker-wrapper.py` | Rust `cargo 1.98`, `rust-android-gradle` plugin, Android NDK, `python3` (ported to `python`) |
| `app/src/main/cpp/hev-socks5-tunnel-src` | hev-socks5-tunnel (TUN/tun2socks) | Gradle `externalNativeBuild` / CMake | Android NDK, CMake |
| `app/src/full/jniLibs/*/libnaive.so`, `libobfs4proxy.so`, `libtor.so` | NaiveProxy, obfs4, Tor | prebuilt `.so` committed under `jniLibs` | Rust/C cross-build on the project's build host |

Exact Go AAR command (from `gomobile-build/Makefile`):

```bash
cd gomobile-build
# full flavor
gomobile bind -trimpath -target android/arm,android/arm64,android/amd64 -androidapi 21 \
  -ldflags '-s -w -checklinkname=0 -extldflags -Wl,-z,max-page-size=16384,-z,common-page-size=16384' \
  -o ../app/libs/golibs-full.aar \
  noizdns/mobile vaydns-mobile/vaydns snowflake-mobile/snowflake \
  hysteria2-mobile/hysteria2 vlessreality-mobile/vlessreality
# lite flavor
gomobile bind -trimpath -target android/arm,android/arm64,android/amd64 -androidapi 21 \
  -ldflags '-s -w -checklinkname=0 -extldflags -Wl,-z,max-page-size=16384,-z,common-page-size=16384' \
  -o ../app/libs/golibs-lite.aar \
  noizdns/mobile vaydns-mobile/vaydns hysteria2-mobile/hysteria2 vlessreality-mobile/vlessreality
```

`gomobile` must be installed and run from a Go module context that includes the
mobile packages (`go install golang.org/x/mobile/cmd/gomobile@latest`).

## Rust (slipstream) build notes

The gradle `rust-android-gradle` plugin drives everything; the module is
`src/main/rust/slipstream-rust`. It requires the Android toolchain targets and
a `linker-wrapper.py` (present at `src/main/rust/linker-wrapper.py`). The
`src/main/rust/slipstream-rust-plus` workspace is a second slipstream variant.

```bash
# if you need to test the Rust build standalone (needs rust-android targets + NDK)
cargo metadata
```

## What is NOT achievable in the current environment

- No `gomobile` in a usable module context (`gomobile version` → "no required
  module provides package golang.org/x/mobile/cmd/gomobile").
- No `adb` / emulator / device (`adb version` → command not found), so no
  on-device connectivity smoke test.
- Android NDK is installed at `%LOCALAPPDATA%\Android\Sdk\ndk\29.0.14206865`
  but there is no `ANDROID_NDK_HOME` and no cross-build target wired for
  gomobile in this shell.
- Go 1.25.10 and Rust 1.98.0 are installed and could compile the Go CLI/server
  (`go -C cli build ./...`, `go -C tzgate build ./...`) and Rust workspaces
  (`cargo build`), but recompiling those does **not** validate tunnel
  connectivity inside the Android app.

## Required toolchain to finish Phase 5 device verification

1. Android emulator or physical device with `adb` on PATH.
2. `gomobile` (Go mobile bind) + Go toolchain + Android NDK wired as
   `ANDROID_NDK_HOME`, so `make build-full`/`build-lite` produce the AARs.
3. Rust Android targets + `linker-wrapper.py` + NDK for the slipstream `.so`.
4. A reachable `tzgate` dev server per protocol to pair against.
