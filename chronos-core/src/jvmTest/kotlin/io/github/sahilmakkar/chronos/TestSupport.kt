package io.github.sahilmakkar.chronos

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.sahilmakkar.chronos.engine.TaskCoordinator
import io.github.sahilmakkar.chronos.port.ConstraintVerifier
import io.github.sahilmakkar.chronos.port.NetworkChecker
import io.github.sahilmakkar.chronos.port.TaskExecutor
import io.github.sahilmakkar.chronos.scheduler.ChronosTaskCanceller
import io.github.sahilmakkar.chronos.scheduler.ChronosTaskEnqueuer
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
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

class FakeClock(var nowMs: Long = 1000L) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(nowMs)
    fun advance(ms: Long) { nowMs += ms }
}

class FakeTaskExecutor : TaskExecutor {
    val executed = mutableListOf<String>()
    var script: (TaskEntity) -> TaskResult = { TaskResult.Success() }
    override suspend fun execute(entity: TaskEntity, inputData: TaskData): TaskResult {
        executed += entity.id
        return script(entity)
    }
}

class FakeConstraintVerifier(var satisfied: Boolean = true) : ConstraintVerifier {
    override fun isSatisfied(entity: TaskEntity): Boolean = satisfied
}

class FakeNetworkChecker(
    var connected: Boolean = true,
    var unmetered: Boolean = true
) : NetworkChecker {
    override fun isConnected(): Boolean = connected
    override fun isUnmetered(): Boolean = unmetered
}

class DummyWorker(inputData: TaskData) : TaskWorker(inputData) {
    override suspend fun doWork(): TaskResult = TaskResult.Success()
}

fun createTestDatabase(tempFile: File = File.createTempFile("chronos_test_", ".db")): ChronosDatabase {
    tempFile.deleteOnExit()
    return Room.databaseBuilder<ChronosDatabase>(
        name = tempFile.absolutePath
    ).setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}

internal class TestHarness(
    val clock: FakeClock = FakeClock(1000L),
    val executor: FakeTaskExecutor = FakeTaskExecutor(),
    val constraintVerifier: FakeConstraintVerifier = FakeConstraintVerifier(),
    val networkChecker: FakeNetworkChecker = FakeNetworkChecker()
) {
    val tempFile: File = File.createTempFile("chronos_harness_", ".db").apply { deleteOnExit() }
    val db: ChronosDatabase = createTestDatabase(tempFile)
    val coordinator: TaskCoordinator = TaskCoordinator(
        db = db,
        taskExecutor = executor,
        constraintProvider = constraintVerifier,
        clock = clock
    )
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val cancellerScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val enqueuer: ChronosTaskEnqueuer = ChronosTaskEnqueuer(
        db = db,
        coroutineScope = scope,
        coordinator = coordinator,
        clock = clock
    )
    val canceller: ChronosTaskCanceller = ChronosTaskCanceller(
        db = db,
        coroutineScope = cancellerScope
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
