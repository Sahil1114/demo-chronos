package io.github.sahilmakkar.chronos.scheduler

import io.github.sahilmakkar.chronos.storage.ChronosDatabase
import io.github.sahilmakkar.chronos.task.TaskState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.BackgroundTasks.BGAppRefreshTask
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.dateWithTimeIntervalSinceNow
import kotlin.concurrent.Volatile
import kotlin.time.Clock

/**
 * Apple/iOS [WakeScheduler] implementation backed by `BGTaskScheduler`.
 *
 * Mirrors the Android [AndroidWakeScheduler] contract:
 * 1. Queries the database for the earliest task in `ENQUEUED` or `RETRYING` state.
 * 2. If none remain, cancels the pending `BGAppRefreshTaskRequest`.
 * 3. Otherwise submits a single [BGAppRefreshTaskRequest]. If the task is due in the future, its
 *    `earliestBeginDate` is that delay from now. If it is already due (it was not run, for example
 *    because constraints or parent tasks are not satisfied), the wake backs off by [RETRY_WAKE_SEC]
 *    so a blocked task cannot cause back-to-back wake requests.
 *    Submitting under the same identifier replaces the previous request, so at most one wake is
 *    ever pending.
 *
 * Platform limits:
 * - `earliestBeginDate` is only a lower bound; iOS decides the actual launch time and gives no
 *   timing guarantees.
 * - `BGAppRefreshTaskRequest` carries no network/charging constraints, so task constraints are
 *   enforced in-process by `IosConstraintVerifier` after the wake fires.
 *
 * Registration:
 * - iOS allows exactly one launch handler per identifier and raises an exception if the identifier
 *   is registered twice, registered after launch has finished, or missing from
 *   `BGTaskSchedulerPermittedIdentifiers`. [register] therefore registers the handler once per
 *   process, skips registration with a log line when the identifier is not permitted, and routes
 *   every wake to the most recently registered scheduler so Chronos can be re-initialized.
 *
 * @param db Chronos database used to find the next due task.
 * @param coroutineScope Scope in which wake passes run.
 * @param onWake Runs all eligible tasks when iOS wakes the process.
 * @param onExpired Invoked if iOS cuts background runtime short (orphan sweep).
 * @param clock Time source.
 */
internal class IosWakeScheduler(
    private val db: ChronosDatabase,
    private val coroutineScope: CoroutineScope,
    private val onWake: suspend () -> Unit,
    private val onExpired: suspend () -> Unit,
    private val clock: Clock = Clock.System,
) : WakeScheduler {
    /**
     * Registers the launch handler with [BGTaskScheduler] and makes this instance the wake target.
     *
     * Must run before the end of `application(_:didFinishLaunchingWithOptions:)`. Safe to call for
     * several instances in one process; only the first call installs the iOS handler.
     */
    fun register() {
        if (!hasBundle()) return
        if (!isIdentifierPermitted()) {
            println(
                "Chronos: $BG_TASK_ID is not listed under BGTaskSchedulerPermittedIdentifiers in Info.plist; " +
                    "background wakes are disabled.",
            )
            return
        }
        active = this
        if (handlerRegistered) return
        handlerRegistered = true
        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
            identifier = BG_TASK_ID,
            usingQueue = null,
        ) { task ->
            (task as? BGAppRefreshTask)?.let { refreshTask ->
                val target = active
                if (target == null) {
                    refreshTask.setTaskCompletedWithSuccess(false)
                } else {
                    target.handleWake(task = refreshTask)
                }
            }
        }
    }

    override suspend fun scheduleNextWake() {
        if (!hasBundle()) return
        val nextTask =
            db.taskDao().getNextDueTask(
                states = listOf(TaskState.ENQUEUED, TaskState.RETRYING),
            )

        if (nextTask == null) {
            cancelWake()
            return
        }
        val dueInMs = nextTask.scheduledAt - clock.now().toEpochMilliseconds()
        val delaySec = if (dueInMs > 0) dueInMs / 1000.0 else RETRY_WAKE_SEC
        submit(delaySec = delaySec)
    }

    override fun cancelWake() {
        if (!hasBundle()) return
        BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(identifier = BG_TASK_ID)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun submit(delaySec: Double) {
        val request = BGAppRefreshTaskRequest(identifier = BG_TASK_ID)
        request.earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(secs = delaySec)
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val submitted =
                BGTaskScheduler.sharedScheduler.submitTaskRequest(
                    taskRequest = request,
                    error = error.ptr,
                )
            if (!submitted) {
                println(
                    "Chronos: failed to submit background wake for $BG_TASK_ID " +
                        "(code ${error.value?.code}): ${error.value?.localizedDescription}",
                )
            }
        }
    }

    /**
     * Runs a wake pass, then always re-syncs the next wake from the database so retries and
     * periodic tasks keep getting a wake, whether the pass succeeded, failed or was expired.
     *
     * - A failure inside [onWake] is logged and reported to iOS as an unsuccessful run; it never
     *   escapes the coroutine, so it cannot terminate the app through an unhandled exception.
     * - If iOS expires the task, the handler only cancels the job; the job then runs its cleanup
     *   (orphan sweep and wake re-sync) under [NonCancellable] before it completes.
     * - The BG task is completed exactly once, from [kotlinx.coroutines.Job.invokeOnCompletion],
     *   after the job has finished, so iOS is never told the task is done while cleanup is still
     *   running. This also covers a job that never starts because the scope was cancelled.
     */
    private fun handleWake(task: BGAppRefreshTask) {
        var succeeded = false
        val job =
            coroutineScope.launch {
                try {
                    onWake()
                    succeeded = true
                } catch (e: CancellationException) {
                    withContext(NonCancellable) { onExpired() }
                    throw e
                } catch (e: Throwable) {
                    println("Chronos: background wake pass failed: ${e.message}")
                } finally {
                    withContext(NonCancellable) {
                        try {
                            scheduleNextWake()
                        } catch (e: Throwable) {
                            println("Chronos: failed to reschedule background wake: ${e.message}")
                        }
                    }
                }
            }
        task.expirationHandler = { job.cancel() }
        job.invokeOnCompletion { cause ->
            task.setTaskCompletedWithSuccess(cause == null && succeeded)
        }
    }

    private fun hasBundle() = NSBundle.mainBundle.bundleIdentifier != null

    private fun isIdentifierPermitted(): Boolean {
        val permitted =
            NSBundle.mainBundle.objectForInfoDictionaryKey(key = "BGTaskSchedulerPermittedIdentifiers") as? List<*>
        return permitted?.contains(BG_TASK_ID) == true
    }

    companion object {
        const val BG_TASK_ID = "io.github.sahilmakkar.chronos.bg_task"
        private const val RETRY_WAKE_SEC = 15 * 60.0

        @Volatile
        private var handlerRegistered = false

        @Volatile
        private var active: IosWakeScheduler? = null
    }
}
