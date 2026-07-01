package com.obrynex.studyguard.aitutor

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.obrynex.studyguard.R
import com.obrynex.studyguard.ui.theme.*
import com.obrynex.studyguard.ui.adaptive.chatBubbleMaxWidthFraction
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import com.obrynex.studyguard.ai.*

/**
 * AI Tutor screen.
 *
 * Changes vs. previous version:
 *  - Retry button disabled while [AiTutorState.isRetrying] is true.
 *  - Elapsed load time shown in [ModelLoadingBanner].
 *  - Fallback UI banner shown when [AiTutorState.isFallbackMode] is true.
 *  - One-shot [AiTutorEvent]s (errors, fallback activation) shown as Snackbars.
 */
// Covering all canonical form factors in a single pass: phone portrait/landscape,
// foldable, and tablet — makes layout regressions (e.g. clipped chips, oversized
// paddings) visible at design time without needing to flash a device.
@PreviewScreenSizes
@Composable
fun AiTutorScreenPreview() {
    val context = LocalContext.current
    val previewManager = remember {
        object : AIEngineManager(context) {
            private val previewState =
                kotlinx.coroutines.flow.MutableStateFlow<AIModelState>(AIModelState.Ready)
            override val state: kotlinx.coroutines.flow.StateFlow<AIModelState> = previewState

            override suspend fun validate() = Unit
            override suspend fun requestReInit() = Unit
            override fun releaseEngine() = Unit
            override fun getEngine(): LocalAiEngine? = null
        }
    }
    AiTutorScreen(vm = AiTutorViewModel(manager = previewManager))
}

@OptIn(androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun AiTutorScreen(
    vm: AiTutorViewModel,
    windowSizeClass: WindowSizeClass = WindowSizeClass.calculateFromSize(
        androidx.compose.ui.unit.DpSize(360.dp, 640.dp)
    )
) {
    val s         by vm.state.collectAsStateWithLifecycle()
    val listState  = rememberLazyListState()
    val snackbar   = remember { SnackbarHostState() }
    val context    = LocalContext.current

    // Drive engine start from UI lifecycle
    LaunchedEffect(Unit) { vm.startEngine() }

    // Collect one-shot events
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is AiTutorEvent.InferenceError   ->
                    snackbar.showSnackbar(
                        context.getString(R.string.inference_error_snackbar, event.message),
                        duration = SnackbarDuration.Long
                    )
                is AiTutorEvent.FallbackActivated ->
                    snackbar.showSnackbar(context.getString(R.string.fallback_activated_snackbar))
            }
        }
    }

    // Auto-scroll to latest message
    LaunchedEffect(s.messages.size) {
        if (s.messages.isNotEmpty()) listState.animateScrollToItem(s.messages.lastIndex)
    }

    Scaffold(
        containerColor = BgDark,
        snackbarHost   = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(BgDark)
                .padding(padding)
        ) {
            ScreenHeader(s = s, onClear = vm::clear)
            // Gradient glow divider
            Box(
                Modifier.fillMaxWidth().height(1.dp).background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Divider, AccentPurple.copy(alpha = 0.3f), Divider, Color.Transparent)
                    )
                )
            )

            // Fallback mode banner (non-blocking — chat still usable)
            if (s.isFallbackMode) {
                FallbackModeBanner()
                HorizontalDivider(color = Divider, thickness = 0.5.dp)
            }

            when (val modelState = s.modelState) {

                is AIModelState.Idle -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color       = AccentGreen,
                            modifier    = Modifier.size(32.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }

                is AIModelState.Validating,
                is AIModelState.Loading -> {
                    ModelLoadingBanner(
                        isHashing  = modelState is AIModelState.Validating,
                        loadTimeMs = s.loadTimeMs
                    )
                }

                is AIModelState.NotFound -> {
                    ModelSetupBanner(
                        modelPath = s.modelPath,
                        onRetry   = vm::retryEngine,
                        isRetrying = s.isRetrying
                    )
                }

                is AIModelState.Failed -> {
                    ModelFailedBanner(
                        failure    = modelState.reason,
                        onRetry    = vm::retryEngine,
                        isRetrying = s.isRetrying
                    )
                }

                is AIModelState.Ready -> {
                    SubjectChipRow(subject = s.subject, onSubjectChange = vm::onSubjectChanged)
                    HorizontalDivider(color = Divider, thickness = 0.5.dp)

                    if (s.messages.isEmpty()) {
                        EmptyChat(Modifier.weight(1f))
                    } else {
                        LazyColumn(
                            state               = listState,
                            modifier            = Modifier.weight(1f),
                            contentPadding      = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(s.messages) { msg -> MessageBubble(msg, windowSizeClass) }
                        }
                    }

                    HorizontalDivider(color = Divider, thickness = 0.5.dp)
                    InputBar(
                        input         = s.input,
                        isGenerating  = s.isGenerating,
                        onInputChange = vm::onInputChanged,
                        onSend        = vm::send
                    )
                }
            }
        }
    }
}

