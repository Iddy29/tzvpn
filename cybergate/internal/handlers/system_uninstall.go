package handlers

import (
	"os"

	"github.com/Iddy29/tzvpn/tree/main/cybergate/internal/actions"
	"github.com/Iddy29/tzvpn/tree/main/cybergate/internal/config"
	"github.com/Iddy29/tzvpn/tree/main/cybergate/internal/prompt"
	"github.com/Iddy29/tzvpn/tree/main/cybergate/internal/service"
	"github.com/Iddy29/tzvpn/tree/main/cybergate/internal/system"
	"github.com/Iddy29/tzvpn/tree/main/cybergate/internal/warp"
)

func handleSystemUninstall(ctx *actions.Context) error {
	out := ctx.Output

	ok, err := prompt.Confirm("This will remove ALL tunnels, services, configs, and the cybergate user. Continue?")
	if err != nil {
		return err
	}
	if !ok {
		out.Info("Cancelled")
		return nil
	}

	// Stop and remove ALL cybergate services (config + any orphaned ones)
	for _, svcName := range service.ListCybergateServices() {
		out.Info("Stopping " + svcName + "...")
		_ = service.Stop(svcName)
		_ = service.Remove(svcName)
	}

	// Also clean up legacy microsocks service
	_ = service.Stop("cybergate-microsocks")
	_ = service.Remove("cybergate-microsocks")

	// Clean up dnstm if present
	_, _ = offerDnstmCleanup(out, actions.SystemUninstall)

	// Stop WARP and remove its dedicated users
	out.Info("Removing WARP...")
	warp.Uninstall()
	warp.RemoveUsers()

	// Remove config directory
	out.Info("Removing /etc/cybergate/...")
	if err := os.RemoveAll(config.DefaultConfigDir); err != nil {
		out.Warning("Failed to remove config dir: " + err.Error())
	}

	// Remove system user
	out.Info("Removing system user...")
	if err := system.RemoveUser(); err != nil {
		out.Warning("Failed to remove user: " + err.Error())
	}

	// Remove binaries
	out.Info("Removing binaries...")
	execPath, _ := os.Executable()
	for _, bin := range []string{
		"dnstt-server", "slipstream-server", "vaydns-server", "caddy-naive", "microsocks",
	} {
		os.Remove(config.DefaultBinDir + "/" + bin)
	}

	// Remove cybergate binary last
	if execPath != "" {
		os.Remove(execPath)
	}

	out.Success("Uninstall complete")
	return nil
}
