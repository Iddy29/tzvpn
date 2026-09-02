package com.vpntz.app.tunnel.adapter

import com.vpntz.app.domain.model.SshAuthType

/** SSH transport chosen from the profile's transport flags. */
enum class SshTransport {
    DIRECT,
    HTTP_PROXY,
    WEB_SOCKET
}

/**
 * Pure, JVM-testable translation of an [TunnelAdapterConfig.Ssh] into the
 * arguments the existing `SshTunnelBridge` (`SshTunnelInstance`, JSch) expects,
 * including which of the `startDirect`/`startOverHttpProxy`/`startOverWebSocket`
 * entry points to use. No credentials are logged here.
 */
object SshBridgeArgs {

    data class Resolved(
        val transport: SshTransport,
        val sshHost: String,
        val sshPort: Int,
        val sshUsername: String,
        val sshPassword: String,
        val listenPort: Int,
        val listenHost: String,
        val forwardDnsThroughSsh: Boolean,
        val sshAuthType: SshAuthType,
        val sshPrivateKey: String,
        val sshKeyPassphrase: String,
        val remoteDnsHost: String,
        val remoteDnsFallback: String,
        // HTTP CONNECT (transport = HTTP_PROXY)
        val proxyHost: String,
        val proxyPort: Int,
        val customHostHeader: String,
        // WebSocket (transport = WEB_SOCKET)
        val wsPath: String,
        val wsUseTls: Boolean,
        val wsCustomHost: String,
        val wsTlsSni: String,
        // TLS / payload (transport = DIRECT, over TLS/raw)
        val tlsEnabled: Boolean,
        val tlsSni: String,
        val sshPayload: String
    )

    fun transport(config: TunnelAdapterConfig.Ssh): SshTransport = when {
        config.wsEnabled -> SshTransport.WEB_SOCKET
        config.httpProxyHost.isNotBlank() -> SshTransport.HTTP_PROXY
        else -> SshTransport.DIRECT
    }

    fun resolve(config: TunnelAdapterConfig.Ssh): Resolved = Resolved(
        transport = transport(config),
        sshHost = config.host,
        sshPort = config.port,
        sshUsername = config.username,
        sshPassword = config.password,
        listenPort = config.listenPort,
        listenHost = config.listenHost,
        forwardDnsThroughSsh = config.forwardDnsThroughSsh,
        sshAuthType = runCatching { SshAuthType.valueOf(config.authType) }
            .getOrDefault(SshAuthType.PASSWORD),
        sshPrivateKey = config.privateKey,
        sshKeyPassphrase = config.keyPassphrase,
        remoteDnsHost = config.remoteDnsHost,
        remoteDnsFallback = config.remoteDnsFallback,
        proxyHost = config.httpProxyHost,
        proxyPort = config.httpProxyPort,
        customHostHeader = config.tlsSni,
        wsPath = config.wsPath,
        wsUseTls = config.wsUseTls,
        wsCustomHost = config.wsCustomHost,
        wsTlsSni = config.wsTlsSni,
        tlsEnabled = config.tlsEnabled,
        tlsSni = config.tlsSni,
        sshPayload = config.payload
    )
}