// ── Shared header ─────────────────────────────────────────────────────────────

@Composable
private fun ScreenHeader(s: AiTutorState, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // AI icon badge
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(AccentPurple.copy(alpha = 0.15f), AccentBlue.copy(alpha = 0.10f))
                        )
                    )
                    .border(0.5.dp, AccentPurple.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = AccentPurple, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(
                    stringResource(R.string.study_assistant),
                    color      = TextPrimary,
                    fontSize   = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        Modifier.size(6.dp).clip(CircleShape)
                            .background(
                                when (s.modelState) {
                                    is AIModelState.Ready      -> AccentGreen
                                    is AIModelState.Validating,
                                    is AIModelState.Loading    -> AccentGreen.copy(alpha = 0.4f)
                                    else                       -> TextMuted
                                }
                            )
                    )
                    Text(
                        text = when {
                            s.isFallbackMode               -> stringResource(R.string.fallback_mode_textrank)
                            s.modelState is AIModelState.Ready      -> stringResource(R.string.gemma_local)
                            s.modelState is AIModelState.Validating -> stringResource(R.string.verifying_status)
                            s.modelState is AIModelState.Loading    -> stringResource(R.string.loading_status)
                            s.modelState is AIModelState.Failed     -> stringResource(R.string.load_failed_status)
                            s.modelState is AIModelState.NotFound   -> stringResource(R.string.model_not_found_status)
                            else                                     -> stringResource(R.string.waiting_status)
                        },
                        color    = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }
        if (s.messages.isNotEmpty() && s.modelState is AIModelState.Ready) {
            FilledTonalIconButton(
                onClick = onClear,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Surface3,
                    contentColor = TextMuted
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.DeleteOutline, stringResource(R.string.clear_chat), modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ── Fallback mode banner ──────────────────────────────────────────────────────

@Composable
private fun FallbackModeBanner() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(0.5.dp, AccentAmber.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .background(AccentAmber.copy(alpha = 0.05f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                .background(AccentAmber.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Warning, null, tint = AccentAmber, modifier = Modifier.size(14.dp))
        }
        Column {
            Text(
                stringResource(R.string.fallback_mode_active),
                color      = AccentAmber,
                fontSize   = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.fallback_mode_desc),
                color    = TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

// ── Loading banner ────────────────────────────────────────────────────────────

@Composable
private fun ModelLoadingBanner(isHashing: Boolean, loadTimeMs: Long) {
    // Elapsed time counter — increments every second while loading
    var elapsedSecs by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000)
            elapsedSecs++
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "load_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "loadGlow"
    )

    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Pulsing outer glow
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(AccentPurple.copy(alpha = glowAlpha), Color.Transparent)
                        )
                    )
            )
            CircularProgressIndicator(
                color       = AccentPurple,
                modifier    = Modifier.size(40.dp),
                strokeWidth = 2.5.dp
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text       = if (isHashing) stringResource(R.string.verifying_file) else stringResource(R.string.loading_model),
                color      = TextPrimary,
                fontSize   = 16.sp,
                fontWeight = FontWeight.Medium
            )
            // Show elapsed time counter
            Text(
                text     = stringResource(R.string.elapsed_seconds, elapsedSecs),
                color    = TextMuted,
                fontSize = 12.sp
            )
            if (!isHashing) {
                Text(
                    stringResource(R.string.may_take_a_minute),
                    color    = TextMuted,
                    fontSize = 12.sp
                )
            }
        }
        // Show the last successful load time as a hint
        if (loadTimeMs > 0) {
            Text(
                stringResource(R.string.last_load_time, (loadTimeMs / 1000).toInt()),
                color    = TextMuted.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
        }
    }
}

// ── Setup banner (file not found) ─────────────────────────────────────────────

