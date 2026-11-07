package io.github.sahilmakkar.chronos

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.sahilmakkar.chronos.engine.TaskCoordinator
import io.github.sahilmakkar.chronos.port.ConstraintVerifier
import io.github.sahilmakkar.chronos.port.NetworkChecker
import io.github.sahilmakkar.chronos.port.TaskExecutor
import io.github.sahilmakkar.chronos.scheduler.ChronosTaskCanceller
import io.github.sahilmakkar.chronos.scheduler.ChronosTaskEnqueuer
import io.github.sahilmakkar.chronos.scheduler.WakeScheduler
import io.github.sahilmakkar.chronos.storage.ChronosDatabase
import io.github.sahilmakkar.chronos.storage.TaskEntity
import io.github.sahilmakkar.chronos.task.TaskChain
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.constraints.EnvironmentConstraints
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.policy.DependentTaskPolicy
import io.github.sahilmakkar.chronos.task.policy.UniqueTaskPolicy
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.task.request.TaskRequest
import io.github.sahilmakkar.chronos.worker.TaskWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

import io.github.sahilmakkar.chronos.testing.FakeClock

internal class FakeTaskExecutor : TaskExecutor {
    val executed: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
    private val activeCount = java.util.concurrent.atomic.AtomicInteger(0)
    val peakConcurrency = java.util.concurrent.atomic.AtomicInteger(0)
    var script: suspend (TaskEntity) -> TaskResult = { TaskResult.Success() }

    override suspend fun execute(entity: TaskEntity, inputData: TaskData): TaskResult {
        val current = activeCount.incrementAndGet()
        peakConcurrency.updateAndGet { maxOf(it, current) }
        try {
            executed += entity.id
            return script(entity)
        } finally {
            activeCount.decrementAndGet()
        }
    }
}

internal class FakeConstraintVerifier(var satisfied: Boolean = true) : ConstraintVerifier {
    var checkCount = 0
    override fun isSatisfied(entity: TaskEntity): Boolean {
        checkCount++
        return satisfied
    }
}

internal class FakeNetworkChecker(
    var connected: Boolean = true,
    var unmetered: Boolean = true
) : NetworkChecker {
    override fun isConnected(): Boolean = connected
    override fun isUnmetered(): Boolean = unmetered
}

internal class FakeWakeScheduler(
    private val db: ChronosDatabase,
    private val clock: Clock = Clock.System
) : WakeScheduler {
    var scheduledDelayMs: Long? = null
    var scheduledCount = 0
    var cancelled = false

    override suspend fun scheduleNextWake() {
        val states = listOf(io.github.sahilmakkar.chronos.task.TaskState.ENQUEUED, io.github.sahilmakkar.chronos.task.TaskState.RETRYING)
        val next = db.taskDao().getNextDueTask(states)
        if (next == null) {
            cancelWake()
            return
        }
        val nowMs = clock.now().toEpochMilliseconds()
        scheduledDelayMs = maxOf(0L, next.scheduledAt - nowMs)
        cancelled = false
        scheduledCount++
    }

    override fun cancelWake() {
        scheduledDelayMs = null
        cancelled = true
    }
}

internal class DummyWorker(inputData: TaskData) : TaskWorker(inputData) {
    override suspend fun doWork(): TaskResult = TaskResult.Success()
}

internal fun createTestDatabase(
    tempFile: File = File.createTempFile("chronos_test_", ".db"),
    queryCoroutineContext: CoroutineContext = Dispatchers.IO
): ChronosDatabase {
    tempFile.deleteOnExit()
    return Room.databaseBuilder<ChronosDatabase>(
        name = tempFile.absolutePath
    ).setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(queryCoroutineContext)
        .build()
}

