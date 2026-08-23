package io.github.sahilmakkar.chronos.executor

import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.data.mapper.fromJson
import io.github.sahilmakkar.chronos.db.WorkDatabase
import io.github.sahilmakkar.chronos.db.WorkRequestEntity
import io.github.sahilmakkar.chronos.task.policy.DependentTaskPolicy
import io.github.sahilmakkar.chronos.utils.KmpClock

class WorkChainCoordinator(
    private val db: WorkDatabase,
    private val clock: KmpClock,
    private val constraintVerifier: WorkConstraintVerifier,
    private val taskExecutor: WorkTaskExecutor
) {

    suspend fun sweepOrphans() {
        db.workDao().getWorkRequestsByState("RUNNING").forEach {
            db.workDao().updateWorkState(it.id, "ENQUEUED")
        }
    }

    suspend fun sweepChainRecovery() {
        db.workDao().getAllDependencies().forEach { dep ->
            val parent = db.workDao().getWorkRequest(dep.parentWorkId) ?: return@forEach
            val child = db.workDao().getWorkRequest(dep.childWorkId) ?: return@forEach
            when {
                parent.state == "SUCCEEDED" && child.state == "ENQUEUED" ->
                    db.workDao().updateScheduledAt(child.id, clock.now().toEpochMilliseconds())

                parent.state == "FAILED" && child.state == "ENQUEUED" ->
                    if (dep.chainFailurePolicy == DependentTaskPolicy.STOP_ON_FAILURE) {
                        db.workDao().updateWorkState(child.id, "FAILED")
                    } else {
                        db.workDao().updateScheduledAt(child.id, clock.now().toEpochMilliseconds())
                    }
            }
        }
    }

    suspend fun runPendingTasks() {
        val nowMs = clock.now().toEpochMilliseconds()
        val pending = db.workDao().getWorkRequestsByState("ENQUEUED") +
                db.workDao().getWorkRequestsByState("RETRYING")
        for (entity in pending) {
            if (entity.scheduledAt > nowMs) continue
            if (!resolveDependencies(entity)) continue
            if (!constraintVerifier.isSatisfied(entity)) continue
            taskExecutor.execute(entity, TaskData.fromJson(entity.inputDataJson))
        }
    }

    private suspend fun resolveDependencies(entity: WorkRequestEntity): Boolean {
        val deps = db.workDao().getDependenciesForChild(entity.id)
        for (dep in deps) {
            val parent = db.workDao().getWorkRequest(dep.parentWorkId)
            if (parent == null || parent.state != "SUCCEEDED") {
                if (parent?.state == "FAILED" && dep.chainFailurePolicy == DependentTaskPolicy.STOP_ON_FAILURE) {
                    db.workDao().updateWorkState(entity.id, "FAILED")
                }
                return false
            }
        }
        return true
    }
}