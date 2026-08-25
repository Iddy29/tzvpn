# CyberGate

Unified tunnel manager for Linux servers. Manages DNS tunnels (DNSTT, NoizDNS, Slipstream, VayDNS) and HTTPS proxies (NaiveProxy) with systemd services, multi-tunnel DNS routing, and user management. Designed for use with the [SlipNet](https://github.com/anonvector/SlipNet) Android VPN app.

[![oosmetrics](https://api.oosmetrics.com/api/v1/badge/achievement/12767996-2dd6-42ed-89a6-5f786c4662b4.svg)](https://oosmetrics.com/repo/anonvector/cybergate)

## Features

- **Multi-transport**: DNSTT/NoizDNS (DNS tunnels with Curve25519 encryption), Slipstream (QUIC-based DNS), VayDNS (KCP-based DNS with Curve25519), NaiveProxy (HTTPS with Caddy), StunTLS (SSH over TLS + WebSocket)
- **Dual backend**: Built-in SOCKS5 proxy or SSH forwarding (custom SSH port supported)
- **DNS routing**: Single-tunnel or multi-tunnel mode with domain-based dispatch
- **External routing**: Forward DNS queries for a domain to a custom port for user-managed protocols
- **WARP integration**: Optional Cloudflare WARP outbound routing (see [dnstun-ezpz](https://github.com/aleskxyz/dnstun-ezpz) for an alternative approach)
- **User management**: Multi-user SSH + SOCKS credentials (all users authenticate simultaneously), with bulk creation of up to 500 users per call
- **Live dashboard**: Real-time TUI with CPU, RAM, traffic sparklines, per-protocol connection stats, and tunnel status
- **Diagnostics**: Built-in health checks for services, ports, keys, DNS resolution, and boot persistence
- **Interactive TUI + CLI**: Menu-driven setup or scriptable subcommands
- **Systemd integration**: Service creation, lifecycle, and logs
- **Auto-TLS**: Let's Encrypt via Caddy for NaiveProxy tunnels
- **Self-update**: Version checking and binary replacement from GitHub releases
- **Client sharing**: Generates `slipnet://` URIs for one-tap app import

## Requirements

- **OS**: Linux (Ubuntu 20.04+, Debian 10+, or similar)
- **Domain**: DNS A record pointed at your server (required for DNS tunnels and NaiveProxy)
- **Ports**: 53/udp (DNS tunnels), 443/tcp (NaiveProxy, StunTLS)

## Quick Start

**One-liner install:**

```bash
curl -fsSL https://raw.githubusercontent.com/anonvector/cybergate/main/install.sh | sudo bash
```

**Or build from source:**

```bash
git clone https://github.com/Iddy29/tzvpn/tree/main/cybergate.git
cd cybergate
make build
sudo ./cybergate install
```

**Offline install (SCP to server):**

Download the binaries you need from the [latest release](https://github.com/Iddy29/tzvpn/tree/main/cybergate/releases):

```bash
# On your local machine — download binaries
mkdir cybergate-bundle && cd cybergate-bundle
curl -LO https://github.com/Iddy29/tzvpn/tree/main/cybergate/releases/latest/download/cybergate-linux-amd64
curl -LO https://github.com/Iddy29/tzvpn/tree/main/cybergate/releases/latest/download/dnstt-server-linux-amd64
curl -LO https://github.com/Iddy29/tzvpn/tree/main/cybergate/releases/latest/download/slipstream-server-linux-amd64
curl -LO https://github.com/Iddy29/tzvpn/tree/main/cybergate/releases/latest/download/caddy-naive-linux-amd64

# SCP to server
scp * user@server:/tmp/cybergate/

# On the server
chmod +x /tmp/cybergate/*
sudo cp /tmp/cybergate/cybergate-linux-amd64 /usr/local/bin/cybergate
sudo cybergate install --bin-dir /tmp/cybergate
```

Then launch the interactive menu:

```bash
sudo cybergate
```

## CLI Usage

```
cybergate                        # Interactive TUI menu
cybergate install                # Install dependencies and configure server
cybergate uninstall              # Remove all services, configs, and binaries
cybergate update                 # Self-update and restart all services
cybergate restart                # Restart all services (DNS router, tunnels, SOCKS)
cybergate users                  # Manage SSH/SOCKS users and view configs
cybergate users add              # Add a single user
cybergate users bulk_add         # Add multiple users in one batch (random creds, up to 500)
cybergate users remove           # Remove a user
cybergate users list             # List users and their per-tunnel configs
cybergate stats                  # Live dashboard (CPU, RAM, traffic, connections, tunnels)
cybergate diag                   # Run diagnostics (services, ports, keys, DNS, boot status)
cybergate mtu [value]            # Set MTU for all DNSTT/NoizDNS/VayDNS tunnels at once

# Tunnel management
cybergate tunnel add             # Add tunnel(s) — supports multi-select and "both" backend
cybergate tunnel edit [tag]      # Edit tunnel settings (tag, MTU, keys)
cybergate tunnel remove [tag]    # Remove a tunnel
cybergate tunnel remove --all    # Remove all tunnels at once
cybergate tunnel start [tag]     # Start a tunnel
cybergate tunnel stop [tag]      # Stop a tunnel
cybergate tunnel status          # Show all tunnel statuses
cybergate tunnel status [tag]    # Show tunnel details (keys, MTU, port)
cybergate tunnel share [tag]     # Generate slipnet:// URI for clients
cybergate tunnel logs [tag]      # View tunnel logs

# DNS routing
cybergate router status          # Show DNS routing config
cybergate router mode            # Switch between single/multi mode
cybergate router switch          # Change active tunnel (single mode)

# Configuration
cybergate config export          # Export configuration
cybergate config import          # Import configuration

# Internal (used by systemd services)
cybergate dnsrouter serve        # Start DNS router
cybergate socks serve            # Start built-in SOCKS5 proxy
cybergate stuntls serve          # Start StunTLS proxy
```

### Non-Interactive Examples

All commands support flags for scripting and automation. If any required flag is omitted, cybergate falls back to an interactive prompt.

```bash
# DNSTT tunnel
sudo cybergate tunnel add \
  --transport dnstt \
  --backend socks \
  --tag mydnstt \
  --domain t.example.com

# DNSTT tunnel with custom Curve25519 keys
sudo cybergate tunnel add \
  --transport dnstt \
  --backend socks \
  --tag mytunnel \
  --domain t.example.com \
  --private-key <64-char-hex> \
  --public-key <64-char-hex>   # optional, validated if provided

# DNSTT with both backends (creates mydnstt-socks + mydnstt-ssh)
sudo cybergate tunnel add \
  --transport dnstt \
  --backend both \
  --tag mydnstt \
  --domain t.example.com

# VayDNS tunnel (KCP + Curve25519)
sudo cybergate tunnel add \
  --transport vaydns \
  --backend socks \
  --tag myvaydns \
  --domain v.example.com

# VayDNS with all tuning parameters
sudo cybergate tunnel add \
  --transport vaydns \
  --backend both \
  --tag myvaydns \
  --domain v.example.com \
  --record-type txt \
  --idle-timeout 10s \
  --keep-alive 2s \
  --clientid-size 2 \
  --queue-size 512

# Slipstream tunnel
sudo cybergate tunnel add \
  --transport slipstream \
  --backend ssh \
  --tag myslip \
  --domain s.example.com

# NaiveProxy tunnel
sudo cybergate tunnel add \
  --transport naive \
  --backend socks \
  --tag myproxy \
  --domain example.com \
  --email admin@example.com \
  --decoy-url https://www.wikipedia.org

# StunTLS tunnel (SSH over TLS + WebSocket)
sudo cybergate tunnel add \
  --transport stuntls \
  --tag mytls

# External DNS routing (forward queries to a custom port)
sudo cybergate tunnel add \
  --transport external \
  --tag my-proto \
  --domain j.example.com \
  --port 5301
# Queries for j.example.com route to 127.0.0.1:5301

# Direct SSH / SOCKS5 transports
sudo cybergate tunnel add --transport direct-ssh --tag myssh
sudo cybergate tunnel add --transport direct-socks5 --tag mysocks

# Rename a tunnel
sudo cybergate tunnel edit --tag mydnstt --new-tag my-tunnel

# Change MTU on a DNSTT tunnel
sudo cybergate tunnel edit --tag mydnstt --mtu 1232

# Set MTU for all DNSTT/NoizDNS/VayDNS tunnels at once (rewrites and restarts each service)
sudo cybergate mtu 1200

# Tune VayDNS parameters
sudo cybergate tunnel edit --tag myvaydns \
  --mtu 1232 \
  --record-type txt \
  --idle-timeout 10s \
  --keep-alive 2s \
  --clientid-size 2 \
  --queue-size 512

# View tunnel details (keys, MTU, port, status)
sudo cybergate tunnel status --tag mydnstt

# Share tunnel config as slipnet:// URI
sudo cybergate tunnel share mydnstt

# Bulk-add SSH/SOCKS users (random passwords, up to 500 per call)
sudo cybergate users bulk_add --count=50 --prefix=user
# Creates user001..user050 with random passwords. A single SOCKS reload
# and WARP rule sync runs for the whole batch.
```

## Architecture

```
                       ┌──────────────────┐
                       │  SlipNet Client  │
                       │                  │
                       └────────┬─────────┘
                                │
              DNS :53/udp ──────┼────── HTTPS/TLS :443/tcp
                    │           │           │
┌───────────────────┼───────────┼───────────┼──────────────────┐
│  SERVER           v           │           v                  │
│                               │                              │
│  ┌────────────────────────┐   │   ┌───────────────────────┐  │
│  │      DNS Router        │   │   │     NaiveProxy        │  │
│  │  domain-based dispatch │   │   │  Caddy + Auto-TLS     │  │
│  │  single / multi mode   │   │   │  + decoy website      │  │
│  │  + external routing    │   │   └───────────┬───────────┘  │
│  └──┬────────┬────────┬───┘   │               │              │
│     │        │        │       │   ┌───────────────────────┐  │
│     v        v        v       │   │     StunTLS           │  │
│  ┌──────┐┌────────┐┌──────┐   │   │  SSH over TLS + WS   │  │
│  │DNSTT ││Slip-   ││VayDNS│   │   │  self-signed cert     │  │
│  │NoizDN││stream  ││      │   │   └───────────┬───────────┘  │
│  │──────││────────││──────│   │               │              │
│  │DNS   ││QUIC    ││KCP   │   │               │              │
│  │Curve ││TLS cert││Curve │   │               │              │
│  │25519 ││        ││25519 │   │               │              │
│  └──┬───┘└───┬────┘└──┬───┘   │               │              │
│     └────────┼────────┘       │               │              │
│              │                │               │              │
│              v                v               v              │
│  ┌──────────────────────────────────────────────────────┐    │
│  │                    Backend Layer                     │    │
│  │                                                      │    │
│  │   ┌──────────────────┐    ┌──────────────────────┐   │    │
│  │   │  SOCKS5 Proxy    │    │   SSH Forwarding     │   │    │
│  │   │  built-in Go     │    │   port forwarding    │   │    │
│  │   │  :1080           │    │   :22 (configurable) │   │    │
│  │   └────────┬─────────┘    └──────────┬───────────┘   │    │
│  │            └─────────┬───────────────┘               │    │
│  └──────────────────────┼───────────────────────────────┘    │
│                         v                                    │
│              ┌──────────────────────┐                        │
│              │   WARP (optional)    │                        │
│              │  Cloudflare outbound │                        │
│              └──────────┬───────────┘                        │
│                         v                                    │
│                    Internet                                  │
└──────────────────────────────────────────────────────────────┘

  systemd: cybergate-dnsrouter, cybergate-socks5, cybergate-{tag}
```

### Transport Types

| Transport | Protocol | Port | Description |
|-----------|----------|------|-------------|
| **DNSTT/NoizDNS** | DNS | 53/udp | Curve25519 encrypted DNS tunnel. A single server serves both DNSTT and NoizDNS clients. NoizDNS adds DPI evasion with base36/hex encoding and CDN prefix stripping |
| **Slipstream** | QUIC DNS | 53/udp | QUIC-based tunnel with certificate authentication |
| **VayDNS** | KCP DNS | 53/udp | KCP-based DNS tunnel with Curve25519 encryption. Supports configurable idle timeout, keepalive, queue size, and multiple DNS record types |
| **NaiveProxy** | HTTPS | 443/tcp | Caddy with forwardproxy plugin. Auto-TLS via Let's Encrypt. Probe-resistant with decoy site |
| **StunTLS** | TLS/WSS | 443/tcp | SSH over TLS + WebSocket proxy. Auto-detects WebSocket, HTTP CONNECT, raw TLS, and payload (DPI bypass) modes. Self-signed TLS cert, no domain required |
| **External** | DNS | 53/udp | Routes DNS queries for a domain to a user-specified UDP port. No managed service — use for custom/private protocol testing |

### Domain Layout

Each DNS tunnel instance requires its own subdomain. When using both SOCKS and SSH backends, the install auto-generates subdomains by appending `s` to the SSH variant:

| Tunnel | Domain | Backend |
|--------|--------|---------|
| dnstt-socks | `t.example.com` | SOCKS5 |
| dnstt-ssh | `ts.example.com` | SSH |
| slipstream-socks | `s.example.com` | SOCKS5 |
| slipstream-ssh | `ss.example.com` | SSH |
| vaydns-socks | `v.example.com` | SOCKS5 |
| vaydns-ssh | `vs.example.com` | SSH |
| naive-socks | `example.com` | SOCKS5 (shared domain) |
| naive-ssh | `example.com` | SSH (shared domain) |

NaiveProxy tunnels share a domain since they use HTTPS (port 443), not DNS. DNSTT and NoizDNS also share a domain — the same server handles both client types.

**Required DNS records** (for the example above):

```
A   ns.example.com       → <server IP>
NS  t.example.com        → ns.example.com
NS  ts.example.com       → ns.example.com
NS  s.example.com        → ns.example.com
NS  ss.example.com       → ns.example.com
NS  v.example.com        → ns.example.com
NS  vs.example.com       → ns.example.com
A   example.com           → <server IP>
```

### Routing Modes

- **Single mode**: One active tunnel runs; DNS router on port 53 forwards to it
- **Multi mode**: All tunnels run on local ports; DNS router on port 53 dispatches queries by domain. Auto-enabled when multiple DNS tunnels are created.

## Client Configuration

After creating a tunnel, generate a shareable config:

```bash
sudo cybergate tunnel share mytunnel
```

This outputs a `slipnet://` URI that can be scanned or imported into the SlipNet Android app. For DNSTT tunnels, you'll be asked to choose between a DNSTT or NoizDNS client profile — both connect to the same server, but NoizDNS profiles enable DPI evasion on the client side.

### User Model

Users are **global**, not scoped to specific tunnels or transports. `cybergate users add` only asks for a username and password — the protocol is a property of the tunnel, chosen at `tunnel add` time. Every user can authenticate against every tunnel using the same credentials, and `cybergate users list` prints one config block per (user × tunnel) pair. The client picks which tunnel to use by importing the matching `slipnet://` URI.

## File Locations

| Path | Description |
|------|-------------|
| `/etc/cybergate/config.json` | Main configuration |
| `/etc/cybergate/tunnels/` | Per-tunnel keys, certs, and configs |
| `/usr/local/bin/cybergate` | CyberGate binary (includes built-in SOCKS5 proxy) |
| `/usr/local/bin/dnstt-server` | DNSTT transport binary |
| `/usr/local/bin/slipstream-server` | Slipstream transport binary |
| `/usr/local/bin/vaydns-server` | VayDNS transport binary |
| `/usr/local/bin/caddy-naive` | Caddy with NaiveProxy plugin |

## Building

```bash
make build              # Build for current platform
make build-linux        # Cross-compile for linux/amd64 and linux/arm64
make test               # Run tests
make release            # Build release binaries
```

## Credits

Built on top of [dnstm](https://github.com/net2share/dnstm) and [vaydns](https://github.com/net2share/vaydns) by [net2share](https://github.com/net2share). WARP integration inspired by [dnstun-ezpz](https://github.com/aleskxyz/dnstun-ezpz).

## License

AGPL-3.0

## Hysteria2

Hysteria2 (QUIC proxy, with optional Salamander obfuscation) is installed with a
dedicated script - it is not part of the main menu yet:

``bash
sudo bash hysteria2-setup.sh
``

Interactive prompts let you set the port, auto-generates a strong auth password,
optionally enables Salamander obfuscation, creates a self-signed certificate,
installs the systemd service, opens the firewall, and prints a ready-to-import
hysteria2:// link (plus QR if qrencode is installed) for the TZVPN app.

Non-interactive example:

``bash
sudo bash hysteria2-setup.sh --port 443 --obfs on
``