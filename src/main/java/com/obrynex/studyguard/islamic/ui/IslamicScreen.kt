package com.obrynex.studyguard.islamic.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import com.obrynex.studyguard.islamic.data.Hadith
import com.obrynex.studyguard.islamic.data.HadithCategory
import com.obrynex.studyguard.ui.theme.*

@Composable
fun IslamicScreen(
    vm: IslamicViewModel,
    onBack: () -> Unit = {}
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx   = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {

        // ── Header ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text       = "أذكار وأحاديث",
                    color      = TextPrimary,
                    fontSize   = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text     = "${state.hadiths.size} حديث",
                    color    = TextMuted,
                    fontSize = 13.sp
                )
            }
            // Spacer to balance the back button on the left
            Spacer(modifier = Modifier.size(48.dp))
        }

        HorizontalDivider(color = Divider, thickness = 0.5.dp)

        LazyColumn(
            modifier            = Modifier.fillMaxSize(),
            contentPadding      = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {

            // ── حديث اليوم ─────────────────────────────────────────────────
            item(key = "daily") {
                DailyHadithCard(
                    hadith    = state.dailyHadith,
                    onCopy    = { copyToClipboard(ctx, state.dailyHadith.text) },
                    modifier  = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                )
            }

            // ── Search bar ─────────────────────────────────────────────────
            item(key = "search") {
                SearchBar(
                    query     = state.searchQuery,
                    onChanged = vm::onSearchQueryChanged,
                    modifier  = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(10.dp))
            }

            // ── Category chips ─────────────────────────────────────────────
            item(key = "chips") {
                CategoryChips(
                    selected      = state.selectedCategory,
                    nawawiActive  = state.showNawawiOnly,
                    onCategory    = vm::onCategorySelected,
                    onNawawi      = vm::toggleNawawiFilter
                )
                Spacer(Modifier.height(8.dp))
            }

            // ── Hadith list ────────────────────────────────────────────────
            if (state.hadiths.isEmpty()) {
                item(key = "empty") {
                    EmptyState(modifier = Modifier.padding(top = 48.dp))
                }
            } else {
                items(
                    items = state.hadiths,
                    key   = { it.id }
                ) { hadith ->
                    HadithCard(
                        hadith       = hadith,
                        isBookmarked = hadith.id in state.bookmarkedIds,
                        onBookmark   = { vm.toggleBookmark(hadith.id) },
                        onCopy       = { copyToClipboard(ctx, hadith.text) },
                        modifier     = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

// ── حديث اليوم card ────────────────────────────────────────────────────────

@Composable
private fun DailyHadithCard(
    hadith   : Hadith,
    onCopy   : () -> Unit,
    modifier : Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1A3A5C), Color(0xFF0D2137))
                )
            )
            .border(
                width = 0.5.dp,
                color = Color(0xFF2E5480),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(18.dp)
    ) {
        Column {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Text(
                    text       = "✨ حديث اليوم",
                    color      = Color(0xFF7EC8E3),
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                hadith.nawawi?.let {
                    Text(
                        text     = "الأربعون النووية #$it",
                        color    = Color(0xFF7EC8E3).copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text      = hadith.text,
                color     = Color(0xFFF0F4F8),
                fontSize  = 15.sp,
                lineHeight = 26.sp,
                textAlign = TextAlign.Right,
                style     = LocalTextStyle.current.copy(
                    textDirection = TextDirection.Rtl
                )
            )

            Spacer(Modifier.height(12.dp))

            HorizontalDivider(color = Color(0xFF2E5480), thickness = 0.5.dp)
            Spacer(Modifier.height(10.dp))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text       = hadith.narrator,
                        color      = Color(0xFF7EC8E3),
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text     = "${hadith.source}${hadith.bookNumber?.let { " ($it)" } ?: ""}",
                        color    = Color(0xFF7EC8E3).copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "نسخ",
                        tint               = Color(0xFF7EC8E3).copy(alpha = 0.7f),
                        modifier           = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text     = "💡 ${hadith.benefit}",
                color    = Color(0xFFFFD580),
                fontSize = 12.sp
            )
        }
    }
}

// ── Search bar ─────────────────────────────────────────────────────────────

@Composable
private fun SearchBar(
    query    : String,
    onChanged: (String) -> Unit,
    modifier : Modifier = Modifier
) {
    OutlinedTextField(
        value         = query,
        onValueChange = onChanged,
        modifier      = modifier.fillMaxWidth(),
        placeholder   = {
            Text("ابحث في الأحاديث...", color = TextMuted, fontSize = 14.sp)
        },
        leadingIcon   = {
            Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted)
        },
        singleLine    = true,
        shape         = RoundedCornerShape(12.dp),
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = Color(0xFF2E5480),
            unfocusedBorderColor = Divider,
            focusedTextColor     = TextPrimary,
            unfocusedTextColor   = TextPrimary,
            cursorColor          = Color(0xFF7EC8E3),
            focusedContainerColor    = Surface2,
            unfocusedContainerColor  = Surface2
        ),
        textStyle = LocalTextStyle.current.copy(
            fontSize      = 14.sp,
            textDirection = TextDirection.Rtl
        )
    )
}

