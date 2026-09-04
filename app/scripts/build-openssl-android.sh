#!/bin/bash
#
# Build OpenSSL from official source for Android (all required ABIs).
# Usage: ./build-openssl-android.sh
#
# Environment variables:
#   OPENSSL_VERSION - OpenSSL version to build (default: 3.0.15)
#   OPENSSL_SHA256  - Expected SHA-256 of the source tarball (default below)
#   OUTPUT_DIR      - Output directory (default: ~/android-openssl/android-ssl)
#   ANDROID_NDK_HOME - Path to Android NDK
#
# Fails on any error and verifies that each ABI produced the expected
# libssl.a / libcrypto.a static libraries plus include/openssl headers.

set -euo pipefail

OPENSSL_VERSION="${OPENSSL_VERSION:-3.0.15}"
# SHA-256 of openssl-${OPENSSL_VERSION}.tar.gz (openssl.org)
EXPECTED_SHA256="${OPENSSL_SHA256:-23c666d0edf20f14249b3d8f0368acaee9ab585b09e1de82107c66e1f3ec9533}"
OUTPUT_DIR="${OUTPUT_DIR:-$HOME/android-openssl/android-ssl}"
ANDROID_API=24

# The ABIs the app actually ships (see app/build.gradle.kts supportedAbis).
declare -a ABIS=(arm64-v8a armeabi-v7a x86_64)
declare -a TARGETS=(android-arm64 android-arm android-x86_64)

# Find NDK (ANDROID_NDK_HOME first, then auto-detect from common locations).
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    for base in "$HOME/Library/Android/sdk/ndk" "${ANDROID_HOME:-}/ndk" "${ANDROID_SDK_ROOT:-}/ndk"; do
        if [ -d "$base" ]; then
            ANDROID_NDK_HOME=$(ls -d "$base"/*/ 2>/dev/null | head -1 | sed 's:/$::')
            [ -n "$ANDROID_NDK_HOME" ] && break
        fi
    done
fi

if [ -z "${ANDROID_NDK_HOME:-}" ] || [ ! -d "$ANDROID_NDK_HOME" ]; then
    echo "Error: Android NDK not found. Set ANDROID_NDK_HOME environment variable." >&2
    exit 1
fi

echo "Using NDK: $ANDROID_NDK_HOME"
echo "OpenSSL version: $OPENSSL_VERSION"
echo "Expected SHA-256: $EXPECTED_SHA256"
echo "Output directory: $OUTPUT_DIR"

# Create working directory
WORK_DIR=$(mktemp -d)
trap 'rm -rf "$WORK_DIR"' EXIT
cd "$WORK_DIR"

# Download and verify the official OpenSSL source
echo "Downloading OpenSSL ${OPENSSL_VERSION}..."
curl -fL -o "openssl-${OPENSSL_VERSION}.tar.gz" \
    "https://www.openssl.org/source/openssl-${OPENSSL_VERSION}.tar.gz"
echo "${EXPECTED_SHA256}  openssl-${OPENSSL_VERSION}.tar.gz" | sha256sum -c - \
    || { echo "ERROR: OpenSSL source checksum mismatch." >&2; exit 1; }
tar xzf "openssl-${OPENSSL_VERSION}.tar.gz"
cd "openssl-${OPENSSL_VERSION}"

# Detect host platform
case "$(uname -s)" in
    Darwin*) HOST_TAG="darwin-x86_64" ;;
    Linux*)  HOST_TAG="linux-x86_64" ;;
    *)
        echo "Unsupported host platform" >&2
        exit 1
        ;;
esac

TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$HOST_TAG"

# Build function: $1 = ABI dir name, $2 = OpenSSL Configure target
build_openssl() {
    local ABI=$1
    local TARGET=$2

    echo ""
    echo "=========================================="
    echo "Building OpenSSL for $ABI"
    echo "=========================================="

    export ANDROID_NDK_ROOT="$ANDROID_NDK_HOME"
    export PATH="$TOOLCHAIN/bin:$PATH"

    local OUTPUT="$OUTPUT_DIR/$ABI"
    mkdir -p "$OUTPUT"

    make clean >/dev/null 2>&1 || true

    # Use neutral --prefix/--openssldir/--libdir so the build-machine path is
    # not baked into OPENSSLDIR/ENGINESDIR/MODULESDIR in the compiled binary.
    # Install via DESTDIR then copy the needed headers and static libs to $OUTPUT.
    ./Configure "$TARGET" \
        -D__ANDROID_API__=$ANDROID_API \
        --prefix=/usr/local \
        --openssldir=/etc/ssl \
        --libdir=lib \
        no-shared \
        no-tests \
        no-ui-console

    make -j"$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 4)"
    local DESTDIR_TMP="$OUTPUT/destdir"
    make install_sw DESTDIR="$DESTDIR_TMP"
    mkdir -p "$OUTPUT/lib" "$OUTPUT/include"
    cp -rp "$DESTDIR_TMP/usr/local/include/." "$OUTPUT/include/"
    find "$DESTDIR_TMP/usr/local/lib" -name "*.a" -exec cp {} "$OUTPUT/lib/" \;
    rm -rf "$DESTDIR_TMP"

    # Verify expected layout
    for f in lib/libssl.a lib/libcrypto.a; do
        if [ ! -f "$OUTPUT/$f" ]; then
            echo "ERROR: missing $OUTPUT/$f" >&2
            exit 1
        fi
    done
    if [ ! -d "$OUTPUT/include/openssl" ]; then
        echo "ERROR: missing $OUTPUT/include/openssl" >&2
        exit 1
    fi

    echo "OpenSSL for $ABI installed to $OUTPUT"
}

for i in "${!ABIS[@]}"; do
    build_openssl "${ABIS[$i]}" "${TARGETS[$i]}"
done

echo ""
echo "=========================================="
echo "OpenSSL build complete!"
echo "Output directory: $OUTPUT_DIR"
echo "=========================================="
