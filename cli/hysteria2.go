package main

// Hysteria2 tunnel support for the TZVPN CLI.
//
// Uses the official Hysteria2 core client library (apernet/hysteria core/v2)
// with optional Salamander obfuscation, exposed as a local SOCKS5 proxy:
//
//	SOCKS5 -> QUIC (Hysteria2, optional Salamander obfs) -> Server -> Internet

import (
	"bufio"
	"context"
	"crypto/tls"
	"encoding/binary"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/signal"
	"strconv"
	"strings"
	"sync"
	"syscall"
	"time"

	hyclient "github.com/apernet/hysteria/core/v2/client"
	hyobfs "github.com/apernet/hysteria/extras/v2/obfs"
)

// parseHysteria2URI parses a standard hysteria2:// URI into a Profile.
// Format: hysteria2://password@host:port?sni=domain&insecure=1&obfs=salamander&obfs-password=xxx#name
func parseHysteria2URI(uri string) (*Profile, error) {
	u, err := url.Parse(uri)
	if err != nil {
		return nil, fmt.Errorf("invalid Hysteria2 URI: %v", err)
	}

	host := u.Hostname()
	if host == "" {
		return nil, fmt.Errorf("invalid Hysteria2 URI: missing server host")
	}
	port := 443
	if ps := u.Port(); ps != "" {
		if v, err := strconv.Atoi(ps); err == nil && v > 0 && v < 65536 {
			port = v
		}
	}
	password := ""
	if u.User != nil {
		password = u.User.Username()
	}

	name := "Hysteria2"
	if frag := u.Fragment; frag != "" {
		if dec, err := url.QueryUnescape(frag); err == nil {
			name = dec
		} else {
			name = frag
		}
	}

	q := u.Query()
	sni := q.Get("sni")
	insecure := false
	switch strings.ToLower(q.Get("insecure")) {
	case "1", "true", "yes":
		insecure = true
	}
	obfs := q.Get("obfs")
	obfsPassword := q.Get("obfs-password")
	if obfs != "" && obfs != "salamander" && obfs != "none" {
		return nil, fmt.Errorf("unsupported Hysteria2 obfs %q (only salamander)", obfs)
	}
	if obfs == "none" {
		obfs = ""
	}

	return &Profile{
		TunnelType:      "hysteria2",
		Name:            name,
		Domain:          host,
		Host:            "127.0.0.1",
		Port:            10880,
		Hy2Auth:         password,
		Hy2ServerPort:   port,
		Hy2Sni:          sni,
		Hy2Insecure:     insecure,
		Hy2Obfs:         obfs,
		Hy2ObfsPass:     obfsPassword,
	}, nil
}

// hy2ObfsConnFactory produces UDP packet conns wrapped with Salamander obfuscation.
type hy2ObfsConnFactory struct {
	PSK []byte
}

func (f *hy2ObfsConnFactory) New(addr net.Addr) (net.PacketConn, error) {
	pc, err := net.ListenUDP("udp", nil)
	if err != nil {
		return nil, err
	}
	return hyobfs.WrapPacketConnSalamander(pc, f.PSK)
}

