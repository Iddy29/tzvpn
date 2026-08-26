#!/usr/bin/env bash
# ============================================================================
#  TzGate - Hysteria2 server setup
#  Part of the TzGate server installer (github.com/vpntz/vpn-tz/tzgate)
#
#  Installs the official Hysteria2 server, writes a hardened config with
#  optional Salamander obfuscation, creates a systemd service, opens the
#  firewall, and prints a ready-to-import hysteria2:// link (and QR) for the
#  VPN-TZ app.
#
#  Usage:
#    sudo bash hysteria2-setup.sh                # interactive
#    sudo bash hysteria2-setup.sh --port 443 --password mypass --obfs off
# ============================================================================
set -euo pipefail

CYAN='\033[0;36m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info()  { echo -e "${CYAN}[TzGate]${NC} $*"; }
ok()    { echo -e "${GREEN}[OK]${NC} $*"; }
warn()  { echo -e "${YELLOW}[!]${NC} $*"; }
fail()  { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

[ "$(id -u)" -eq 0 ] || fail "Run as root: sudo bash $0"

# ── Defaults ────────────────────────────────────────────────────────────────
PORT="443"
PASSWORD=""
OBFS="ask"            # ask|on|off
MASQUERADE="https://news.ycombinator.com/"
SNI_DOMAIN=""         # empty = self-signed cert + insecure=1 in the link

while [[ $# -gt 0 ]]; do
    case "$1" in
        --port)     PORT="$2"; shift 2 ;;
        --password) PASSWORD="$2"; shift 2 ;;
        --obfs)     OBFS="$2"; shift 2 ;;          # on | off
        --sni)      SNI_DOMAIN="$2"; shift 2 ;;    # real domain (ACME) - advanced
        --masquerade) MASQUERADE="$2"; shift 2 ;;
        -h|--help)  grep '^#' "$0" | tail -n +3 | head -14; exit 0 ;;
        *) fail "Unknown option: $1" ;;
    esac
done

echo ""
echo "=========================================="
echo "   TzGate - Hysteria2 server setup"
echo "=========================================="
echo ""

# ── Interactive prompts (when not supplied via flags) ───────────────────────
if [ "$PORT" = "443" ] && [ -t 0 ]; then
    read -rp "Listen port [443]: " _p || true
    PORT="${_p:-443}"
fi
if [ -z "$PASSWORD" ]; then
    PASSWORD="$(tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 20)"
    info "Generated auth password: $PASSWORD"
fi
if [ "$OBFS" = "ask" ]; then
    OBFS_PASSWORD="$(tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 16)"
    if [ -t 0 ]; then
        read -rp "Enable Salamander obfuscation? [y/N]: " _o || true
        case "${_o:-n}" in y|Y|yes|Yes) OBFS="on" ;; *) OBFS="off" ;; esac
    else
        OBFS="off"
    fi
else
    OBFS_PASSWORD="$(tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 16)"
fi
if [ "$OBFS" = "on" ] && [ -z "${OBFS_PASSWORD:-}" ]; then
    OBFS_PASSWORD="$(tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 16)"
fi

# ── Public IP detection ─────────────────────────────────────────────────────
info "Detecting public IPv4..."
PUBLIC_IP="$(curl -4 -fsS --max-time 8 https://api.ipify.org 2>/dev/null \
    || curl -4 -fsS --max-time 8 https://ifconfig.me 2>/dev/null \
    || echo '')"
if [ -n "$PUBLIC_IP" ]; then ok "Server IP: $PUBLIC_IP"; else warn "Could not detect public IP (continuing)"; fi

# ── Install Hysteria2 (official installer, direct binary fallback) ──────────
if command -v hysteria >/dev/null 2>&1; then
    ok "Hysteria already installed: $(hysteria version 2>/dev/null | grep -m1 '^Version:' || echo 'unknown')"
else
    info "Installing Hysteria2 (official installer)..."
    if curl -fsSL https://get.hy2.sh/ | bash >/dev/null 2>&1; then
        ok "Installed via get.hy2.sh"
    else
        warn "Official installer failed - trying direct binary download"
        ARCH="$(uname -m)"
        case "$ARCH" in
            x86_64)  HARCH="amd64" ;;
            aarch64) HARCH="arm64" ;;
            *) fail "Unsupported arch: $ARCH" ;;
        esac
        BASE="https://github.com/apernet/hysteria/releases/latest/download"
        curl -fsSL -o /usr/local/bin/hysteria "$BASE/hysteria-linux-$HARCH" \
            || fail "Could not download hysteria binary"
        chmod +x /usr/local/bin/hysteria
        # minimal systemd unit (official layout)
        cat > /etc/systemd/system/hysteria-server.service <<'UNIT'
[Unit]
Description=Hysteria2 Server (TzGate)
After=network.target

[Service]
ExecStart=/usr/local/bin/hysteria server -c /etc/hysteria/config.yaml
WorkingDirectory=/etc/hysteria
Restart=on-failure
RestartSec=5
LimitNOFILE=infinity