internal class TestHarness(
    val clock: FakeClock = FakeClock(1000L),
    val executor: FakeTaskExecutor = FakeTaskExecutor(),
    val constraintVerifier: FakeConstraintVerifier = FakeConstraintVerifier(),
    val networkChecker: FakeNetworkChecker = FakeNetworkChecker(),
    val queryCoroutineContext: CoroutineContext = Dispatchers.IO,
    wakeSchedulerFactory: ((ChronosDatabase) -> WakeScheduler)? = null
) {
    val tempFile: File = File.createTempFile("chronos_harness_", ".db").apply { deleteOnExit() }
    val db: ChronosDatabase = createTestDatabase(tempFile, queryCoroutineContext)
    val wakeScheduler: WakeScheduler? = wakeSchedulerFactory?.invoke(db)
    val coordinator: TaskCoordinator = TaskCoordinator(
        db = db,
        taskExecutor = executor,
        constraintProvider = constraintVerifier,
        clock = clock,
        wakeScheduler = wakeScheduler
    )
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val cancellerScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val enqueuer: ChronosTaskEnqueuer = ChronosTaskEnqueuer(
        db = db,
        coroutineScope = scope,
        coordinator = coordinator,
        clock = clock,
        wakeScheduler = wakeScheduler
    )
    val canceller: ChronosTaskCanceller = ChronosTaskCanceller(
        db = db,
        coroutineScope = cancellerScope,
        wakeScheduler = wakeScheduler
    )

    fun request(
        workerClass: KClass<out TaskWorker> = DummyWorker::class,
        initialDelay: Duration = Duration.ZERO,
        tags: Set<String> = emptySet(),
        constraints: EnvironmentConstraints = EnvironmentConstraints()
    ): OneTimeTaskRequest {
        val b = OneTimeTaskRequest.Builder(workerClass)
            .setInitialDelay(initialDelay)
            .setConstraints(constraints)
        tags.forEach { b.addTag(it) }
        return b.build()
    }

    suspend fun enqueue(request: TaskRequest) {
        enqueuer.enqueue(request)
        scope.coroutineContext[Job]?.children?.toList()?.forEach { it.join() }
    }

    suspend fun enqueueUniqueTask(name: String, policy: UniqueTaskPolicy, request: OneTimeTaskRequest) {
        enqueuer.enqueueUniqueTask(name, policy, request)
        scope.coroutineContext[Job]?.children?.toList()?.forEach { it.join() }
    }

    suspend fun enqueueUniquePeriodicTask(name: String, policy: UniqueTaskPolicy, request: io.github.sahilmakkar.chronos.task.request.PeriodicTaskRequest) {
        enqueuer.enqueueUniquePeriodicTask(name, policy, request)
        scope.coroutineContext[Job]?.children?.toList()?.forEach { it.join() }
    }

    suspend fun enqueueChain(
        first: TaskRequest,
        vararg rest: TaskRequest,
        failurePolicy: DependentTaskPolicy = DependentTaskPolicy.STOP_ON_FAILURE
    ) {
        var chain = TaskChain.start(first)
        for (r in rest) {
            chain = chain.then(r, failurePolicy)
        }
        enqueuer.enqueue(chain)
        scope.coroutineContext[Job]?.children?.toList()?.forEach { it.join() }
    }

    suspend fun cancelTaskById(id: String) {
        canceller.cancelTaskById(id)
        cancellerScope.coroutineContext[Job]?.children?.toList()?.forEach { it.join() }
    }

    suspend fun cancelTaskByTag(tag: String) {
        canceller.cancelTaskByTag(tag)
        cancellerScope.coroutineContext[Job]?.children?.toList()?.forEach { it.join() }
    }

    suspend fun cancelAllTasks() {
        canceller.cancelAllTasks()
        cancellerScope.coroutineContext[Job]?.children?.toList()?.forEach { it.join() }
    }

    suspend fun runPendingTasks() {
        scope.coroutineContext[Job]?.children?.toList()?.forEach { it.join() }
        coordinator.runPendingTasks()
    }

    suspend fun close() {
        scope.coroutineContext[Job]?.children?.toList()?.forEach { it.join() }
        cancellerScope.coroutineContext[Job]?.children?.toList()?.forEach { it.join() }
        scope.cancel()
        cancellerScope.cancel()
        db.close()
        tempFile.delete()
        File("${tempFile.absolutePath}-wal").delete()
        File("${tempFile.absolutePath}-shm").delete()
    }
}
