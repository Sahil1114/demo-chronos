package io.github.sahilmakkar.chronos.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun CapabilityItem(label: String, supported: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(
            if (supported) "Yes" else "No",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (supported) Color(0xFF81C784) else Color(0xFFE57373)
        )
    }
}