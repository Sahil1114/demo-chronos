package io.github.sahilmakkar.chronos.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.model.TaskInfo

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
                Text(
                    "Progress: $progress",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
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