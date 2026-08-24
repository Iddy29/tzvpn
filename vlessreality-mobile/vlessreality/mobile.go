// Package vlessreality provides a gomobile-compatible API for VLESS over
// REALITY (raw TCP transport) — the standard REALITY setup.
//
// It runs a local SOCKS5 server; each connection is tunneled through a
// REALITY-authenticated TLS session carrying the VLESS protocol:
//
//	App -> hev-socks5-tunnel -> local SOCKS5 -> REALITY (TLS1.3 + uTLS fingerprint)
//	     -> VLESS -> Server -> Internet
package vlessreality

import (
	"bufio"
	"context"
	"encoding/base64"
	"encoding/hex"
	"fmt"
	"io"
	"log"
	"net"
	"sync"

	"vlessreality-mobile/reality"
)

// Client wraps a VLESS+REALITY dialer with a Start/Stop lifecycle and a
// local SOCKS5 listener.
type Client struct {
	listenAddr  string
	serverAddr  string
	uuid        string
	sni         string
	publicKey   string // base64url (or std) raw 32-byte x25519 public key
	shortID     string // hex, 0-16 hex chars
	fingerprint string

	mu      sync.Mutex
	running bool
	cancel  context.CancelFunc
	ln      net.Listener
}

// NewClient creates a VLESS+REALITY client bound to listenAddr.
//
// serverAddr is "host:port" of the REALITY server.
// uuid is the VLESS user id.
// sni is the serverName the REALITY server expects.
// publicKey is the server's REALITY public key (base64url raw 32 bytes).
// shortID is the server's shortId (hex string, may be empty).
// fingerprint is a uTLS fingerprint name ("chrome", "firefox", ...); empty = chrome.
func NewClient(listenAddr, serverAddr, uuid, sni, publicKey, shortID, fingerprint string) (*Client, error) {
	if listenAddr == "" {
		return nil, fmt.Errorf("listen address is required")
	}
	if serverAddr == "" {
		return nil, fmt.Errorf("server address is required")
	}
	if uuid == "" {
		return nil, fmt.Errorf("uuid is required")
	}
	if publicKey == "" {
		return nil, fmt.Errorf("publicKey is required")
	}
	if fingerprint == "" {
		fingerprint = "chrome"
	}
	return &Client{
		listenAddr:  listenAddr,
		serverAddr:  serverAddr,
		uuid:        uuid,
		sni:         sni,
		publicKey:   publicKey,
		shortID:     shortID,
		fingerprint: fingerprint,
	}, nil
}

// Start begins accepting SOCKS5 connections.
func (c *Client) Start() error {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.running {
		return fmt.Errorf("client is already running")
	}

	ctx, cancel := context.WithCancel(context.Background())
	ln, err := net.Listen("tcp", c.listenAddr)
	if err != nil {
		cancel()
		return fmt.Errorf("opening listener on %s: %v", c.listenAddr, err)
	}
	c.cancel = cancel
	c.ln = ln
	c.running = true

	go func() {
		<-ctx.Done()
		ln.Close()
	}()
	go func() {
		defer func() {
			c.mu.Lock()
			c.running = false
			c.mu.Unlock()
		}()
		for {
			conn, err := ln.Accept()
			if err != nil {
				if ctx.Err() != nil {
					return
				}
				log.Printf("vlessreality accept: %v", err)
				continue
			}
			go func() {
				defer conn.Close()
				if err := c.handle(conn); err != nil {
					log.Printf("vlessreality handle: %v", err)
				}
			}()
		}
	}()
	return nil
}

// parseUUIDBytes converts a canonical UUID string to 16 bytes.
func parseUUIDBytes(id string) ([]byte, error) {
	h := ""
	for _, r := range id {
		if r != '-' {
			h += string(r)
		}
	}
	if len(h) != 32 {
		return nil, fmt.Errorf("invalid uuid %q", id)
	}
	return hex.DecodeString(h)
}

