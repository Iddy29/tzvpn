package com.vpntz.app.tunnel

import java.io.InputStream

// Delegating facade to the independent protocol sniffer in com.vpntz.app.network,
// preserving the legacy object name for the existing tunnel-package call sites.
object ProtocolSniffer {
    fun sniff(clientInput: InputStream, timeoutMs: Int = 3000): com.vpntz.app.network.ProtocolSniffer.SniffResult =
        com.vpntz.app.network.ProtocolSniffer.sniff(clientInput, timeoutMs)
}
