package handlers

import (
	"fmt"

	"github.com/Iddy29/tzvpn/tree/main/cybergate/internal/actions"
	"github.com/Iddy29/tzvpn/tree/main/cybergate/internal/config"
	"github.com/Iddy29/tzvpn/tree/main/cybergate/internal/router"
)

func handleRouterSwitch(ctx *actions.Context) error {
	cfg := ctx.Config.(*config.Config)
	tag := ctx.GetArg("tag")

	if tag == "" {
		return actions.NewError(actions.RouterSwitch, "tunnel tag is required", nil)
	}

	tunnel := cfg.GetTunnel(tag)
	if tunnel == nil {
		return actions.NewError(actions.RouterSwitch, fmt.Sprintf("tunnel %q not found", tag), nil)
	}

	if cfg.Route.Mode != "single" {
		return actions.NewErrorWithHint(actions.RouterSwitch, "switch only works in single mode",
			"Run 'cybergate router mode single' first", nil)
	}

	if err := router.SwitchActive(cfg, tag); err != nil {
		return actions.NewError(actions.RouterSwitch, "failed to switch tunnel", err)
	}

	cfg.Route.Active = tag
	if err := cfg.Save(); err != nil {
		return actions.NewError(actions.RouterSwitch, "failed to save config", err)
	}

	ctx.Output.Success(fmt.Sprintf("Switched active tunnel to %q", tag))
	return nil
}