// handle performs the SOCKS5 handshake and relays through VLESS+REALITY.
func (c *Client) handle(local net.Conn) error {
	br := bufio.NewReader(local)
	buf := make([]byte, 258)

	n, err := br.Read(buf)
	if err != nil || n < 2 || buf[0] != 0x05 {
		return fmt.Errorf("socks5 greeting")
	}
	local.Write([]byte{0x05, 0x00})

	n, err = br.Read(buf)
	if err != nil || n < 7 || buf[0] != 0x05 || buf[1] != 0x01 {
		local.Write([]byte{0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0})
		return fmt.Errorf("socks5 unsupported request")
	}

	atyp := buf[3]
	var destHost string
	var addrEnd int
	switch atyp {
	case 0x01:
		if n < 10 {
			return fmt.Errorf("socks5 short ipv4")
		}
		destHost = net.IP(buf[4:8]).String()
		addrEnd = 8
	case 0x03:
		domainLen := int(buf[4])
		if n < 5+domainLen+2 {
			return fmt.Errorf("socks5 short domain")
		}
		destHost = string(buf[5 : 5+domainLen])
		addrEnd = 5 + domainLen
	case 0x04:
		if n < 22 {
			return fmt.Errorf("socks5 short ipv6")
		}
		destHost = net.IP(buf[4:20]).String()
		addrEnd = 20
	default:
		return fmt.Errorf("socks5 bad atyp %d", atyp)
	}
	destPort := int(buf[addrEnd])<<8 | int(buf[addrEnd+1])

	local.Write([]byte{0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0})

	uuidBytes, err := parseUUIDBytes(c.uuid)
	if err != nil {
		return err
	}
	pub, err := base64.RawURLEncoding.DecodeString(c.publicKey)
	if err != nil {
		pub, err = base64.StdEncoding.DecodeString(c.publicKey)
	}
	if err != nil || len(pub) != 32 {
		return fmt.Errorf("invalid REALITY public key")
	}
	sid, err := hex.DecodeString(c.shortID)
	if err != nil {
		return fmt.Errorf("invalid REALITY shortId")
	}

	cfg := &reality.Config{
		ServerName:  c.sni,
		PublicKey:   pub,
		ShortID:     sid,
		Fingerprint: c.fingerprint,
	}
	remote, err := reality.Dial(context.Background(), c.serverAddr, cfg)
	if err != nil {
		return fmt.Errorf("reality dial %s: %v", c.serverAddr, err)
	}
	defer remote.Close()

	// VLESS request header: version + uuid + addons_len(0) + cmd tcp + port + addr
	hdr := buildVlessHeader(uuidBytes, destHost, destPort)
	if _, err := remote.Write(hdr); err != nil {
		return fmt.Errorf("vless write: %v", err)
	}

	// VLESS response: version(1) + addons_len(1) + [addons]
	h := make([]byte, 2)
	if _, err := io.ReadFull(br, h); err != nil {
		return fmt.Errorf("vless response: %v", err)
	}
	if addons := int(h[1]); addons > 0 {
		if _, err := io.CopyN(io.Discard, br, int64(addons)); err != nil {
			return fmt.Errorf("vless addons: %v", err)
		}
	}

	var wg sync.WaitGroup
	wg.Add(2)
	go func() {
		defer wg.Done()
		io.Copy(remote, local)
	}()
	go func() {
		defer wg.Done()
		io.Copy(local, br)
	}()
	wg.Wait()
	return nil
}

// buildVlessHeader constructs the VLESS request header.
func buildVlessHeader(uuid []byte, host string, port int) []byte {
	buf := make([]byte, 0, 64)
	buf = append(buf, 0x00)    // version
	buf = append(buf, uuid...) // 16 bytes
	buf = append(buf, 0x00)    // addons length
	buf = append(buf, 0x01)    // command TCP
	buf = append(buf, byte(port>>8), byte(port&0xFF))

	if ip := net.ParseIP(host); ip != nil {
		if ip4 := ip.To4(); ip4 != nil {
			buf = append(buf, 0x01)
			buf = append(buf, ip4...)
		} else {
			buf = append(buf, 0x03)
			buf = append(buf, ip.To16()...)
		}
	} else {
		buf = append(buf, 0x02)
		buf = append(buf, byte(len(host)))
		buf = append(buf, []byte(host)...)
	}
	return buf
}

// Stop shuts the tunnel down.
func (c *Client) Stop() {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.cancel != nil {
		c.cancel()
		c.cancel = nil
	}
	if c.ln != nil {
		c.ln.Close()
		c.ln = nil
	}
	c.running = false
}

// IsRunning reports whether the client is currently running.
func (c *Client) IsRunning() bool {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.running
}
