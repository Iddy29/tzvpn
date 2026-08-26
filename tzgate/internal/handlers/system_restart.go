package handlers

import (
	"fmt"

	"github.com/vpntz/vpn-tz/tzgate/internal/actions"
	"github.com/vpntz/vpn-tz/tzgate/internal/config"
	"github.com/vpntz/vpn-tz/tzgate/internal/network"
	"github.com/vpntz/vpn-tz/tzgate/internal/proxy"
	"github.com/vpntz/vpn-tz/tzgate/internal/service"
	"github.com/vpntz/vpn-tz/tzgate/internal/warp"
)

func handleSystemRestart(ctx *actions.Context) error {
	out := ctx.Output
	cfg := ctx.Config.(*config.Config)

	out.Info("Restarting all services...")

	// Ensure port 53 is available
	for _, t := range cfg.Tunnels {
		if t.IsDNSTunnel() {
			if err := network.DisableResolvedStub(); err != nil {
				out.Warning("Failed to disable systemd-resolved stub: " + err.Error())
			}
			break
		}
	}

	// 1. Restart SOCKS5 backend first (tunnels forward to it)
	if service.Exists("tzgate-socks5") {
		// Check if the service user matches the expected WARP state.
		// If not, recreate the service file to fix it.
		expectedUser := config.SystemUser
		if cfg.Warp.Enabled {
			expectedUser = warp.SocksUser
		}
		if service.GetUser("tzgate-socks5") != expectedUser {
			if cfg.Warp.Enabled {
				proxy.RunAsUser = warp.SocksUser
			} else {
				proxy.RunAsUser = ""
			}
			directSOCKS := false
			for _, t := range cfg.Tunnels {
				if t.Transport == config.TransportSOCKS {
					directSOCKS = true
				}
			}
			var socksErr error
			if directSOCKS {
				if len(cfg.Users) > 0 {
					socksErr = proxy.SetupSOCKSExternalWithUsers(cfg.Users)
				} else {
					socksErr = proxy.SetupSOCKS()
				}
			} else if len(cfg.Users) > 0 {
				socksErr = proxy.SetupSOCKSWithUsers(cfg.Users)
			} else {
				socksErr = proxy.SetupSOCKS()
			}
			if socksErr != nil {
				out.Warning(fmt.Sprintf("Failed to restart tzgate-socks5: %v", socksErr))
			} else {
				out.Success("  tzgate-socks5 restarted (fixed service user)")
			}
		} else {
			if err := service.Restart("tzgate-socks5"); err != nil {
				out.Warning(fmt.Sprintf("Failed to restart tzgate-socks5: %v", err))
			} else {
				out.Success("  tzgate-socks5 restarted")
			}
		}
	}

	// 2. Restart tunnel services (connect clients to backends)
	for _, t := range cfg.Tunnels {
		if t.IsDirectTransport() {
			continue
		}
		svcName := service.TunnelServiceName(t.Tag)
		if service.Exists(svcName) {
			if err := service.Restart(svcName); err != nil {
				out.Warning(fmt.Sprintf("Failed to restart %s: %v", svcName, err))
			} else {
				out.Success(fmt.Sprintf("  %s restarted", svcName))
			}
		}
	}

	// 3. Restart DNS router last (routes to tunnels, needs them up)
	if service.Exists("tzgate-dnsrouter") {
		if err := service.Restart("tzgate-dnsrouter"); err != nil {
			out.Warning(fmt.Sprintf("Failed to restart tzgate-dnsrouter: %v", err))
		} else {
			out.Success("  tzgate-dnsrouter restarted")
		}
	}

	out.Print("")
	out.Success("All services restarted")
	return nil
}
