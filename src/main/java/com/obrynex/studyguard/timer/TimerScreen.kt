package com.obrynex.studyguard.timer

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ManageHistory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.obrynex.studyguard.R
import com.obrynex.studyguard.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Study Timer screen — primary composable for the "المذاكرة" tab.
 * Premium redesign with gradient backgrounds, glow effects on the timer ring,
 * and polished controls.
 */
@Composable
fun TimerScreen(
    vm: TimerViewModel,
    onNavigateToTracker: () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(state.isFinished) {
        if (!state.isFinished) return@LaunchedEffect
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message     = context.getString(R.string.session_complete_snackbar),
                actionLabel = context.getString(R.string.view_log),
                duration    = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                onNavigateToTracker()
            }
            vm.resetTimer()
        }
    }

    Scaffold(
        containerColor = BgDark,
        snackbarHost   = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val layoutMaxWidth = this@BoxWithConstraints.maxWidth
            val layoutMaxHeight = this@BoxWithConstraints.maxHeight
            val isLandscape = layoutMaxWidth > layoutMaxHeight

            // Active timer accent — smooth color transition
            val timerColor by animateColorAsState(
                targetValue = if (state.phase is TimerPhase.Break) AccentBlue else AccentGreen,
                animationSpec = tween(600, easing = FastOutSlowInEasing),
                label = "timerColor"
            )

            if (isLandscape) {
                // ── Landscape ─────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    PremiumTimerHeader(
                        completedSessions = state.completedSessions,
                        onNavigateToTracker = onNavigateToTracker
                    )

                    // Gradient divider
                    Box(
                        Modifier.fillMaxWidth().height(1.dp).background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Divider, timerColor.copy(0.3f), Divider, Color.Transparent)
                            )
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val circleSize = (layoutMaxHeight * 0.65f).coerceAtMost(180.dp)
                        PremiumTimerRing(
                            progress = state.progress,
                            displayMinutes = state.displayMinutes,
                            displaySeconds = state.displaySeconds,
                            phase = state.phase,
                            circleSize = circleSize,
                            timerColor = timerColor
                        )

                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
                        ) {
                            PremiumSubjectField(state.subject, vm::onSubjectChanged, state.isIdle)
                            if (state.isIdle) {
                                DurationSelector(stringResource(R.string.study_duration), state.durationMinutes,
                                    { vm.onDurationChanged(maxOf(5, state.durationMinutes - 5)) },
                                    { vm.onDurationChanged(minOf(120, state.durationMinutes + 5)) })
                                DurationSelector(stringResource(R.string.break_duration), state.breakMinutes,
                                    { vm.onBreakChanged(maxOf(1, state.breakMinutes - 1)) },
                                    { vm.onBreakChanged(minOf(30, state.breakMinutes + 1)) })
                                PremiumAutoBreakToggle(state.isBreakEnabled, vm::onBreakEnabledChanged)
                            }
                            TimerControls(state, vm, timerColor)
                        }
                    }
                }
            } else {
                // ── Portrait ──────────────────────────────────────────
                val circleSize    = (layoutMaxHeight * 0.38f).coerceIn(160.dp, 240.dp)
                val verticalGap   = (layoutMaxHeight * 0.05f).coerceIn(12.dp, 44.dp)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PremiumTimerHeader(
                        completedSessions = state.completedSessions,
                        onNavigateToTracker = onNavigateToTracker,
                        verticalPadding = verticalGap.coerceAtMost(20.dp)
                    )

                    // Gradient divider
                    Box(
                        Modifier.fillMaxWidth().height(1.dp).background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Divider, timerColor.copy(0.3f), Divider, Color.Transparent)
                            )
                        )
                    )
                    Spacer(Modifier.height(verticalGap))

                    PremiumSubjectField(state.subject, vm::onSubjectChanged, state.isIdle)
                    Spacer(Modifier.height(verticalGap))

                    PremiumTimerRing(
                        progress = state.progress,
                        displayMinutes = state.displayMinutes,
                        displaySeconds = state.displaySeconds,
                        phase = state.phase,
                        circleSize = circleSize,
                        timerColor = timerColor
                    )

                    Spacer(Modifier.height(verticalGap))

                    if (state.isIdle) {
                        DurationSelector(stringResource(R.string.study_duration), state.durationMinutes,
                            { vm.onDurationChanged(maxOf(5, state.durationMinutes - 5)) },
                            { vm.onDurationChanged(minOf(120, state.durationMinutes + 5)) })
                        Spacer(Modifier.height(12.dp))
                        DurationSelector(stringResource(R.string.break_duration), state.breakMinutes,
                            { vm.onBreakChanged(maxOf(1, state.breakMinutes - 1)) },
                            { vm.onBreakChanged(minOf(30, state.breakMinutes + 1)) })
                        Spacer(Modifier.height(16.dp))
                        PremiumAutoBreakToggle(state.isBreakEnabled, vm::onBreakEnabledChanged)
                    }

                    Spacer(Modifier.weight(1f))

                    TimerControls(state, vm, timerColor)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

// ── Premium header composable ─────────────────────────────────────────────────

@Composable
private fun PremiumTimerHeader(
    completedSessions: Int,
    onNavigateToTracker: () -> Unit,
    verticalPadding: androidx.compose.ui.unit.Dp = 18.dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                stringResource(R.string.timer_title),
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            if (completedSessions > 0) {
                Text(
                    stringResource(R.string.sessions_today_count, completedSessions),
                    color = TextMuted,
                    fontSize = 13.sp
                )
            }
        }
        FilledTonalIconButton(
            onClick = onNavigateToTracker,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Surface3,
                contentColor = TextMuted
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(40.dp)
        ) {
            Icon(Icons.Outlined.ManageHistory, stringResource(R.string.session_log), modifier = Modifier.size(20.dp))
        }
    }
}

