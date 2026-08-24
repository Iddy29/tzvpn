//go:build tools

// Package gomobilebuild exists only to keep the gomobile-bound modules in
// go.mod (gomobile bind resolves packages through this module's require/replace
// graph; `go mod tidy` would otherwise drop modules that no Go source imports).
package gomobilebuild

import (
	_ "hysteria2-mobile/hysteria2"
	_ "noizdns/mobile"
	_ "snowflake-mobile/snowflake"
	_ "vaydns-mobile/vaydns"
	_ "vlessreality-mobile/vlessreality"
)
