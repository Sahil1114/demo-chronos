package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.constraints.IosConstraintVerifier
import io.github.sahilmakkar.chronos.port.NetworkChecker
import io.github.sahilmakkar.chronos.storage.TaskEntity
import io.github.sahilmakkar.chronos.storage.mapper.toEntity
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.constraints.EnvironmentConstraints
import io.github.sahilmakkar.chronos.task.constraints.NetworkType
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.testing.FakeClock
import io.github.sahilmakkar.chronos.worker.TaskWorker
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class AppleDummyWorker(taskData: TaskData) : TaskWorker(taskData) {
    override suspend fun doWork(): TaskResult = TaskResult.Success()
}

private class TestAppleNetworkChecker(
    var connected: Boolean = true,
    var unmetered: Boolean = true
) : NetworkChecker {
    override fun isConnected(): Boolean = connected
    override fun isUnmetered(): Boolean = unmetered
}

class IosAppleTest {

    private fun createEntity(
        networkType: NetworkType = NetworkType.NOT_REQUIRED,
        requiresCharging: Boolean = false,
        requiresBatteryNotLow: Boolean = false,
        requiresStorageNotLow: Boolean = false
    ): TaskEntity {
        val constraints = EnvironmentConstraints(
            requiredNetworkType = networkType,
            requiresCharging = requiresCharging,
            requiresBatteryNotLow = requiresBatteryNotLow,
            requiresStorageNotLow = requiresStorageNotLow
        )
        return OneTimeTaskRequest.Builder(AppleDummyWorker::class)
            .setConstraints(constraints)
            .build()
            .toEntity(scheduledAt = 0L)
    }

    @Test
    fun testIosConstraintVerifierNetworkConstraints() {
        val clock = FakeClock(1000L)
        val networkChecker = TestAppleNetworkChecker(connected = false)
        val verifier = IosConstraintVerifier(networkChecker, clock)

        val unconstrained = createEntity()
        assertTrue(verifier.isSatisfied(unconstrained))

        val networkRequired = createEntity(networkType = NetworkType.CONNECTED)
        assertFalse(verifier.isSatisfied(networkRequired))

        networkChecker.connected = true
        assertTrue(verifier.isSatisfied(networkRequired))
    }

    @Test
    fun testIosConstraintVerifierUnmeteredNetwork() {
        val clock = FakeClock(1000L)
        val networkChecker = TestAppleNetworkChecker(connected = true, unmetered = false)
        val verifier = IosConstraintVerifier(networkChecker, clock)

        val unmeteredRequired = createEntity(networkType = NetworkType.UNMETERED)
        assertFalse(verifier.isSatisfied(unmeteredRequired))

        networkChecker.unmetered = true
        assertTrue(verifier.isSatisfied(unmeteredRequired))
    }
}
