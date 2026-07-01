package com.obrynex.studyguard.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obrynex.studyguard.R
import com.obrynex.studyguard.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DebugInfoScreen(vm: DebugInfoViewModel) {
    val s by vm.state.collectAsState()
    val scroll = rememberScrollState()
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val emDash = stringResource(R.string.em_dash)
    val hashCopiedMsg = stringResource(R.string.hash_copied)
    val cachedHashCopiedMsg = stringResource(R.string.cached_hash_copied)
    val errorCopiedMsg = stringResource(R.string.error_copied)

    // Pulse animation for status dot
    val infiniteTransition = rememberInfiniteTransition(label = "status_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Scaffold(
        containerColor = BgDark,
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDark)
                .verticalScroll(scroll)
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Header ─────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.ai_engine_diagnostics),
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                FilledTonalIconButton(
                    onClick = vm::refresh,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Surface3,
                        contentColor = TextMuted
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Refresh, stringResource(R.string.refresh), modifier = Modifier.size(20.dp))
                }
            }

            Text(stringResource(R.string.debug_build_only), color = TextMuted, fontSize = 11.sp)

            // Gradient divider
            Box(
                Modifier.fillMaxWidth().height(1.dp).background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Divider, AccentBlue.copy(0.3f), Divider, Color.Transparent)
                    )
                )
            )

            // ── Engine state with animated dot ─────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.engine_state), color = TextMuted, fontSize = 12.sp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val stateColor = when (s.stateName) {
                        "Ready" -> AccentGreen
                        "Failed", "NotFound" -> AccentRed
                        "Validating", "Loading" -> AccentAmber
                        else -> TextMuted
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (s.stateName in listOf("Validating", "Loading"))
                                    stateColor.copy(alpha = pulseAlpha)
                                else stateColor
                            )
                    )
                    Text(
                        s.stateName,
                        color = stateColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // ── Detail card ────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(0.5.dp, Divider, RoundedCornerShape(16.dp))
                    .background(Surface2)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DiagRow(
                    label = stringResource(R.string.load_time),
                    value = if (s.loadTimeMs >= 0) stringResource(R.string.load_time_ms, s.loadTimeMs) else emDash
                )

                DiagSection(
                    title = stringResource(R.string.in_memory_hash),
                    content = s.hash ?: emDash,
                    onCopy = {
                        s.hash?.let { copyToClipboard(ctx, "hash", it) }
                        snack.tryShowSnackbar(scope, hashCopiedMsg)
                    }
                )

                DiagSection(
                    title = stringResource(R.string.cached_hash_datastore),
                    content = s.cachedHash ?: emDash,
                    onCopy = {
                        s.cachedHash?.let { copyToClipboard(ctx, "cached_hash", it) }
                        snack.tryShowSnackbar(scope, cachedHashCopiedMsg)
                    }
                )

                DiagRow(
                    label = stringResource(R.string.cached_file_size),
                    value = if (s.cachedSize >= 0) stringResource(R.string.cached_file_size_mb, s.cachedSize / 1_000_000L) else emDash
                )
                DiagRow(
                    label = stringResource(R.string.cached_last_modified),
                    value = if (s.cachedModified >= 0)
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(s.cachedModified))
                    else emDash
                )
            }

            // ── Device RAM card ────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(0.5.dp, Divider, RoundedCornerShape(16.dp))
                    .background(Surface2)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(AccentBlue.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Memory, null, tint = AccentBlue, modifier = Modifier.size(14.dp))
                    }
                    Text(stringResource(R.string.device_ram), color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                DiagRow(
                    label = stringResource(R.string.total_ram),
                    value = if (s.totalRamMb >= 0) stringResource(R.string.ram_mb, s.totalRamMb) else emDash
                )
                DiagRow(
                    label = stringResource(R.string.available_ram),
                    value = if (s.availRamMb >= 0) stringResource(R.string.ram_mb, s.availRamMb) else emDash,
                    valueColor = when {
                        s.availRamMb < 0 -> TextMuted
                        s.isLowMemory -> AccentRed
                        s.availRamMb < 512 -> AccentAmber
                        else -> AccentGreen
                    }
                )
                if (s.isLowMemory) {
                    Text(stringResource(R.string.low_memory_warning), color = AccentRed, fontSize = 11.sp)
                }
            }

            // ── Error section ──────────────────────────────────────
            s.errorMessage?.let { err ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(0.5.dp, AccentRed.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .background(AccentRed.copy(alpha = 0.04f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.error_label), color = AccentRed, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        IconButton(
                            onClick = {
                                copyToClipboard(ctx, "error", err)
                                snack.tryShowSnackbar(scope, errorCopiedMsg)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Surface1)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = err,
                            color = Color(0xFFEF9A9A),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // ── Force Re-init ──────────────────────────────────────
            OutlinedButton(
                onClick = vm::forceReInit,
                enabled = !s.isForceReiniting,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentAmber),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, if (s.isForceReiniting) Surface2 else AccentAmber.copy(alpha = 0.5f)
                )
            ) {
                if (s.isForceReiniting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = TextMuted,
                        strokeWidth = 1.5.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.reinitialising), color = TextMuted, fontSize = 13.sp)
                } else {
                    Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.force_reinit), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String, valueColor: Color = TextPrimary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextMuted, fontSize = 12.sp)
        Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DiagSection(title: String, content: String, onCopy: (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = TextMuted, fontSize = 12.sp)
            val emDash = stringResource(R.string.em_dash)
            if (onCopy != null && content != emDash) {
                IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ContentCopy, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Surface1)
                .padding(10.dp)
        ) {
            Text(
                text = content,
                color = TextPrimary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp
            )
        }
    }
}

private fun copyToClipboard(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun SnackbarHostState.tryShowSnackbar(
    scope: kotlinx.coroutines.CoroutineScope,
    message: String
) {
    scope.launch { showSnackbar(message) }
}
