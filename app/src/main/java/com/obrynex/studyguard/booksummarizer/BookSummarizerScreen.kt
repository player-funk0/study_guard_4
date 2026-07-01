package com.obrynex.studyguard.booksummarizer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.*
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import com.obrynex.studyguard.ai.AIEngineManager
import com.obrynex.studyguard.ai.AIModelState
import com.obrynex.studyguard.ui.theme.*

// ── Public entry-point ─────────────────────────────────────────────────────────

/**
 * Root composable for the Book Summarizer feature.
 *
 * Wires the [BookSummarizerViewModel] to the UI hierarchy and handles
 * one-time side-effects (file pick results, clipboard copy).
 */
@Composable
fun BookSummarizerScreen(
    vm: BookSummarizerViewModel,
    onBack: (() -> Unit)? = null
) {
    val state     by vm.state.collectAsStateWithLifecycle()
    val ctx        = LocalContext.current
    val clipboard  = LocalClipboardManager.current
    val snackbar   = remember { SnackbarHostState() }
    val scope      = rememberCoroutineScope()

    // File picker — opens system file browser filtered to plain text
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) vm.loadFile(ctx, uri)
    }

    Scaffold(
        containerColor       = BgDark,
        snackbarHost         = { SnackbarHost(snackbar) },
        // Fix 3: FAB declared inside Scaffold so it receives correct insets and
        // the Scaffold automatically reserves space so content is never covered.
        floatingActionButton = {
            SummarizerFab(
                canSummarize = state.canSummarize,
                isRunning    = state.isRunning,
                onSummarize  = vm::startSummarizing,
                onCancel     = vm::cancel
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDark)
                .padding(padding)
                // Fix 5: shift the whole content column up when the soft keyboard
                // opens so the text area is never obscured.
                .imePadding()
        ) {
            // ── Header ────────────────────────────────────────────────────────
            BookSummarizerHeader(
                wordCount  = state.wordCount,
                fileName   = state.fileName,
                isRunning  = state.isRunning,
                onReset    = vm::reset,
                onBack     = onBack
            )
            HorizontalDivider(color = Divider, thickness = 0.5.dp)

            // ── Model-not-ready banner ────────────────────────────────────────
            AnimatedVisibility(visible = !state.isModelReady) {
                ModelStatusBanner(
                    modelState  = state.modelState,
                    isModelBusy = state.isModelBusy,
                    onRetry     = vm::retryEngine
                )
            }

            // ── Scrollable body ───────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // Input card
                InputCard(
                    text         = state.bookText,
                    fileName     = state.fileName,
                    isRunning    = state.isRunning,
                    onTextChange = vm::onBookTextChanged,
                    onPickFile   = {
                        filePicker.launch(arrayOf("text/plain", "text/*"))
                    }
                )

                // Error chip
                AnimatedVisibility(visible = state.error != null) {
                    state.error?.let { ErrorChip(it) }
                }

                // Progress card — visible as soon as a run starts
                AnimatedVisibility(
                    visible = state.phase != SummarizerPhase.Idle,
                    enter   = fadeIn() + expandVertically(),
                    exit    = fadeOut() + shrinkVertically()
                ) {
                    ProgressCard(state = state)
                }

                // Final summary card
                AnimatedVisibility(
                    visible = state.finalSummary.isNotEmpty(),
                    enter   = fadeIn() + expandVertically(),
                    exit    = fadeOut()
                ) {
                    FinalSummaryCard(
                        summary = state.finalSummary,
                        isDone  = state.phase == SummarizerPhase.Done,
                        onCopy  = {
                            clipboard.setText(AnnotatedString(state.finalSummary))
                            scope.launch { snackbar.showSnackbar("تم نسخ الملخص ✓") }
                        }
                    )
                }

                // Fix 4: no magic Spacer needed — Scaffold's floatingActionButton
                // parameter automatically adds bottom padding equal to FAB height.
            }
        }
    }
}

// ── Header ─────────────────────────────────────────────────────────────────────

