package com.vpntz.app.tunnel

/**
 * Bandwidth pacer based on the Generic Cell Rate Algorithm (GCRA), a leaky-bucket
 * scheduler. VPN-TZ original implementation.
 *
 * Instead of accumulating tokens, GCRA tracks a single [tat] (Theoretical Arrival
 * Time) deadline. Each admitted burst of [byteCount] bytes advances the deadline
 * proportionally to burst size / rate. A burst is admitted immediately as long as
 * the deadline has not drifted more than one scheduling quantum into the future;
 * otherwise the caller sleeps until the bucket has drained enough.
 *
 * Compared to a token bucket this needs no refill math per call, handles bursts
 * up to one second of rate (matching the previous burst ceiling), and recovers
 * smoothly when the rate limit is changed mid-flight.
 *
 * Thread-safe: multiple copy loops may share one instance.
 *
 * @param bytesPerSecond Maximum sustained throughput in bytes/sec. 0 = unlimited.
 */
class RateLimiter(bytesPerSecond: Long) {

    @Volatile
    var bytesPerSecond: Long = bytesPerSecond
        set(value) {
            synchronized(lock) {
                field = value
                // Treat the bucket as fully drained after a rate change so the
                // new limit takes effect on the very next acquire.
                primed = true
                tatNanos = System.nanoTime()
            }
        }

    private val lock = Any()

    /** Theoretical arrival time — the instant the bucket is fully drained. */
    private var tatNanos: Long = 0L
    private var primed = false

    /**
     * Blocks until [byteCount] bytes are permitted. Returns immediately when the
     * rate is unlimited (0) or the request is empty.
     */
    fun acquire(byteCount: Int) {
        var rate = bytesPerSecond
        if (rate <= 0 || byteCount <= 0) return

        while (true) {
            val waitMs: Long
            synchronized(lock) {
                rate = bytesPerSecond
                if (rate <= 0) return
                val now = System.nanoTime()
                val costNanos = byteCount.toLong() * 1_000_000_000L / rate

                if (!primed) {
                    primed = true
                    tatNanos = now + costNanos
                    return
                }

                // Bursts within one scheduling window of the deadline pass free.
                val earliest = tatNanos - BURST_TOLERANCE_NANOS
                if (now >= earliest) {
                    tatNanos = maxOf(now, tatNanos) + costNanos
                    return
                }
                waitMs = (earliest - now + 999_999L) / 1_000_000L
            }
            try {
                Thread.sleep(waitMs.coerceIn(1, MAX_SLEEP_MS))
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
            // Loop: re-evaluate with a fresh clock reading (rate may have changed).
        }
    }

    companion object {
        /**
         * Scheduling tolerance — a fixed one-second window. Bursts arriving
         * less than this far behind schedule pass without delay, capping the
         * burst size at (rate x 1s) bytes independently of the current rate.
         */
        private const val BURST_TOLERANCE_NANOS = 1_000_000_000L

        /** Upper bound for a single sleep so rate changes are honored promptly. */
        private const val MAX_SLEEP_MS = 250L
    }
}
