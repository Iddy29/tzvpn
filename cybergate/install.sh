#!/bin/bash
# CyberGate installer — download binary and run `cybergate install`
set -e

REPO="anonvector/cybergate"
INSTALL_DIR="/usr/local/bin"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
CYAN='\033[1;36m'
NC='\033[0m'

info()    { echo -e "${GREEN}[+]${NC} $1"; }
error()   { echo -e "${RED}[-]${NC} $1"; exit 1; }

# Check root
[[ $EUID -ne 0 ]] && error "This script must be run as root (sudo)"

# Detect architecture
ARCH=$(uname -m)
case "$ARCH" in
    x86_64)  ARCH="amd64" ;;
    aarch64) ARCH="arm64" ;;
    *)       error "Unsupported architecture: $ARCH" ;;
esac

OS=$(uname -s | tr '[:upper:]' '[:lower:]')
[[ "$OS" != "linux" ]] && error "CyberGate only supports Linux"

BINARY="cybergate-${OS}-${ARCH}"

# Override with: CYBERGATE_RELEASE_TAG=v1.5.1 bash install.sh
RELEASE_TAG="${CYBERGATE_RELEASE_TAG:-}"
CHANNEL=""  # ← set to "dev" on dev branch, empty on main

if [[ -n "$RELEASE_TAG" ]]; then
    URL="https://github.com/${REPO}/releases/download/${RELEASE_TAG}/${BINARY}"
elif [[ "$CHANNEL" == "dev" ]]; then
    # Find the latest dev pre-release tag via GitHub API
    DEV_TAG=$(curl -fsSL "https://api.github.com/repos/${REPO}/releases" \
        | grep -o '"tag_name": *"[^"]*-dev"' | head -1 | grep -o '"[^"]*-dev"' | tr -d '"')
    if [[ -n "$DEV_TAG" ]]; then
        URL="https://github.com/${REPO}/releases/download/${DEV_TAG}/${BINARY}"
        info "Dev channel: using release ${DEV_TAG}"
    else
        URL="https://github.com/${REPO}/releases/latest/download/${BINARY}"
        info "No dev release found, falling back to latest stable"
    fi
else
    URL="https://github.com/${REPO}/releases/latest/download/${BINARY}"
fi

echo -e "${CYAN}"
echo "   _____ _ _       _____       _       "
echo "  / ____| (_)     / ____|     | |      "
echo " | (___ | |_ _ __| |  __  __ _| |_ ___ "
echo "  \___ \| | | '_ \ | |_ |/ _\` | __/ _ \\"
echo "  ____) | | | |_) | |__| | (_| | ||  __/"
echo " |_____/|_|_| .__/ \_____|\__,_|\__\___|"
echo "             | |                         "
echo "             |_|                         "
echo -e "${NC}"

# Kill any running cybergate/tunnel processes and remove old binary
killall cybergate 2>/dev/null || true
killall dnstt-server 2>/dev/null || true
killall slipstream-server 2>/dev/null || true
rm -f "${INSTALL_DIR}/cybergate"

info "Downloading cybergate ($OS/$ARCH)..."
if command -v curl &>/dev/null; then
    curl -fsSL "$URL" -o "${INSTALL_DIR}/cybergate"
elif command -v wget &>/dev/null; then
    wget -qO "${INSTALL_DIR}/cybergate" "$URL"
else
    error "Neither curl nor wget found"
fi

chmod +x "${INSTALL_DIR}/cybergate"

info "Running cybergate install..."
if ! "${INSTALL_DIR}/cybergate" install </dev/tty; then
    error "cybergate install failed — run 'sudo cybergate install' to retry"
fi

info "Done! Run 'sudo cybergate' to see the menu."