@Composable
private fun BookSummarizerHeader(
    wordCount : Int,
    fileName  : String,
    isRunning : Boolean,
    onReset   : () -> Unit,
    onBack    : (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
            Column {
                Text(
                    text       = "تلخيص الكتب",
                    color      = TextPrimary,
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (wordCount > 0) {
                    Text(
                        text     = "$wordCount كلمة${if (fileName.isNotEmpty()) " · $fileName" else ""}",
                        color    = TextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (wordCount > 0 || isRunning) {
            IconButton(onClick = onReset) {
                Icon(
                    Icons.Filled.RestartAlt,
                    contentDescription = "إعادة تعيين",
                    tint   = TextMuted,
                    modifier = Modifier.size(22.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.size(48.dp))
        }
    }
}

// ── Model status banner ────────────────────────────────────────────────────────

@Composable
private fun ModelStatusBanner(
    modelState  : AIModelState,
    isModelBusy : Boolean,
    onRetry     : () -> Unit
) {
    val (icon, title, subtitle, showRetry) = when (modelState) {
        is AIModelState.Idle       -> BannerData(Icons.Outlined.HourglassEmpty, "في انتظار النموذج…", "", false)
        is AIModelState.Validating -> BannerData(Icons.Outlined.Security,        "التحقق من الملف…",  "", false)
        is AIModelState.Loading    -> BannerData(Icons.Outlined.Memory,          "تحميل النموذج…",   "قد يستغرق هذا دقيقة أو أكثر", false)
        is AIModelState.NotFound   -> BannerData(Icons.Outlined.FolderOpen,      "ملف النموذج غير موجود", "راجع شاشة مساعد الدراسة للإعداد", true)
        is AIModelState.Failed     -> BannerData(Icons.Default.Warning,          "فشل تحميل النموذج", modelState.reason.toArabicMessage(), true)
        is AIModelState.Ready      -> null
    } ?: return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface2)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isModelBusy) {
            CircularProgressIndicator(
                color       = AccentGreen,
                strokeWidth = 2.dp,
                modifier    = Modifier.size(20.dp)
            )
        } else {
            Icon(icon, null, tint = AccentGreen, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title,    color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = TextMuted, fontSize = 11.sp)
            }
        }
        if (showRetry) {
            TextButton(onClick = onRetry) {
                Text("إعادة المحاولة", color = AccentGreen, fontSize = 12.sp)
            }
        }
    }
}

private data class BannerData(
    val icon      : androidx.compose.ui.graphics.vector.ImageVector,
    val title     : String,
    val subtitle  : String,
    val showRetry : Boolean
)

// ── Input card ─────────────────────────────────────────────────────────────────

@Composable
private fun InputCard(
    text         : String,
    fileName     : String,
    isRunning    : Boolean,
    onTextChange : (String) -> Unit,
    onPickFile   : () -> Unit
) {
    // Fix 6: compute a height that is 25% of the screen — at least 100dp, at most 200dp.
    // This keeps the text area usable on small phones (320dp tall) and not wasteful on
    // tablets.
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val textAreaMinHeight = (screenHeight * 0.25f).coerceIn(100.dp, 200.dp)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Surface2
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // Toolbar row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text       = if (fileName.isNotEmpty()) fileName else "نص الكتاب",
                    color      = if (fileName.isNotEmpty()) AccentGreen else TextMuted,
                    fontSize   = 13.sp,
                    fontWeight = if (fileName.isNotEmpty()) FontWeight.Medium else FontWeight.Normal
                )
                OutlinedButton(
                    onClick        = onPickFile,
                    enabled        = !isRunning,
                    shape          = RoundedCornerShape(8.dp),
                    border         = BorderStroke(1.dp,
                        if (isRunning) TextMuted.copy(alpha = 0.3f)
                        else           AccentGreen.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Outlined.AttachFile,
                        contentDescription = null,
                        tint     = if (isRunning) TextMuted else AccentGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "استيراد .txt",
                        color    = if (isRunning) TextMuted else AccentGreen,
                        fontSize = 12.sp
                    )
                }
            }

            HorizontalDivider(color = Divider, thickness = 0.5.dp)

            // Text area — multiline, RTL-aware, adaptive min height
            BasicTextField(
                value         = text,
                onValueChange = onTextChange,
                enabled       = !isRunning,
                textStyle     = LocalTextStyle.current.copy(
                    color         = TextPrimary,
                    fontSize      = 14.sp,
                    lineHeight    = 22.sp,
                    textDirection = TextDirection.Content
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType     = KeyboardType.Text,
                    capitalization   = KeyboardCapitalization.Sentences,
                    // No ImeAction.Done — this is a multiline field; Enter inserts
                    // a newline rather than dismissing the keyboard.
                    imeAction        = ImeAction.Default
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = textAreaMinHeight)
                    .padding(16.dp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) {
                            Text(
                                text       = "الصق نص الكتاب هنا أو استورد ملف .txt …",
                                color      = TextMuted.copy(alpha = 0.5f),
                                fontSize   = 14.sp,
                                lineHeight = 22.sp
                            )
                        }
                        inner()
                    }
                }
            )
        }
    }
}

// ── Progress card ──────────────────────────────────────────────────────────────

