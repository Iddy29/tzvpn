package com.vpntz.app.tunnel.adapter

import com.vpntz.app.domain.model.TunnelType

/** Static facts an upper layer may want to reason about without touching a protocol. */
data class TunnelAdapterSpec(
    /** The protocol enum value this spec describes. */
    val protocol: TunnelType,
    /**
     * True when the tuned engine is exposed through a native binary (gomobile AAR,
     * a `.so`, or Rust JNI). False for pure-JVM engines such as SSH (JSch).
     */
    val requiresNativeArtifact: Boolean,
    /** True when the protocol can be layered with an SSH hop (e.g. DNSTT+SSH). */
    val chainable: Boolean
)

/**
 * Maps a [TunnelType] to its [TunnelAdapter], and enumerates static per-protocol
 * facts. The adapter is bound to a [TunnelLifecycleBackend] that does the real
 * bridge/native work, so factory logic itself is pure and unit-testable.
 */
class TunnelAdapterFactory {

    /**
     * Bind [protocol] to [backend]. Throws [IllegalArgumentException] for types
     * that are not tunnel adapters (e.g. the standalone SOCKS5 proxy).
     */
    fun create(protocol: TunnelType, backend: TunnelLifecycleBackend): TunnelAdapter {
        if (protocol == TunnelType.SOCKS5) {
            throw IllegalArgumentException("SOCKS5 is a standalone proxy, not a tunnel adapter")
        }
        return BoundTunnelAdapter(protocol, backend)
    }

    /** Static descriptors for every protocol understood by the adapter layer. */
    fun specs(): List<TunnelAdapterSpec> = SPECS

    /** [specs] keyed by protocol. */
    fun spec(protocol: TunnelType): TunnelAdapterSpec? = SPECS_BY_TYPE[protocol]

    companion object {
        /**
         * whether/matrix facts derived from the audit (docs/provenance/PHASE5_AUDIT.md).
         * `chainable` = an SSH layer is meaningful on top of this protocol.
         */
        private val SPECS = listOf(
            TunnelAdapterSpec(TunnelType.DNSTT, requiresNativeArtifact = true, chainable = true),
            TunnelAdapterSpec(TunnelType.DNSTT_SSH, requiresNativeArtifact = true, chainable = false),
            TunnelAdapterSpec(TunnelType.NOIZDNS, requiresNativeArtifact = true, chainable = true),
            TunnelAdapterSpec(TunnelType.NOIZDNS_SSH, requiresNativeArtifact = true, chainable = false),
            TunnelAdapterSpec(TunnelType.VAYDNS, requiresNativeArtifact = true, chainable = true),
            TunnelAdapterSpec(TunnelType.VAYDNS_SSH, requiresNativeArtifact = true, chainable = false),
            TunnelAdapterSpec(TunnelType.SLIPSTREAM, requiresNativeArtifact = true, chainable = true),
            TunnelAdapterSpec(TunnelType.SLIPSTREAM_SSH, requiresNativeArtifact = true, chainable = false),
            TunnelAdapterSpec(TunnelType.NAIVE, requiresNativeArtifact = true, chainable = true),
            TunnelAdapterSpec(TunnelType.NAIVE_SSH, requiresNativeArtifact = true, chainable = false),
            TunnelAdapterSpec(TunnelType.VLESS, requiresNativeArtifact = true, chainable = false),
            TunnelAdapterSpec(TunnelType.SNOWFLAKE, requiresNativeArtifact = true, chainable = false),
            TunnelAdapterSpec(TunnelType.HYSTERIA2, requiresNativeArtifact = true, chainable = false),
            TunnelAdapterSpec(TunnelType.SSH, requiresNativeArtifact = false, chainable = true),
            TunnelAdapterSpec(TunnelType.DOH, requiresNativeArtifact = true, chainable = false)
        )

        private val SPECS_BY_TYPE = SPECS.associateBy { it.protocol }
    }
}

/**
 * Minimal [TunnelAdapter] that forwards the lifecycle contract to [backend]
 * and carries the protocol identity. Real per-protocol behavior lives in the
 * backend, so this class is deliberately thin.
 */
private class BoundTunnelAdapter(
    override val protocol: TunnelType,
    private val backend: TunnelLifecycleBackend
) : TunnelAdapter {

    override suspend fun start(config: TunnelAdapterConfig): Result<Unit> = backend.start(config)

    override fun stop() = backend.stop()

    override fun isRunning(): Boolean = backend.isRunning()

    override fun isHealthy(): Boolean = backend.isHealthy()

    override fun cleanup() = backend.cleanup()
}