@Composable
private fun ModelSetupBanner(
    modelPath  : String,
    onRetry    : () -> Unit,
    isRetrying : Boolean
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            stringResource(R.string.ai_setup_title),
            color      = TextPrimary,
            fontSize   = 17.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            stringResource(R.string.ai_setup_desc),
            color      = TextMuted,
            fontSize   = 13.sp,
            lineHeight = 22.sp
        )

        HorizontalDivider(color = Divider, thickness = 0.5.dp)

        Text(stringResource(R.string.setup_steps_header), color = TextMuted, fontSize = 12.sp)

        listOf(
            "1" to stringResource(R.string.setup_step_1),
            "2" to stringResource(R.string.setup_step_2),
            "3" to stringResource(R.string.setup_step_3)
        ).forEach { (num, step) ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(num, color = Accent.copy(0.6f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(step, color = TextMuted, fontSize = 12.sp, lineHeight = 20.sp)
            }
        }

        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Surface1)
                .padding(12.dp)
        ) {
            Text(
                "adb push gemma-2b-it-cpu-int4.bin \"$modelPath\"",
                color      = AccentGreen,
                fontSize   = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Text(stringResource(R.string.after_copy_retry), color = TextMuted, fontSize = 12.sp)

        // Retry button — disabled while isRetrying to prevent double-tap
        Button(
            onClick  = onRetry,
            enabled  = !isRetrying,
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(
                containerColor         = Accent,
                disabledContainerColor = Surface2
            )
        ) {
            if (isRetrying) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(16.dp),
                    color       = TextMuted,
                    strokeWidth = 1.5.dp
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.loading_in_progress), color = TextMuted)
            } else {
                Text(stringResource(R.string.retry), color = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
}

// ── Failed banner ─────────────────────────────────────────────────────────────

@Composable
private fun ModelFailedBanner(
    failure    : ValidationFailure,
    onRetry    : () -> Unit,
    isRetrying : Boolean
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            stringResource(R.string.model_load_failed),
            color      = androidx.compose.ui.graphics.Color(0xFFE57373),
            fontSize   = 17.sp,
            fontWeight = FontWeight.Medium
        )

        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Surface1)
                .padding(14.dp)
        ) {
            Text(
                text       = failure.toArabicMessage(),
                color      = TextPrimary,
                fontSize   = 13.sp,
                lineHeight = 22.sp
            )
        }

        when (failure) {
            is ValidationFailure.SizeTooSmall ->
                Text(
                    stringResource(
                        R.string.file_size_too_small,
                        (failure.actualBytes / 1_000_000).toInt(),
                        (failure.minimumBytes / 1_000_000).toInt()
                    ),
                    color = TextMuted, fontSize = 12.sp, lineHeight = 20.sp
                )
            is ValidationFailure.ChecksumMismatch ->
                Text(
                    stringResource(
                        R.string.expected_hash,
                        failure.expected.takeLast(12),
                        failure.actual.takeLast(12)
                    ),
                    color      = TextMuted,
                    fontSize   = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                )
            is ValidationFailure.LoadFailed ->
                Text(
                    failure.cause.message ?: "Unknown MediaPipe error",
                    color      = TextMuted,
                    fontSize   = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                )
            is ValidationFailure.InsufficientRam ->
                Text(
                    stringResource(R.string.close_apps_for_ram),
                    color = TextMuted, fontSize = 12.sp, lineHeight = 20.sp
                )
            else -> Unit
        }

        // Retry button — disabled while retrying
        Button(
            onClick  = onRetry,
            enabled  = !isRetrying,
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(
                containerColor         = Accent,
                disabledContainerColor = Surface2
            )
        ) {
            if (isRetrying) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(16.dp),
                    color       = TextMuted,
                    strokeWidth = 1.5.dp
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.loading_in_progress), color = TextMuted)
            } else {
                Text(stringResource(R.string.retry), color = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
}

// ── Subject chips ─────────────────────────────────────────────────────────────

@Composable
private fun SubjectChipRow(subject: String, onSubjectChange: (String) -> Unit) {
    val generalLabel = stringResource(R.string.subject_general)
    val subjects = listOf(
        generalLabel to "",
        stringResource(R.string.subject_math) to stringResource(R.string.subject_math),
        stringResource(R.string.subject_physics) to stringResource(R.string.subject_physics),
        stringResource(R.string.subject_chemistry) to stringResource(R.string.subject_chemistry),
        stringResource(R.string.subject_biology) to stringResource(R.string.subject_biology),
        stringResource(R.string.subject_history) to stringResource(R.string.subject_history)
    )

    LazyRow(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        contentPadding        = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        item {
            Text(stringResource(R.string.subject_label), color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        items(subjects) { (label, value) ->
            val active = subject == value || (value.isEmpty() && subject.isEmpty())
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (active) Modifier.border(0.5.dp, AccentPurple.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        else Modifier
                    )
                    .background(if (active) AccentPurple.copy(alpha = 0.10f) else Surface2)
                    .clickable { onSubjectChange(value) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .semantics { contentDescription = label }
            ) {
                Text(
                    label,
                    color = if (active) AccentPurple else TextMuted,
                    fontSize = 11.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyChat(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "empty_pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Pulsing AI icon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(AccentPurple.copy(alpha = glowAlpha), Color.Transparent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AccentPurple.copy(alpha = 0.10f))
                        .border(0.5.dp, AccentPurple.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = AccentPurple.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                }
            }
            Text(stringResource(R.string.ask_me_anything), color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.ai_runs_locally),
                color    = TextMuted.copy(0.5f),
                fontSize = 12.sp
            )
        }
    }
}

// ── Input bar ─────────────────────────────────────────────────────────────────

@Composable
private fun InputBar(
    input        : String,
    isGenerating : Boolean,
    onInputChange: (String) -> Unit,
    onSend       : () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Surface0)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value         = input,
            onValueChange = onInputChange,
            modifier      = Modifier.weight(1f),
            placeholder   = { Text(stringResource(R.string.type_your_question), color = TextMuted, fontSize = 13.sp) },
            maxLines      = 4,
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = AccentPurple.copy(alpha = 0.5f),
                unfocusedBorderColor    = Divider,
                focusedTextColor        = TextPrimary,
                unfocusedTextColor      = TextPrimary,
                cursorColor             = AccentPurple,
                focusedContainerColor   = Surface2,
                unfocusedContainerColor = Surface2
            ),
            shape = RoundedCornerShape(14.dp)
        )
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (input.isNotBlank() && !isGenerating)
                        Brush.linearGradient(listOf(AccentPurple, AccentBlue))
                    else Brush.linearGradient(listOf(Surface2, Surface2))
                )
                .clickable(enabled = input.isNotBlank() && !isGenerating) { onSend() },
            contentAlignment = Alignment.Center
        ) {
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(18.dp),
                    color       = TextMuted,
                    strokeWidth = 1.5.dp
                )
            } else {
                Icon(
                    Icons.Default.Send, null,
                    tint     = if (input.isNotBlank()) Color.White else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ── Message bubble ────────────────────────────────────────────────────────────

@Composable
private fun MessageBubble(msg: ChatMessage, windowSizeClass: WindowSizeClass) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
    ) {
        val bubbleShape = RoundedCornerShape(
            topStart    = if (msg.isUser) 16.dp else 4.dp,
            topEnd      = if (msg.isUser) 4.dp else 16.dp,
            bottomStart = 16.dp,
            bottomEnd   = 16.dp
        )
        Box(
            Modifier
                .fillMaxWidth(windowSizeClass.chatBubbleMaxWidthFraction)
                .clip(bubbleShape)
                .then(
                    if (!msg.isUser && !msg.isFallback)
                        Modifier.border(0.5.dp, AccentPurple.copy(alpha = 0.12f), bubbleShape)
                    else Modifier
                )
                .background(
                    when {
                        msg.isUser     -> Brush.linearGradient(
                            listOf(AccentGreen.copy(alpha = 0.12f), AccentGreen.copy(alpha = 0.06f))
                        )
                        msg.isFallback -> Brush.linearGradient(
                            listOf(Surface1.copy(alpha = 0.7f), Surface1.copy(alpha = 0.7f))
                        )
                        else           -> Brush.linearGradient(
                            listOf(Surface2, Surface1)
                        )
                    }
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (msg.isLoading) LoadingDots()
            else Text(msg.text, color = TextPrimary, fontSize = 14.sp, lineHeight = 22.sp)
        }
    }
}

// ── Loading dots animation — cascading wave ───────────────────────────────────

@Composable
private fun LoadingDots() {
    val inf = rememberInfiniteTransition(label = "dots")
    
    // Three dots with staggered offsets for a wave effect
    val offsets = List(3) { index ->
        inf.animateFloat(
            initialValue = 0f,
            targetValue = -8f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 1000
                    0f at 0
                    -8f at 250 with FastOutSlowInEasing
                    0f at 500 with FastOutSlowInEasing
                    0f at 1000
                },
                repeatMode = RepeatMode.Restart,
                initialStartOffset = StartOffset(index * 150)
            ),
            label = "dot_$index"
        )
    }
    val alphas = List(3) { index ->
        inf.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 1000
                    0.3f at 0
                    1f at 250
                    0.3f at 500
                    0.3f at 1000
                },
                repeatMode = RepeatMode.Restart,
                initialStartOffset = StartOffset(index * 150)
            ),
            label = "alpha_$index"
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        repeat(3) { index ->
            Box(
                Modifier
                    .offset(y = offsets[index].value.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                AccentPurple.copy(alpha = alphas[index].value),
                                AccentPurple.copy(alpha = alphas[index].value * 0.4f)
                            )
                        )
                    )
            )
        }
    }
}
