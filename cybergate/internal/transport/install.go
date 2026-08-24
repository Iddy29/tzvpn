package transport

import (
	"github.com/Iddy29/tzvpn/tree/main/cybergate/internal/binary"
	"github.com/Iddy29/tzvpn/tree/main/cybergate/internal/config"
)

// EnsureInstalled downloads the binary for a transport if not already present.
func EnsureInstalled(transport string) error {
	bin, ok := config.TransportBinaries[transport]
	if !ok {
		return nil
	}
	return binary.EnsureInstalled(bin)
}
