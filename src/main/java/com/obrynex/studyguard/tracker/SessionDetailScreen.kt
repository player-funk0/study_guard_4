package com.obrynex.studyguard.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obrynex.studyguard.R
import com.obrynex.studyguard.data.StudySession
import com.obrynex.studyguard.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun SessionDetailScreen(
    session: StudySession,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // ── Header ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = TextPrimary)
            }
            Text(
                text = stringResource(R.string.session_details),
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = AccentRed)
            }
        }

        // Gradient divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Divider, AccentGreen.copy(alpha = 0.3f), Divider, Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Status badge ────────────────────────────────────────
            val isCompleted = session.completed
            val statusColor = if (isCompleted) AccentGreen else AccentAmber
            val statusIcon = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.Warning
            val statusText = if (isCompleted) stringResource(R.string.completed) else stringResource(R.string.stopped_early)

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(0.5.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .background(statusColor.copy(alpha = 0.08f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(16.dp))
                Text(statusText, color = statusColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }

            // ── Detail card ─────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(0.5.dp, Divider, RoundedCornerShape(16.dp))
                    .background(Surface2)
            ) {
                // Card header with gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(statusColor.copy(alpha = 0.08f), Color.Transparent)
                            )
                        )
                        .padding(16.dp)
                ) {
                    Text(
                        text = session.subject.ifBlank { stringResource(R.string.general_study) },
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.3).sp
                    )
                }

                HorizontalDivider(color = Divider, thickness = 0.5.dp)

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    InfoRow(
                        icon = Icons.Default.Schedule,
                        label = "Duration",
                        value = stringResource(R.string.duration_min, session.durationMinutes),
                        accentColor = AccentGreen
                    )
                    InfoRow(
                        icon = Icons.Default.CalendarToday,
                        label = "Date",
                        value = dateFormat.format(session.date),
                        accentColor = AccentBlue
                    )
                    InfoRow(
                        icon = Icons.Default.PauseCircle,
                        label = "Paused",
                        value = formatPaused(session.pausedTimeMs),
                        accentColor = AccentAmber
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // ── Delete button ───────────────────────────────────────
            OutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentRed.copy(alpha = 0.3f))
            ) {
                Icon(Icons.Default.Delete, null, tint = AccentRed, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.delete_session),
                    color = AccentRed,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    stringResource(R.string.delete_session_question),
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = { Text(stringResource(R.string.delete_cannot_undo), color = TextMuted) },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteDialog = false },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.delete), color = Color.White)
                }
            },
            containerColor = Surface2,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accentColor.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = accentColor, modifier = Modifier.size(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = TextMuted, fontSize = 11.sp)
            Spacer(Modifier.height(1.dp))
            Text(value, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private fun formatPaused(pausedMs: Long): String {
    if (pausedMs <= 0L) return "0 sec"
    val totalSeconds = pausedMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}
