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
import io.github.sahilmakkar.chronos.Chronos
import io.github.sahilmakkar.chronos.beginWith
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import kotlin.time.Duration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(chronos: Chronos) {
    val tasks by chronos.observeTasksByTag("sample-task")
        .collectAsState(initial = emptyList())

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

                Spacer(modifier = Modifier.height(16.dp))

                // Actions section
                Text(
                    text = "Enqueue Tasks",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val req =
                                OneTimeTaskRequest.Builder(workerClass = SampleSuccessWorker::class)
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
                            val req =
                                OneTimeTaskRequest.Builder(workerClass = SampleFailureWorker::class)
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
                            val req =
                                OneTimeTaskRequest.Builder(workerClass = SampleRetryWorker::class)
                                    .setBackoffCriteria(
                                        policy = BackoffPolicy.LINEAR,
                                        initialDelay = Duration.parse("3s"),
                                        maxRetries = 3
                                    )
                                    .addTag(tag = "sample-task")
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
                            val req1 =
                                OneTimeTaskRequest.Builder(workerClass = SampleSuccessWorker::class)
                                    .addTag(tag = "sample-task")
                                    .build()
                            val req2 =
                                OneTimeTaskRequest.Builder(workerClass = SampleSuccessWorker::class)
                                    .addTag(tag = "sample-task")
                                    .build()
                            // TaskChain has no enqueue() of its own -- the chain goes through
                            // chronos.enqueue(chain), same as everywhere else it's used.
                            chronos.enqueue(
                                chain = chronos.beginWith(request = req1).then(request = req2)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Success Chain")
                    }
                    Button(
                        onClick = {
                            chronos.cancelAllTasks()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373))
                    ) {
                        Text("Cancel All")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Task List section
                Text(
                    text = "Scheduled Tasks (${tasks.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (tasks.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "No tasks enqueued", color = Color.Gray)
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