package transport

import (
	"fmt"
	"path/filepath"

	"github.com/vpntz/vpn-tz/tzgate/internal/config"
)

// buildSlipstreamExecStart builds the ExecStart for slipstream-server.
func buildSlipstreamExecStart(tunnel *config.TunnelConfig, cfg *config.Config) (string, error) {
	if tunnel.TzKitonga == nil {
		return "", fmt.Errorf("slipstream config is nil")
	}

	backend := cfg.GetBackend(tunnel.Backend)
	if backend == nil {
		return "", fmt.Errorf("backend %q not found", tunnel.Backend)
	}

	binPath := filepath.Join(config.DefaultBinDir, "slipstream-server")

	return fmt.Sprintf("%s --dns-listen-host 127.0.0.1 --dns-listen-port %d --cert %s --key %s --domain %s --target-address %s",
		binPath,
		tunnel.Port,
		tunnel.TzKitonga.Cert,
		tunnel.TzKitonga.Key,
		tunnel.Domain,
		backend.Address,
	), nil
}
