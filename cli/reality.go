package main

// Minimal REALITY client (XTLS REALITY protocol).
//
// Vendored and adapted from Xray-core's transport/internet/reality client
// (https://github.com/XTLS/Xray-core) so the VPN-TZ CLI can speak VLESS over
// REALITY without importing the whole Xray-core module.
//
// Simplifications vs. the Xray implementation:
//   - No ML-DSA-65 post-quantum certificate verification (optional feature).
//   - No "spider" fallback mode: when the server rejects us and returns a
//     real certificate (MITM / redirection / wrong key), we fail with a
//     clear error instead of crawling the decoy site.

import (
	"bytes"
	"context"
	cryptoecdh "crypto/ecdh"
	"crypto/ed25519"
	"crypto/aes"
	"crypto/cipher"
	"crypto/hmac"
	"crypto/sha256"
	"crypto/sha512"
	"crypto/x509"
	"encoding/binary"
	"fmt"
	"net"
	"time"

	utls "github.com/refraction-networking/utls"
	"golang.org/x/crypto/hkdf"
)

// realityConfig holds the REALITY client parameters for one server.
type realityConfig struct {
	ServerName  string // SNI the server expects (must be in server's serverNames list)
	PublicKey   []byte // raw 32-byte x25519 public key (from server's private key)
	ShortID     []byte // 0-8 bytes hex shortId configured on the server
	Fingerprint string // uTLS fingerprint name ("chrome", "firefox", ...)
}

// realityUConn is a utls connection with REALITY handshake + verification.
type realityUConn struct {
	*utls.UConn
	config     *realityConfig
	serverName string
	authKey    []byte
	verified   bool
}

var realityFingerprints = map[string]utls.ClientHelloID{
	"chrome":  utls.HelloChrome_Auto,
	"firefox": utls.HelloFirefox_Auto,
	"safari":  utls.HelloSafari_Auto,
	"ios":     utls.HelloIOS_Auto,
	"edge":    utls.HelloEdge_Auto,
	"android": utls.HelloAndroid_11_OkHttp,
}

// realityDial connects to addr over TCP and performs the REALITY handshake.
func realityDial(ctx context.Context, addr string, cfg *realityConfig) (net.Conn, error) {
	if cfg.ServerName == "" {
		return nil, fmt.Errorf("REALITY: serverName (SNI) is required")
	}
	if len(cfg.PublicKey) != 32 {
		return nil, fmt.Errorf("REALITY: public key must be 32 raw bytes (base64url), got %d", len(cfg.PublicKey))
	}
	if len(cfg.ShortID) > 8 {
		return nil, fmt.Errorf("REALITY: shortId must be at most 8 bytes")
	}

	d := net.Dialer{}
	raw, err := d.DialContext(ctx, "tcp", addr)
	if err != nil {
		return nil, fmt.Errorf("REALITY: TCP dial %s: %w", addr, err)
	}
	if t, ok := raw.(*net.TCPConn); ok {
		t.SetNoDelay(true)
	}

	conn, err := realityClient(raw, cfg)
	if err != nil {
		raw.Close()
		return nil, err
	}
	return conn, nil
}

// realityClient performs the REALITY handshake over an established TCP conn.
func realityClient(raw net.Conn, cfg *realityConfig) (net.Conn, error) {
	fpID, ok := realityFingerprints[cfg.Fingerprint]
	if !ok {
		fpID = utls.HelloChrome_Auto
	}

	u := &realityUConn{config: cfg, serverName: cfg.ServerName}
	utlsConfig := &utls.Config{
		ServerName:             cfg.ServerName,
		InsecureSkipVerify:     true, // REALITY does its own verification below
		SessionTicketsDisabled: true,
		VerifyPeerCertificate:  u.verifyPeerCertificate,
	}
	u.UConn = utls.UClient(raw, utlsConfig, fpID)

	// Build the ClientHello and inject the REALITY auth payload into the
	// session ID field (fixed offset 39 in the raw handshake bytes).
	u.BuildHandshakeState()
	hello := u.HandshakeState.Hello
	hello.SessionId = make([]byte, 32)
	copy(hello.Raw[39:], hello.SessionId)
	// [0:4] client version marker (zeros), [4:8] unix timestamp, [8:] shortId
	binary.BigEndian.PutUint32(hello.SessionId[4:], uint32(time.Now().Unix()))
	copy(hello.SessionId[8:], cfg.ShortID)

	publicKey, err := cryptoecdh.X25519().NewPublicKey(cfg.PublicKey)
	if err != nil {
		return nil, fmt.Errorf("REALITY: bad public key: %w", err)
	}
	ecdhe := u.HandshakeState.State13.KeyShareKeys.Ecdhe
	if ecdhe == nil {
		ecdhe = u.HandshakeState.State13.KeyShareKeys.MlkemEcdhe
	}
	if ecdhe == nil {
		return nil, fmt.Errorf("REALITY: fingerprint %q has no TLS 1.3 key share; handshake cannot establish", cfg.Fingerprint)
	}
	u.authKey, err = ecdhe.ECDH(publicKey)
	if err != nil || u.authKey == nil {
		return nil, fmt.Errorf("REALITY: ECDH with server public key failed: %v", err)
	}
	// Derive the temporary auth key:
	// authKey = HKDF-SHA256(shared, salt=hello.random[:20], info="REALITY")
	if _, err := hkdf.New(sha256.New, u.authKey, hello.Random[:20], []byte("REALITY")).Read(u.authKey); err != nil {
		return nil, fmt.Errorf("REALITY: hkdf: %w", err)
	}
	// Seal the first 16 bytes of the session ID with AES-256-GCM using the
	// auth key; nonce = hello.random[20:32], AAD = raw ClientHello.
	block, err := aes.NewCipher(u.authKey)
	if err != nil {
		return nil, err
	}
	aead, err := cipher.NewGCM(block)
	if err != nil {
		return nil, err
	}
	aead.Seal(hello.SessionId[:0], hello.Random[20:32], hello.SessionId[:16], hello.Raw)
	copy(hello.Raw[39:], hello.SessionId)

	if err := u.HandshakeContext(context.Background()); err != nil {
		return nil, fmt.Errorf("REALITY: handshake failed: %w", err)
	}
	if !u.verified {
		return nil, fmt.Errorf("REALITY: received a real certificate (wrong publicKey/shortId, MITM, or redirection)")
	}
	return u, nil
}

// verifyPeerCertificate distinguishes the REALITY temporary certificate
// (signed with HMAC-SHA512(authKey, ed25519 pubkey)) from a real one.
func (u *realityUConn) verifyPeerCertificate(rawCerts [][]byte, _ [][]*x509.Certificate) error {
	if len(rawCerts) == 0 {
		return fmt.Errorf("REALITY: no certificate presented")
	}
	cert, err := x509.ParseCertificate(rawCerts[0])
	if err != nil {
		return fmt.Errorf("REALITY: bad certificate: %w", err)
	}
	if pub, ok := cert.PublicKey.(ed25519.PublicKey); ok {
		h := hmac.New(sha512.New, u.authKey)
		h.Write(pub)
		if bytes.Equal(h.Sum(nil), cert.Signature) {
			u.verified = true
			return nil
		}
	}
	return fmt.Errorf("REALITY: server returned a real certificate (not REALITY-authenticated)")
}
