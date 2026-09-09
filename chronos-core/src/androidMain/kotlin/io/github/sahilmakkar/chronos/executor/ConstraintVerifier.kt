package io.github.sahilmakkar.chronos.executor

import io.github.sahilmakkar.chronos.db.WorkRequestEntity
import io.github.sahilmakkar.chronos.db.executor.WorkConstraintVerifier

class WorkConstraintVerifier : WorkConstraintVerifier {
    /**
     * Always satisfied — constraint enforcement on Android is delegated to
     * WorkManager itself. [AndroidTaskExecutor.toWorkManagerConstraints] attaches
     * the same network/charging/battery/storage/idle constraints directly to the
     * WorkRequest, and WorkManager (via JobScheduler) holds the job until the OS
     * confirms they're met — it re-checks continuously, not just once at enqueue.
     * Re-verifying here would be redundant and could only ever be less accurate
     * than the OS's own check.
     */
    override fun isSatisfied(entity: WorkRequestEntity): Boolean = true
}