package io.github.sahilmakkar.chronos.task.policy

/**
 * Specifies the policy for calculating delays when retrying failed tasks.
 */
public enum class BackoffPolicy {
    /**
     * Linear delay increase: backoffDelay * attempt.
     */
    LINEAR,

    /**
     * Exponential delay increase: backoffDelay * 2^(attempt - 1).
     */
    EXPONENTIAL;

    internal fun calculateDelay(initialDelayMs: Long, attempt: Int): Long {
        if (attempt <= 0 || initialDelayMs <= 0) return 0L
        return when (this) {
            LINEAR -> {
                val mult = attempt.toLong()
                if (mult > Long.MAX_VALUE / initialDelayMs) Long.MAX_VALUE else initialDelayMs * mult
            }
            EXPONENTIAL -> {
                val shift = attempt - 1
                if (shift >= 62) {
                    Long.MAX_VALUE
                } else {
                    val multiplier = 1L shl shift
                    if (multiplier > Long.MAX_VALUE / initialDelayMs) {
                        Long.MAX_VALUE
                    } else {
                        initialDelayMs * multiplier
                    }
                }
            }
        }
    }
}