// ── Category chips ─────────────────────────────────────────────────────────

@Composable
private fun CategoryChips(
    selected     : HadithCategory?,
    nawawiActive : Boolean,
    onCategory   : (HadithCategory?) -> Unit,
    onNawawi     : () -> Unit
) {
    LazyRow(
        contentPadding      = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // "الكل" chip
        item {
            FilterChip(
                selected = selected == null && !nawawiActive,
                onClick  = { onCategory(null) },
                label    = { Text(HadithCategory.ALL_LABEL, fontSize = 12.sp) },
                colors   = filterChipColors(selected == null && !nawawiActive)
            )
        }

        // "الأربعين النووية" chip
        item {
            FilterChip(
                selected = nawawiActive,
                onClick  = onNawawi,
                label    = { Text("📜 الأربعين النووية", fontSize = 12.sp) },
                colors   = filterChipColors(nawawiActive)
            )
        }

        // Category chips
        items(HadithCategory.values().toList()) { category ->
            FilterChip(
                selected = selected == category && !nawawiActive,
                onClick  = { onCategory(category) },
                label    = { Text("${category.emoji} ${category.label}", fontSize = 12.sp) },
                colors   = filterChipColors(selected == category && !nawawiActive)
            )
        }
    }
}

@Composable
private fun filterChipColors(isSelected: Boolean): SelectableChipColors =
    FilterChipDefaults.filterChipColors(
        selectedContainerColor   = Color(0xFF1A3A5C),
        selectedLabelColor       = Color(0xFF7EC8E3),
        containerColor           = Surface2,
        labelColor               = if (isSelected) Color(0xFF7EC8E3) else TextMuted,
        selectedLeadingIconColor = Color(0xFF7EC8E3),
        iconColor                = TextMuted
    )

// ── Hadith card ────────────────────────────────────────────────────────────

@Composable
private fun HadithCard(
    hadith       : Hadith,
    isBookmarked : Boolean,
    onBookmark   : () -> Unit,
    onCopy       : () -> Unit,
    modifier     : Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface2)
            .padding(16.dp)
    ) {
        // Top row: nawawi badge + grade + bookmark
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Grade badge
                GradeBadge(hadith.grade)

                // Nawawi badge
                hadith.nawawi?.let {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1A3A5C))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text      = "نووية #$it",
                            color     = Color(0xFF7EC8E3),
                            fontSize  = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Category badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1C2A1C))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text     = "${hadith.category.emoji} ${hadith.category.label}",
                        color    = Color(0xFF7EC8A0),
                        fontSize = 10.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "نسخ",
                        tint               = TextMuted,
                        modifier           = Modifier.size(15.dp)
                    )
                }
                IconButton(onClick = onBookmark, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "حفظ",
                        tint               = if (isBookmarked) Color(0xFF7EC8E3) else TextMuted,
                        modifier           = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Hadith text
        Text(
            text       = hadith.text,
            color      = TextPrimary,
            fontSize   = 15.sp,
            lineHeight = 27.sp,
            textAlign  = TextAlign.Right,
            style      = LocalTextStyle.current.copy(
                textDirection = TextDirection.Rtl
            )
        )

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = Divider, thickness = 0.5.dp)
        Spacer(Modifier.height(8.dp))

        // Narrator + source
        Text(
            text     = hadith.narrator,
            color    = TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text     = "${hadith.source}${hadith.bookNumber?.let { " ($it)" } ?: ""}",
            color    = TextMuted.copy(alpha = 0.6f),
            fontSize = 11.sp
        )

        // Benefit
        if (hadith.benefit.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text     = "💡 ${hadith.benefit}",
                color    = Color(0xFFFFD580),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun GradeBadge(grade: String) {
    val (bg, fg) = when (grade) {
        "صحيح"       -> Color(0xFF0F2B1C) to Color(0xFF4CAF50)
        "حسن صحيح"  -> Color(0xFF1A2B0F) to Color(0xFF8BC34A)
        "حسن"        -> Color(0xFF2B2000) to Color(0xFFFFB300)
        else          -> Color(0xFF1C1C1C) to TextMuted
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = grade, color = fg, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── Empty state ────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier              = modifier.fillMaxWidth(),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.Center
    ) {
        Text("🔍", fontSize = 40.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            text      = "لا توجد نتائج",
            color     = TextPrimary,
            fontSize  = 16.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text      = "جرّب كلمة بحث مختلفة",
            color     = TextMuted,
            fontSize  = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

// ── Clipboard helper ───────────────────────────────────────────────────────

private fun copyToClipboard(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("hadith", text))
}

// ── Previews ───────────────────────────────────────────────────────────────

@PreviewScreenSizes
@Composable
private fun IslamicScreenPreview() {
    val application = LocalContext.current.applicationContext as android.app.Application
    IslamicScreen(vm = IslamicViewModel(application))
}