// connectHysteria2 starts a local SOCKS5 proxy tunneling through Hysteria2.
func connectHysteria2(profile *Profile) {
	listenAddr := fmt.Sprintf("%s:%d", profile.Host, profile.Port)
	serverAddr := fmt.Sprintf("%s:%d", profile.Domain, profile.Hy2ServerPort)

	fmt.Println()
	fmt.Println("╔══════════════════════════════════════════════════╗")
	fmt.Printf("║          TZVPN CLI  %-25s  ║\n", version)
	fmt.Println("╚══════════════════════════════════════════════════╝")
	fmt.Println()
	fmt.Printf("  Profile:    %s\n", profile.Name)
	fmt.Printf("  Type:       Hysteria2 (QUIC)\n")
	fmt.Printf("  Server:     %s\n", serverAddr)
	if profile.Hy2Sni != "" {
		fmt.Printf("  SNI:        %s\n", profile.Hy2Sni)
	}
	if profile.Hy2Insecure {
		fmt.Printf("  TLS:        insecure (skip verify)\n")
	}
	if profile.Hy2Obfs == "salamander" {
		fmt.Printf("  Obfs:       salamander\n")
	}
	fmt.Printf("  SOCKS5:     %s\n", listenAddr)
	fmt.Println()

	if profile.Domain == "" {
		fmt.Println("  Error: server host is required")
		return
	}
	if profile.Hy2Auth == "" {
		fmt.Println("  Error: auth password is required")
		return
	}

	udpAddr, err := net.ResolveUDPAddr("udp", serverAddr)
	if err != nil {
		fmt.Fprintf(os.Stderr, "  Error: cannot resolve server %s: %v\n", serverAddr, err)
		return
	}

	cfg := &hyclient.Config{
		ServerAddr: udpAddr,
		Auth:       profile.Hy2Auth,
		TLSConfig: hyclient.TLSConfig{
			ServerName:         profile.Hy2Sni,
			InsecureSkipVerify: profile.Hy2Insecure,
		},
	}
	if profile.Hy2Obfs == "salamander" {
		if profile.Hy2ObfsPass == "" {
			fmt.Println("  Error: obfs-password is required when obfs=salamander")
			return
		}
		cfg.ConnFactory = &hy2ObfsConnFactory{PSK: []byte(profile.Hy2ObfsPass)}
	}

	fmt.Println("  Connecting to Hysteria2 server...")

	client, _, err := hyclient.NewClient(cfg)
	if err != nil {
		fmt.Fprintf(os.Stderr, "  Error: Hysteria2 connect failed: %v\n", err)
		return
	}
	defer client.Close()

	fmt.Printf("  Connected! SOCKS5 proxy listening on %s\n", listenAddr)
	fmt.Println()
	fmt.Printf("  Or: curl --socks5-hostname %s https://ifconfig.me\n", listenAddr)
	fmt.Println()
	fmt.Println("  Press Ctrl+C to disconnect.")

	ln, err := net.Listen("tcp", listenAddr)
	if err != nil {
		fmt.Fprintf(os.Stderr, "  Error: %v\n", err)
		return
	}
	go func() {
		for {
			conn, err := ln.Accept()
			if err != nil {
				return
			}
			go handleHy2Socks5(conn, client)
		}
	}()

	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	<-sigCh
	fmt.Println("\n  Disconnecting...")
	ln.Close()
	client.Close()
	fmt.Println("  Done.")
}

// handleHy2Socks5 handles a single SOCKS5 connection and relays it over Hysteria2 TCP.
func handleHy2Socks5(client net.Conn, hy hyclient.Client) {
	defer client.Close()

	br := bufio.NewReader(client)

	// Greeting
	buf := make([]byte, 258)
	n, err := br.Read(buf)
	if err != nil || n < 2 || buf[0] != 0x05 {
		return
	}
	client.Write([]byte{0x05, 0x00}) // no auth

	// Request
	n, err = br.Read(buf)
	if err != nil || n < 7 || buf[0] != 0x05 || buf[1] != 0x01 {
		client.Write([]byte{0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0})
		return
	}

	atyp := buf[3]
	var destHost string
	var addrEnd int
	switch atyp {
	case 0x01: // IPv4
		if n < 10 {
			return
		}
		destHost = fmt.Sprintf("%d.%d.%d.%d", buf[4], buf[5], buf[6], buf[7])
		addrEnd = 8
	case 0x03: // Domain
		domainLen := int(buf[4])
		if n < 5+domainLen+2 {
			return
		}
		destHost = string(buf[5 : 5+domainLen])
		addrEnd = 5 + domainLen
	case 0x04: // IPv6
		if n < 22 {
			return
		}
		destHost = net.IP(buf[4:20]).String()
		addrEnd = 20
	default:
		return
	}
	destPort := int(buf[addrEnd])<<8 | int(buf[addrEnd+1])
	destAddr := net.JoinHostPort(destHost, strconv.Itoa(destPort))

	client.Write([]byte{0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0}) // success

	remote, err := hy.TCP(destAddr)
	if err != nil {
		fmt.Fprintf(os.Stderr, "  Hysteria2 TCP %s failed: %v\n", destAddr, err)
		return
	}
	defer remote.Close()

	// Bidirectional relay
	var wg sync.WaitGroup
	wg.Add(2)
	go func() {
		defer wg.Done()
		io.Copy(remote, client)
	}()
	go func() {
		defer wg.Done()
		io.Copy(client, remote)
	}()
	wg.Wait()
}

var _ = tls.VersionTLS13
var _ = binary.BigEndian
var _ = http.MethodPost
var _ = time.Second
var _ = context.Background