@Composable
private fun ProgressCard(state: BookSummarizerState) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Surface(shape = RoundedCornerShape(16.dp), color = Surface2) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Phase label + pulse dot
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.isRunning) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .alpha(pulse)
                            .background(AccentGreen, shape = RoundedCornerShape(50))
                    )
                } else {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint     = AccentGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text       = state.progressLabel,
                    color      = if (state.isRunning) AccentGreen else TextPrimary,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Progress bar
            LinearProgressIndicator(
                progress          = { state.progress },
                modifier          = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color             = AccentGreen,
                trackColor        = Divider
            )

            // Streaming chunk preview
            AnimatedVisibility(
                visible = state.chunkStreamText.isNotEmpty(),
                enter   = fadeIn(),
                exit    = fadeOut()
            ) {
                Text(
                    text      = state.chunkStreamText,
                    color     = TextMuted,
                    fontSize  = 12.sp,
                    lineHeight = 18.sp,
                    maxLines  = 5,
                    overflow  = TextOverflow.Ellipsis,
                    style     = LocalTextStyle.current.copy(
                        textDirection = TextDirection.Content
                    )
                )
            }
        }
    }
}

// ── Final summary card ─────────────────────────────────────────────────────────

@Composable
private fun FinalSummaryCard(
    summary  : String,
    isDone   : Boolean,
    onCopy   : () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Surface2
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // Card header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(AccentGreen.copy(alpha = 0.12f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.AutoStories,
                        contentDescription = null,
                        tint     = AccentGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text       = if (isDone) "الملخص النهائي" else "جارٍ إنشاء الملخص…",
                        color      = TextPrimary,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (isDone) {
                    IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = "نسخ",
                            tint     = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = Divider, thickness = 0.5.dp)

            // Summary text
            SelectionContainer {
                Text(
                    text      = summary,
                    color     = TextPrimary,
                    fontSize  = 14.sp,
                    lineHeight = 24.sp,
                    modifier  = Modifier.padding(20.dp),
                    style     = LocalTextStyle.current.copy(
                        textDirection = TextDirection.Content
                    )
                )
            }
        }
    }
}

// ── Error chip ─────────────────────────────────────────────────────────────────

@Composable
private fun ErrorChip(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFF5252).copy(alpha = 0.12f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Filled.Error,
            contentDescription = null,
            tint     = Color(0xFFFF5252),
            modifier = Modifier.size(18.dp)
        )
        Text(
            text     = message,
            color    = Color(0xFFFF5252),
            fontSize = 13.sp
        )
    }
}

// ── FAB ────────────────────────────────────────────────────────────────────────

@Composable
private fun SummarizerFab(
    canSummarize : Boolean,
    isRunning    : Boolean,
    onSummarize  : () -> Unit,
    onCancel     : () -> Unit
) {
    AnimatedContent(
        targetState = isRunning,
        label       = "fab",
        transitionSpec = {
            (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut())
        }
    ) { running ->
        if (running) {
            FloatingActionButton(
                onClick           = onCancel,
                containerColor    = Color(0xFFFF5252),
                contentColor      = Color.White,
                shape             = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.Stop, "إيقاف", modifier = Modifier.size(22.dp))
            }
        } else {
            ExtendedFloatingActionButton(
                onClick           = onSummarize,
                containerColor    = if (canSummarize) AccentGreen else Surface2,
                contentColor      = if (canSummarize) Color.Black  else TextMuted,
                shape             = RoundedCornerShape(16.dp),
                text              = { Text("لخّص الكتاب", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
                icon              = {
                    Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(20.dp))
                }
            )
        }
    }
}

// ── Aliases for missing BasicTextField import ─────────────────────────────────

@Composable
private fun BasicTextField(
    value           : String,
    onValueChange   : (String) -> Unit,
    enabled         : Boolean,
    textStyle       : androidx.compose.ui.text.TextStyle,
    keyboardOptions : androidx.compose.foundation.text.KeyboardOptions,
    modifier        : Modifier,
    decorationBox   : @Composable (innerTextField: @Composable () -> Unit) -> Unit
) {
    androidx.compose.foundation.text.BasicTextField(
        value           = value,
        onValueChange   = onValueChange,
        enabled         = enabled,
        textStyle       = textStyle,
        keyboardOptions = keyboardOptions,
        modifier        = modifier,
        decorationBox   = decorationBox
    )
}

@Composable
private fun SelectionContainer(content: @Composable () -> Unit) {
    androidx.compose.foundation.text.selection.SelectionContainer(content = content)
}

// ── Previews ───────────────────────────────────────────────────────────────

@PreviewScreenSizes
@Composable
private fun BookSummarizerScreenPreview() {
    val context = LocalContext.current
    val previewManager = remember {
        object : AIEngineManager(context) {
            private val previewState =
                kotlinx.coroutines.flow.MutableStateFlow<AIModelState>(AIModelState.Ready)
            override val state: kotlinx.coroutines.flow.StateFlow<AIModelState> = previewState

            override suspend fun validate() = Unit
            override suspend fun requestReInit() = Unit
            override fun releaseEngine() = Unit
            override fun getEngine(): com.obrynex.studyguard.ai.LocalAiEngine? = null
        }
    }
    BookSummarizerScreen(vm = BookSummarizerViewModel(manager = previewManager))
}