[Install]
WantedBy=multi-user.target
UNIT
    fi
fi
mkdir -p /etc/hysteria

# ── TLS certificate ─────────────────────────────────────────────────────────
if [ -n "$SNI_DOMAIN" ]; then
    info "SNI domain provided - using ACME (port 80/443 must be free)"
    TLS_BLOCK="acme:
  domains:
    - $SNI_DOMAIN
  email: admin@$(echo "$SNI_DOMAIN" | sed 's/^www\.//')"
    CERT_NOTE="real certificate (insecure=0)"
    URI_SNI="sni=$SNI_DOMAIN"
else
    if [ -n "$PUBLIC_IP" ]; then CERT_CN="${PUBLIC_IP}"; else CERT_CN="www.microsoft.com"; fi
    info "Generating self-signed certificate (CN=$CERT_CN)"
    openssl req -x509 -newkey rsa:2048 -nodes -days 3650 \
        -keyout /etc/hysteria/server.key -out /etc/hysteria/server.crt \
        -subj "/CN=$CERT_CN" >/dev/null 2>&1 || fail "openssl not available"
    TLS_BLOCK="cert: /etc/hysteria/server.crt
  key: /etc/hysteria/server.key"
    CERT_NOTE="self-signed (clients use insecure=1)"
    URI_SNI=""
fi
chmod 600 /etc/hysteria/server.key 2>/dev/null || true

# ── Config ──────────────────────────────────────────────────────────────────
info "Writing /etc/hysteria/config.yaml"
{
    echo "listen: :$PORT"
    echo ""
    echo "tls:"
    echo "$TLS_BLOCK" | sed 's/^/  /'
    echo ""
    if [ "$OBFS" = "on" ]; then
        echo "obfs:"
        echo "  type: salamander"
        echo "  salamander:"
        echo "    password: $OBFS_PASSWORD"
        echo ""
    fi
    echo "quic:"
    echo "  initStreamReceiveWindow: 8388608"
    echo "  maxStreamReceiveWindow: 8388608"
    echo "  initConnReceiveWindow: 20971520"
    echo "  maxConnReceiveWindow: 20971520"
    echo "  maxIdleTimeout: 30s"
    echo "  keepAlivePeriod: 10s"
    echo ""
    echo "bandwidth:"
    echo "  up: 1 gbps"
    echo "  down: 1 gbps"
    echo ""
    echo "ignoreClientBandwidth: false"
    echo ""
    echo "auth:"
    echo "  type: password"
    echo "  password: $PASSWORD"
    echo ""
    echo "masquerade:"
    echo "  type: proxy"
    echo "  proxy:"
    echo "    url: $MASQUERADE"
    echo "    rewriteHost: true"
} > /etc/hysteria/config.yaml
chmod 600 /etc/hysteria/config.yaml

# ── Service ─────────────────────────────────────────────────────────────────
info "Enabling + starting hysteria-server.service"
systemctl daemon-reload
systemctl enable hysteria-server >/dev/null 2>&1 || true
systemctl restart hysteria-server
sleep 1
if systemctl is-active --quiet hysteria-server; then
    ok "Service is running"
else
    fail "Service failed to start - check: journalctl -u hysteria-server -e"
fi

# ── Firewall ────────────────────────────────────────────────────────────────
if command -v ufw >/dev/null 2>&1; then
    info "Opening UDP $PORT in ufw"
    ufw allow "$PORT/udp" >/dev/null 2>&1 || true
fi

# ── Build the hysteria2:// link ─────────────────────────────────────────────
LINK_HOST="${PUBLIC_IP:-<SERVER_IP>}"
QUERY="insecure=1"
[ -n "$URI_SNI" ] && QUERY="sni=$SNI_DOMAIN"
if [ "$OBFS" = "on" ]; then
    QUERY="$QUERY&obfs=salamander&obfs-password=$OBFS_PASSWORD"
fi
LINK="hysteria2://${PASSWORD}@${LINK_HOST}:${PORT}/?${QUERY}#TzGate-Hy2"

echo ""
echo "=========================================="
echo -e "  ${GREEN}Hysteria2 server is ready!${NC}"
echo "=========================================="
echo ""
echo "  Server:   ${LINK_HOST}:${PORT} (UDP)"
echo "  TLS:      $CERT_NOTE"
echo "  Obfs:     $OBFS"
echo ""
echo "  Import this link into the VPN-TZ app:"
echo ""
echo -e "  ${YELLOW}${LINK}${NC}"
echo ""

# QR code in terminal if possible
if command -v qrencode >/dev/null 2>&1; then
    echo "  QR code:"
    qrencode -t ANSIUTF8 "$LINK" && echo "" || true
else
    if command -v apt-get >/dev/null 2>&1; then
        warn "Tip: 'apt install qrencode' then re-run to print a scannable QR."
    fi
fi

echo "  Management:"
echo "    systemctl status hysteria-server"
echo "    journalctl -u hysteria-server -e"
echo "    nano /etc/hysteria/config.yaml && systemctl restart hysteria-server"
echo ""
