package io.github.sahilmakkar.chronos.task.policy

/**
 * Specifies the policy for calculating delays when retrying failed tasks.
 */
enum class BackoffPolicy {
    /**
     * Linear delay increase: backoffDelay * attempt.
     */
    LINEAR,

    /**
     * Exponential delay increase: backoffDelay * 2^(attempt - 1).
     */
    EXPONENTIAL
}