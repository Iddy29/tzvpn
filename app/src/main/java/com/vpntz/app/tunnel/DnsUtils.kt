package com.vpntz.app.tunnel

// Delegating facade to the independent DNS codec in com.vpntz.app.network,
// preserving the legacy object name for the existing tunnel-package call sites.
object DnsUtils {
    fun isAAAAQuery(payload: ByteArray): Boolean =
        com.vpntz.app.network.DnsUtils.isAAAAQuery(payload)

    fun buildAAAANoDataResponse(query: ByteArray): ByteArray? =
        com.vpntz.app.network.DnsUtils.buildAAAANoDataResponse(query)
}
