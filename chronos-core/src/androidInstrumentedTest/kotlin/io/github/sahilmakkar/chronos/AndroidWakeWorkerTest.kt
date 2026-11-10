package io.github.sahilmakkar.chronos

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.policy.UniqueTaskPolicy
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.worker.TaskWorker
import io.github.sahilmakkar.chronos.worker.WorkerRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class AndroidTestWorker(inputData: TaskData) : TaskWorker(inputData) {
    override suspend fun doWork(): TaskResult = TaskResult.Success()
}

@RunWith(AndroidJUnit4::class)
class AndroidWakeWorkerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @Test
    fun testWakeWorkerRunsDelayedTaskToSuccess() = runBlocking {
        val registry = WorkerRegistry().apply {
            register(AndroidTestWorker::class.qualifiedName!!) { AndroidTestWorker(it) }
        }
        val chronos = Chronos.initialize(PlatformSupport(context), registry)

        val request = OneTimeTaskRequest.Builder(AndroidTestWorker::class)
            .setInitialDelay(30.seconds)
            .build()

        chronos.enqueueTask("wake_worker_test", UniqueTaskPolicy.REPLACE, request)

        val initialInfo = chronos.observeTask(request.id).first { it != null }
        assertEquals(TaskState.ENQUEUED, initialInfo?.state)

        val workManager = WorkManager.getInstance(context)
        val testDriver = WorkManagerTestInitHelper.getTestDriver(context)!!
        val wakeWorks = workManager.getWorkInfosForUniqueWork("chronos_wake").get()
        assertEquals(1, wakeWorks.size)
        val wakeWorkId = wakeWorks.first().id

        testDriver.setInitialDelayMet(wakeWorkId)

        val finalInfo = chronos.observeTask(request.id).first { it != null && it.state == TaskState.SUCCEEDED }
        assertEquals(TaskState.SUCCEEDED, finalInfo?.state)

        chronos.close()
    }
}
