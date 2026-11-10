package io.github.sahilmakkar.chronos.task.policy

/**
 * Algorithm used to compute delay duration before a failed task is scheduled for a retry attempt.
 *
 * Retry delays are calculated dynamically based on the current completed attempt count `n` (where `n >= 1`)
 * and the configured base duration [io.github.sahilmakkar.chronos.task.request.TaskRequest.backoffDelay].
 *
 * @see io.github.sahilmakkar.chronos.task.request.TaskRequestBuilder.setBackoffCriteria
 */
public enum class BackoffPolicy {
    /**
     * Delay scales linearly with the attempt number:
     * ```
     * delay = backoffDelay * attempt
     * ```
     *
     * Example with a 10s base delay:
     * - Attempt 1: 10s
     * - Attempt 2: 20s
     * - Attempt 3: 30s
     */
    LINEAR,

    /**
     * Delay scales exponentially with the attempt number:
     * ```
     * delay = backoffDelay * 2^(attempt - 1)
     * ```
     *
     * Example with a 10s base delay:
     * - Attempt 1: 10s
     * - Attempt 2: 20s
     * - Attempt 3: 40s
     * - Attempt 4: 80s
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
