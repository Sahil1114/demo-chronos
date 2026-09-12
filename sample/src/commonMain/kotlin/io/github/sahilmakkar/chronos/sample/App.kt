package io.github.sahilmakkar.chronos.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sahilmakkar.chronos.*
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.TaskInfo
import io.github.sahilmakkar.chronos.task.model.workDataOf
import io.github.sahilmakkar.chronos.worker.Worker
import io.github.sahilmakkar.chronos.worker.WorkerRegistry
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.TaskState
import kotlinx.coroutines.delay
import kotlin.time.Duration

class SampleSuccessWorker(inputData: TaskData) : Worker(inputData) {
    override suspend fun doWork(): TaskResult {
        setProgress(workDataOf("status" to "Working: step 1/2"))
        delay(1000)
        setProgress(workDataOf("status" to "Working: step 2/2"))
        delay(1000)
        return TaskResult.Success(workDataOf("message" to "Completed successfully!"))
    }
}

class SampleFailureWorker(inputData: TaskData) : Worker(inputData) {
    override suspend fun doWork(): TaskResult {
        setProgress(workDataOf("status" to "Processing..."))
        delay(1500)
        return TaskResult.Failure(workDataOf("error" to "Simulated task failure"))
    }
}

class SampleRetryWorker(inputData: TaskData) : Worker(inputData) {
    override suspend fun doWork(): TaskResult {
        setProgress(workDataOf("status" to "Attempting run..."))
        delay(1000)
        return TaskResult.Retry
    }
}

fun createSampleRegistry(): WorkerRegistry {
    return WorkerRegistry().apply {
        register("io.github.sahilmakkar.chronos.sample.SampleSuccessWorker") { SampleSuccessWorker(it) }
        register("io.github.sahilmakkar.chronos.sample.SampleFailureWorker") { SampleFailureWorker(it) }
        register("io.github.sahilmakkar.chronos.sample.SampleRetryWorker") { SampleRetryWorker(it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(chronos: Chronos) {
    val coroutineScope = rememberCoroutineScope()
    val tasks by chronos.getWorkInfosByTagFlow("sample-task")
        .collectAsState(initial = emptyList())
    val capabilities = remember { chronos.platformCapabilities() }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF81C784),
            secondary = Color(0xFF64B5F6),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            onBackground = Color(0xFFE0E0E0),
            onSurface = Color(0xFFE0E0E0)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Chronos KMP Scheduler", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp)
            ) {
                // Platform Capabilities section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Platform Capabilities", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            CapabilityItem("Periodic", capabilities.periodicTasksGuaranteed)
                            CapabilityItem("Precise Delay", capabilities.initialDelayPrecise)
                            CapabilityItem("BG Reliable", capabilities.backgroundExecutionReliable)
                            CapabilityItem("Native Network", capabilities.networkConstraintNative)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions section
                Text("Enqueue Tasks", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val req = OneTimeTaskRequest.Builder(SampleSuccessWorker::class)
                                .addTag("sample-task")
                                .build()
                            chronos.enqueue(req)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Success")
                    }
                    Button(
                        onClick = {
                            val req = OneTimeTaskRequest.Builder(SampleFailureWorker::class)
                                .addTag("sample-task")
                                .build()
                            chronos.enqueue(req)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Failure")
                    }
                    Button(
                        onClick = {
                            val req = OneTimeTaskRequest.Builder(SampleRetryWorker::class)
                                .setBackoffCriteria(BackoffPolicy.LINEAR, Duration.parse("3s"), 3)
                                .addTag("sample-task")
                                .build()
                            chronos.enqueue(req)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Retry")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val req1 = OneTimeTaskRequest.Builder(SampleSuccessWorker::class).addTag("sample-task").build()
                            val req2 = OneTimeTaskRequest.Builder(SampleSuccessWorker::class).addTag("sample-task").build()
                            chronos.beginWith(req1).then(req2).enqueue()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Success Chain")
                    }
                    Button(
                        onClick = {
                            chronos.cancelAllWork()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373))
                    ) {
                        Text("Cancel All")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Task List section
                Text("Scheduled Tasks (${tasks.size})", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(8.dp))

                if (tasks.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No tasks enqueued", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(tasks) { info ->
                            TaskCard(info)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CapabilityItem(label: String, supported: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(if (supported) "Yes" else "No", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (supported) Color(0xFF81C784) else Color(0xFFE57373))
    }
}

@Composable
fun TaskCard(info: TaskInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ID: ${info.id.take(8)}...",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                StateChip(info.state)
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            Text("Attempts: ${info.runAttemptCount}", fontSize = 12.sp, color = Color.Gray)
            
            val progress = info.progress.getString("status")
            if (!progress.isNullOrEmpty()) {
                Text("Progress: $progress", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            }

            val msg = info.outputData.getString("message")
            val err = info.outputData.getString("error")
            if (!msg.isNullOrEmpty()) {
                Text("Output: $msg", fontSize = 12.sp, color = Color(0xFF81C784))
            } else if (!err.isNullOrEmpty()) {
                Text("Error: $err", fontSize = 12.sp, color = Color(0xFFE57373))
            }
        }
    }
}

@Composable
fun StateChip(state: TaskState) {
    val color = when (state) {
        TaskState.ENQUEUED -> Color.Gray
        TaskState.RUNNING -> Color(0xFF64B5F6)
        TaskState.SUCCEEDED -> Color(0xFF81C784)
        TaskState.FAILED -> Color(0xFFE57373)
        TaskState.RETRYING -> Color(0xFFFFB74D)
        TaskState.CANCELLED -> Color(0xFFE0E0E0)
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(state.name, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
