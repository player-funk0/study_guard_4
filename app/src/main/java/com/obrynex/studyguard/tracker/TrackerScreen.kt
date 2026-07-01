package com.obrynex.studyguard.tracker

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.obrynex.studyguard.R
import com.obrynex.studyguard.data.StudySession
import com.obrynex.studyguard.ui.adaptive.contentHorizontalPadding
import com.obrynex.studyguard.ui.theme.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TrackerScreen(
    vm: TrackerViewModel,
    windowSizeClass: WindowSizeClass? = null,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToAI: () -> Unit = {},
    onNavigateToHadith: () -> Unit = {},
    onNavigateToBookSummarizer: () -> Unit = {},
    onNavigateToWellbeing: () -> Unit = {},
    onNavigateToDebug: () -> Unit = {}
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val hPad = windowSizeClass?.contentHorizontalPadding ?: 16.dp
    val isExpanded = windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Expanded

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // ── Header ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = hPad, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    stringResource(R.string.study_tracker),
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                if (state.totalSessions > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.sessions_today_summary, state.totalSessions, state.todayMinutes),
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // AI Tutor button — premium pill shape
                FilledTonalIconButton(
                    onClick = onNavigateToAI,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = AccentGreen.copy(alpha = 0.12f),
                        contentColor = AccentGreen
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = stringResource(R.string.ai_tutor),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = vm::refresh) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh),
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Subtle gradient divider instead of plain line
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

        // ── Stats ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = hPad, vertical = 16.dp)
                .then(if (isExpanded) Modifier.widthIn(max = 600.dp) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PremiumStatCard(
                value = "${state.todayMinutes}",
                label = stringResource(R.string.today_min),
                icon = Icons.Default.Today,
                accentColor = AccentGreen,
                modifier = Modifier.weight(1f)
            )
            PremiumStatCard(
                value = "${state.weekMinutes}",
                label = stringResource(R.string.week_min),
                icon = Icons.Default.DateRange,
                accentColor = AccentBlue,
                modifier = Modifier.weight(1f)
            )
            PremiumStatCard(
                value = "${state.streak}",
                label = stringResource(R.string.streak),
                icon = Icons.Default.LocalFireDepartment,
                accentColor = AccentAmber,
                modifier = Modifier.weight(1f)
            )
        }

        // ── Section header ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = hPad, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.session_history),
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp
            )
            if (state.sessions.isNotEmpty()) {
                Text(
                    "${state.sessions.size}",
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Surface3)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = AccentGreen,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(32.dp)
                )
            }
        } else if (state.sessions.isEmpty()) {
            PremiumEmptyState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = hPad, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.sessions, key = { it.id }) { session ->
                    PremiumSessionRow(
                        session = session,
                        modifier = (if (isExpanded) Modifier.widthIn(max = 700.dp) else Modifier)
                            .animateItem(
                                fadeInSpec = tween(300, easing = FastOutSlowInEasing),
                                fadeOutSpec = tween(200, easing = FastOutSlowInEasing),
                                placementSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ),
                        onClick = { onNavigateToDetail(session.id) }
                    )
                }
                // Bottom spacer for nav bar
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

// ── Premium Stat Card ────────────────────────────────────────────────────────

@Composable
private fun PremiumStatCard(
    value: String,
    label: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 0.5.dp,
                color = accentColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp)
            )
            .background(
                Brush.verticalGradient(
                    listOf(
                        accentColor.copy(alpha = 0.06f),
                        Surface2
                    )
                )
            )
            .padding(horizontal = 12.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Small icon badge
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            value,
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        )
        Text(
            label,
            color = TextMuted,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp
        )
    }
}

// ── Premium Session Row ──────────────────────────────────────────────────────

@Composable
private fun PremiumSessionRow(
    session: StudySession,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault()) }
    val statusColor = if (session.completed) AccentGreen else AccentAmber

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface2)
            .clickable(onClick = onClick)
    ) {
        // Left accent bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(statusColor)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status dot + info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Status indicator
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(statusColor.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (session.completed) Icons.Default.CheckCircleOutline else Icons.Default.PauseCircleOutline,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        session.subject.ifBlank { stringResource(R.string.general_study) },
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        dateFormat.format(session.date),
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
            // Duration badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(statusColor.copy(alpha = 0.10f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    stringResource(R.string.duration_min, session.durationMinutes),
                    color = statusColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ── Premium Empty State ──────────────────────────────────────────────────────

@Composable
private fun PremiumEmptyState() {
    // Pulse animation for the icon
    val infiniteTransition = rememberInfiniteTransition(label = "empty_pulse")
    val glowEasing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f) // EaseInOutSine
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = glowEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = glowEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size((80 * scale).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(AccentGreen.copy(alpha = glowAlpha), Color.Transparent)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.BarChart,
                contentDescription = null,
                tint = AccentGreen.copy(alpha = 0.6f),
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.no_sessions_yet),
            color = TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.start_timer_prompt),
            color = TextMuted,
            fontSize = 13.sp
        )
    }
}

private val EaseInOutCubic = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
