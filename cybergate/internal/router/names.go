package router

import (
	"github.com/Iddy29/tzvpn/tree/main/cybergate/internal/config"
	"github.com/Iddy29/tzvpn/tree/main/cybergate/internal/service"
)

// ServiceName returns the systemd service name for a tunnel.
func ServiceName(tag string) string {
	return service.TunnelServiceName(tag)
}

// AllocatePort assigns the next available port for a DNS tunnel.
func AllocatePort(cfg *config.Config) int {
	return cfg.NextAvailablePort()
}
