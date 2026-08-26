// Package hysteria2 provides a gomobile-compatible API for the Hysteria2
// QUIC-based proxy protocol with optional Salamander obfuscation.
//
// It runs a local SOCKS5 server that tunnels each connection through
// Hysteria2 TCP relay:
//
//	App -> hev-socks5-tunnel -> local SOCKS5 -> QUIC/Hysteria2 -> Server -> Internet
package hysteria2

import (
	"bufio"
	"context"
	"fmt"
	"io"
	"log"
	"net"
	"strconv"
	"sync"

	hyclient "github.com/apernet/hysteria/core/v2/client"
	hyobfs "github.com/apernet/hysteria/extras/v2/obfs"
)

// Client wraps a Hysteria2 client with Start/Stop lifecycle and a local SOCKS5 listener.
type Client struct {
	listenAddr   string
	serverAddr   string
	auth         string
	sni          string
	obfs         string
	obfsPassword string
	insecure     bool

	mu      sync.Mutex
	running bool
	cancel  context.CancelFunc
	ln      net.Listener
	hy      hyclient.Client
}

// NewClient creates a Hysteria2 client bound to listenAddr.
//
// serverAddr is "host:port" of the Hysteria2 server.
// auth is the auth password.
// sni is the TLS ServerName (empty = use server host).
// obfs is "" or "salamander"; obfsPassword is its PSK (required for salamander).
// insecure skips TLS certificate verification.
func NewClient(listenAddr, serverAddr, auth, sni, obfs, obfsPassword string, insecure bool) (*Client, error) {
	if listenAddr == "" {
		return nil, fmt.Errorf("listen address is required")
	}
	if serverAddr == "" {
		return nil, fmt.Errorf("server address is required")
	}
	if auth == "" {
		return nil, fmt.Errorf("auth password is required")
	}
	return &Client{
		listenAddr:   listenAddr,
		serverAddr:   serverAddr,
		auth:         auth,
		sni:          sni,
		obfs:         obfs,
		obfsPassword: obfsPassword,
		insecure:     insecure,
	}, nil
}

// hy2ConnFactory produces UDP packet conns wrapped with Salamander obfuscation.
type hy2ConnFactory struct{ PSK []byte }

func (f *hy2ConnFactory) New(addr net.Addr) (net.PacketConn, error) {
	pc, err := net.ListenUDP("udp", nil)
	if err != nil {
		return nil, err
	}
	ob, err := hyobfs.NewSalamanderObfuscator(f.PSK)
	if err != nil {
		return nil, err
	}
	return hyobfs.WrapPacketConn(pc, ob), nil
}

// Start connects to the server and begins accepting SOCKS5 connections.
func (c *Client) Start() error {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.running {
		return fmt.Errorf("client is already running")
	}

	udpAddr, err := net.ResolveUDPAddr("udp", c.serverAddr)
	if err != nil {
		return fmt.Errorf("resolving server %s: %v", c.serverAddr, err)
	}

	cfg := &hyclient.Config{
		ServerAddr: udpAddr,
		Auth:       c.auth,
		TLSConfig: hyclient.TLSConfig{
			ServerName:         c.sni,
			InsecureSkipVerify: c.insecure,
		},
	}
	if c.obfs == "salamander" {
		if c.obfsPassword == "" {
			return fmt.Errorf("obfs-password is required when obfs=salamander")
		}
		cfg.ConnFactory = &hy2ConnFactory{PSK: []byte(c.obfsPassword)}
	}

	hy, _, err := hyclient.NewClient(cfg)
	if err != nil {
		return fmt.Errorf("hysteria2 connect: %v", err)
	}

	ctx, cancel := context.WithCancel(context.Background())
	ln, err := net.Listen("tcp", c.listenAddr)
	if err != nil {
		cancel()
		hy.Close()
		return fmt.Errorf("opening listener on %s: %v", c.listenAddr, err)
	}

	c.cancel = cancel
	c.ln = ln
	c.hy = hy
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
				log.Printf("hysteria2 accept: %v", err)
				continue
			}
			go func() {
				defer conn.Close()
				if err := c.handle(conn); err != nil {
					log.Printf("hysteria2 handle: %v", err)
				}
			}()
		}
	}()

	return nil
}

// handle performs the SOCKS5 handshake and relays through Hysteria2 TCP.
func (c *Client) handle(local net.Conn) error {
	br := bufio.NewReader(local)
	buf := make([]byte, 258)

	// Greeting
	n, err := br.Read(buf)
	if err != nil || n < 2 || buf[0] != 0x05 {
		return fmt.Errorf("socks5 greeting")
	}
	local.Write([]byte{0x05, 0x00})

	// Request (CONNECT only)
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
	destAddr := net.JoinHostPort(destHost, strconv.Itoa(destPort))

	local.Write([]byte{0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0})

	remote, err := c.hy.TCP(destAddr)
	if err != nil {
		return fmt.Errorf("hysteria2 tcp %s: %v", destAddr, err)
	}
	defer remote.Close()

	var wg sync.WaitGroup
	wg.Add(2)
	go func() {
		defer wg.Done()
		io.Copy(remote, local)
	}()
	go func() {
		defer wg.Done()
		io.Copy(local, remote)
	}()
	wg.Wait()
	return nil
}

// Stop shuts the tunnel down and releases all resources.
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
	if c.hy != nil {
		c.hy.Close()
		c.hy = nil
	}
	c.running = false
}

// IsRunning reports whether the client is currently running.
func (c *Client) IsRunning() bool {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.running
}