// ── Premium timer ring ────────────────────────────────────────────────────────

@Composable
private fun PremiumTimerRing(
    progress: Float,
    displayMinutes: Int,
    displaySeconds: Int,
    phase: TimerPhase,
    circleSize: androidx.compose.ui.unit.Dp,
    timerColor: Color
) {
    val isActive = phase !is TimerPhase.Idle

    // Smooth easing for natural "breathing" glow feel
    val glowEasing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f) // EaseInOutSine

    // ── Powerful pulsing glow animation ──
    val infiniteTransition = rememberInfiniteTransition(label = "timer_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = if (isActive) 0.08f else 0.03f,
        targetValue = if (isActive) 0.25f else 0.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isActive) 1800 else 3000, easing = glowEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.08f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isActive) 1800 else 3000, easing = glowEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )
    val borderGlow by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = if (isActive) 0.30f else 0.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isActive) 1500 else 2500, easing = glowEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "borderGlow"
    )

    // Smoothly animate the progress arc so it doesn't jump
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "smoothProgress"
    )

    Box(contentAlignment = Alignment.Center) {
        // Outer pulsing glow ring — scales and breathes
        Box(
            modifier = Modifier
                .size(circleSize + 24.dp)
                .scale(glowScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            timerColor.copy(alpha = glowAlpha),
                            timerColor.copy(alpha = glowAlpha * 0.3f),
                            Color.Transparent
                        )
                    )
                )
        )
        // Inner border glow ring
        Box(
            modifier = Modifier
                .size(circleSize + 6.dp)
                .clip(CircleShape)
                .border(
                    width = 1.5.dp,
                    brush = Brush.sweepGradient(
                        listOf(
                            timerColor.copy(alpha = borderGlow),
                            Color.Transparent,
                            timerColor.copy(alpha = borderGlow * 0.5f),
                            Color.Transparent,
                            timerColor.copy(alpha = borderGlow)
                        )
                    ),
                    shape = CircleShape
                )
        )
        // Main progress — uses smoothly animated value
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.size(circleSize),
            color = timerColor,
            strokeWidth = 6.dp,
            trackColor = Surface3
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val timeFontSize = (circleSize.value * 0.21f).coerceIn(28f, 52f)
            Text(
                text = String.format("%02d:%02d", displayMinutes, displaySeconds),
                color = TextPrimary,
                fontSize = timeFontSize.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp
            )
            if (phase is TimerPhase.Break) {
                Text(stringResource(R.string.break_label), color = AccentBlue, fontSize = 14.sp)
            } else if (phase is TimerPhase.Paused) {
                Text(stringResource(R.string.paused_status), color = TextMuted, fontSize = 14.sp)
            }
        }
    }
}

// ── Premium subject field ─────────────────────────────────────────────────────

@Composable
private fun PremiumSubjectField(
    subject: String,
    onSubjectChanged: (String) -> Unit,
    isIdle: Boolean
) {
    OutlinedTextField(
        value = subject,
        onValueChange = onSubjectChanged,
        placeholder = { Text(stringResource(R.string.subject_optional), color = TextMuted) },
        enabled = isIdle,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentGreen,
            unfocusedBorderColor = Divider,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            disabledTextColor = TextMuted,
            disabledBorderColor = Divider,
            focusedContainerColor = Surface2,
            unfocusedContainerColor = Surface2
        ),
        shape = RoundedCornerShape(14.dp),
        singleLine = true
    )
}

// ── Premium auto-break toggle ─────────────────────────────────────────────────

@Composable
private fun PremiumAutoBreakToggle(
    isEnabled: Boolean,
    onChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface2)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(stringResource(R.string.auto_break), color = TextMuted, fontSize = 14.sp)
        Switch(
            checked = isEnabled,
            onCheckedChange = onChanged,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BgDark,
                checkedTrackColor = AccentGreen,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = Surface3
            )
        )
    }
}

// ── Timer controls ────────────────────────────────────────────────────────────

@Composable
private fun TimerControls(
    state: TimerUiState,
    vm: TimerViewModel,
    timerColor: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        when {
            state.isIdle || state.isFinished -> {
                Button(
                    onClick = vm::startTimer,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = timerColor),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 2.dp)
                ) {
                    Text(
                        stringResource(R.string.start_studying),
                        color = BgDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
            state.phase is TimerPhase.Break -> {
                Text(stringResource(R.string.break_in_progress), color = AccentBlue, fontSize = 15.sp)
            }
            else -> {
                OutlinedButton(
                    onClick = vm::stopTimer,
                    modifier = Modifier.height(54.dp),
                    border = BorderStroke(1.dp, SolidColor(Divider)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.stop), color = TextMuted)
                }
                Button(
                    onClick = vm::togglePauseResume,
                    modifier = Modifier.weight(1f).height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = timerColor),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 2.dp)
                ) {
                    Text(
                        if (state.isPaused) stringResource(R.string.resume) else stringResource(R.string.pause),
                        color = BgDark,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ── Duration selector ─────────────────────────────────────────────────────────

@Composable
private fun DurationSelector(
    label: String,
    minutes: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(0.5.dp, Divider, RoundedCornerShape(14.dp))
            .background(Surface2)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextMuted, fontSize = 14.sp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalIconButton(
                onClick = onDecrement,
                modifier = Modifier.size(32.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Surface3,
                    contentColor = TextPrimary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.minus_sign), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                stringResource(R.string.duration_unit_d, minutes),
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            FilledTonalIconButton(
                onClick = onIncrement,
                modifier = Modifier.size(32.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Surface3,
                    contentColor = TextPrimary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.plus_sign), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
