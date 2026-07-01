package com.obrynex.studyguard.summarizer.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.obrynex.studyguard.R
import com.obrynex.studyguard.ai.AIModelState
import com.obrynex.studyguard.ui.adaptive.contentHorizontalPadding
import com.obrynex.studyguard.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SummarizerScreen(
    vm: SummarizerViewModel,
    windowSizeClass: WindowSizeClass? = null
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val hPad = windowSizeClass?.contentHorizontalPadding ?: 16.dp
    val isExpanded = windowSizeClass?.widthSizeClass ==
        androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Expanded

    Scaffold(
        containerColor = BgDark,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDark)
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
        Column(
            modifier = Modifier
                .then(if (isExpanded) Modifier.widthIn(max = 700.dp) else Modifier.fillMaxWidth())
        ) {
            // ── Header ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = hPad, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(R.string.text_summarizer),
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        stringResource(R.string.paste_text_to_get_summary),
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
                if (state.summaryText.isNotEmpty()) {
                    FilledTonalIconButton(
                        onClick = vm::clear,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Surface3,
                            contentColor = TextMuted
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.clear), modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Gradient divider
            Box(
                Modifier.fillMaxWidth().height(1.dp).background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Divider, AccentGreen.copy(alpha = 0.3f), Divider, Color.Transparent)
                    )
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(hPad),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                // ── AI toggle card ──────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface2)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.use_ai_model), color = TextMuted, fontSize = 13.sp)
                    Switch(
                        checked = state.useAI,
                        onCheckedChange = { vm.toggleUseAI() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BgDark,
                            checkedTrackColor = AccentGreen,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = Surface3
                        )
                    )
                }

                // ── Summary level chips ─────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryLevel.values().forEach { level ->
                        val selected = state.summaryLevel == level
                        FilterChip(
                            selected = selected,
                            onClick = { vm.onSummaryLevelChanged(level) },
                            label = { Text(stringResource(level.labelResId), fontSize = 12.sp) },
                            shape = RoundedCornerShape(10.dp),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = if (selected) AccentGreen.copy(alpha = 0.3f) else Divider,
                                selectedBorderColor = AccentGreen.copy(alpha = 0.3f),
                                enabled = true,
                                selected = selected
                            ),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentGreen.copy(alpha = 0.15f),
                                selectedLabelColor = AccentGreen,
                                containerColor = Surface2,
                                labelColor = TextMuted
                            )
                        )
                    }
                }

                // ── Input area ──────────────────────────────────
                OutlinedTextField(
                    value = state.inputText,
                    onValueChange = vm::onInputChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp, max = 300.dp),
                    placeholder = { Text(stringResource(R.string.paste_text_here), color = TextMuted.copy(alpha = 0.5f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentGreen,
                        unfocusedBorderColor = Divider,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = Surface2,
                        unfocusedContainerColor = Surface2
                    ),
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default
                    )
                )

                // ── Action buttons ──────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { clipboard.getText()?.let { vm.onInputChanged(it.text) } },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, SolidColor(Divider))
                    ) {
                        Icon(Icons.Outlined.ContentPaste, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.paste), color = TextMuted, fontSize = 13.sp)
                    }

                    Button(
                        onClick = vm::summarize,
                        enabled = state.inputText.isNotBlank() && !state.isSummarizing,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        shape = RoundedCornerShape(14.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 1.dp)
                    ) {
                        if (state.isSummarizing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = BgDark,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.AutoStories, null, tint = BgDark, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (state.isSummarizing) stringResource(R.string.summarizing) else stringResource(R.string.summarize),
                            color = BgDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                // ── Error ───────────────────────────────────────────────
                AnimatedVisibility(
                    visible = state.error != null,
                    enter = fadeIn(
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + expandVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ),
                    exit = fadeOut(
                        animationSpec = tween(200, easing = FastOutSlowInEasing)
                    ) + shrinkVertically(
                        animationSpec = tween(250, easing = FastOutSlowInEasing)
                    )
                ) {
                    state.error?.let { error ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(0.5.dp, AccentRed.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .background(AccentRed.copy(alpha = 0.06f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Error, null, tint = AccentRed, modifier = Modifier.size(18.dp))
                            Text(error, color = AccentRed, fontSize = 13.sp)
                        }
                    }
                }

                // ── Summary result ──────────────────────────────────────────
                AnimatedVisibility(
                    visible = state.summaryText.isNotEmpty(),
                    enter = fadeIn(
                        animationSpec = tween(400, easing = FastOutSlowInEasing)
                    ) + expandVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ),
                    exit = fadeOut(
                        animationSpec = tween(200)
                    ) + shrinkVertically(
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    )
                ) {
                    val copiedMsg = stringResource(R.string.copied_to_clipboard)
                    PremiumSummaryResultCard(
                        summary = state.summaryText,
                        onCopy = {
                            clipboard.setText(AnnotatedString(state.summaryText))
                            scope.launch { snackbar.showSnackbar(copiedMsg) }
                        }
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun PremiumSummaryResultCard(
    summary: String,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(0.5.dp, AccentGreen.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .background(Surface2)
    ) {
        // Gradient header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(AccentGreen.copy(alpha = 0.08f), Color.Transparent)
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.summary),
                color = AccentGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            FilledTonalIconButton(
                onClick = onCopy,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = AccentGreen.copy(alpha = 0.12f),
                    contentColor = AccentGreen
                ),
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.ContentCopy, stringResource(R.string.copy), modifier = Modifier.size(14.dp))
            }
        }

        Text(
            summary,
            color = TextPrimary,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
            modifier = Modifier.padding(16.dp)
        )
    }
}
