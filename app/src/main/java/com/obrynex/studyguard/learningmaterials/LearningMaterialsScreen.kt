package com.obrynex.studyguard.learningmaterials

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.obrynex.studyguard.R
import com.obrynex.studyguard.ui.adaptive.contentHorizontalPadding
import com.obrynex.studyguard.ui.theme.*

@Composable
fun LearningMaterialsScreen(
    vm: LearningMaterialsViewModel,
    windowSizeClass: WindowSizeClass? = null,
    onStudyModeClick: (LearningMaterial) -> Unit = {}
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val filteredMaterials = remember(state.materials, state.selectedCategory, state.searchQuery) {
        vm.getFilteredMaterials()
    }
    val hPad = windowSizeClass?.contentHorizontalPadding ?: 16.dp
    val isTwoPaneCapable = windowSizeClass?.widthSizeClass != WindowWidthSizeClass.Compact

    Scaffold(
        containerColor = BgDark,
        floatingActionButton = {
            FloatingActionButton(
                onClick = vm::showAddDialog,
                containerColor = AccentGreen,
                contentColor = BgDark,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 2.dp
                )
            ) {
                Icon(Icons.Default.Add, stringResource(R.string.add_material))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDark)
                .padding(padding)
        ) {
            // ── Header ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = hPad, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(R.string.learning_materials),
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        stringResource(R.string.materials_count, state.materials.size),
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentGreen.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MenuBook, null, tint = AccentGreen, modifier = Modifier.size(22.dp))
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

            // ── Search bar ──────────────────────────────────────────
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = vm::onSearchQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = hPad, vertical = 12.dp),
                placeholder = { Text(stringResource(R.string.search_materials), color = TextMuted.copy(alpha = 0.5f)) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { vm.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Close, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentGreen,
                    unfocusedBorderColor = Divider,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = Surface2,
                    unfocusedContainerColor = Surface2
                )
            )

            // ── Category chips ──────────────────────────────────────
            if (state.categories.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = hPad),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        PremiumFilterChip(
                            label = stringResource(R.string.all),
                            selected = state.selectedCategory == null,
                            onClick = { vm.onCategorySelected(null) }
                        )
                    }
                    items(state.categories) { category ->
                        PremiumFilterChip(
                            label = category,
                            selected = state.selectedCategory == category,
                            onClick = { vm.onCategorySelected(category) }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // ── Materials list ──────────────────────────────────────
            if (filteredMaterials.isEmpty()) {
                EmptyMaterialsState(state.searchQuery.isNotEmpty() || state.selectedCategory != null)
            } else if (isTwoPaneCapable) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = hPad, end = hPad, top = 8.dp, bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    gridItems(filteredMaterials, key = { it.id }) { material ->
                        PremiumMaterialCard(
                            material = material,
                            vm = vm,
                            onStudyModeClick = onStudyModeClick,
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(300, easing = FastOutSlowInEasing),
                                fadeOutSpec = tween(200, easing = FastOutSlowInEasing),
                                placementSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = hPad, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredMaterials, key = { it.id }) { material ->
                        PremiumMaterialCard(
                            material = material,
                            vm = vm,
                            onStudyModeClick = onStudyModeClick,
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(300, easing = FastOutSlowInEasing),
                                fadeOutSpec = tween(200, easing = FastOutSlowInEasing),
                                placementSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        )
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    // Dialogs
    if (state.showAddDialog) {
        MaterialDialog(
            title = stringResource(R.string.add_learning_material),
            titleValue = state.newTitle,
            contentValue = state.newContent,
            categoryValue = state.newCategory,
            onTitleChange = vm::onNewTitleChanged,
            onContentChange = vm::onNewContentChanged,
            onCategoryChange = vm::onNewCategoryChanged,
            onConfirm = vm::addMaterial,
            onDismiss = vm::dismissAddDialog,
            confirmLabel = stringResource(R.string.add)
        )
    }
    if (state.showEditDialog) {
        MaterialDialog(
            title = stringResource(R.string.edit_material),
            titleValue = state.newTitle,
            contentValue = state.newContent,
            categoryValue = state.newCategory,
            onTitleChange = vm::onNewTitleChanged,
            onContentChange = vm::onNewContentChanged,
            onCategoryChange = vm::onNewCategoryChanged,
            onConfirm = vm::updateMaterial,
            onDismiss = vm::dismissEditDialog,
            confirmLabel = stringResource(R.string.save)
        )
    }
    if (state.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = vm::dismissDeleteDialog,
            title = { Text(stringResource(R.string.delete_material_question), color = TextPrimary, fontWeight = FontWeight.SemiBold) },
            text = { Text(stringResource(R.string.material_delete_confirm, state.selectedMaterial?.title ?: ""), color = TextMuted) },
            confirmButton = {
                Button(
                    onClick = vm::confirmDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.delete), color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = vm::dismissDeleteDialog, shape = RoundedCornerShape(10.dp)) {
                    Text(stringResource(R.string.cancel), color = TextMuted)
                }
            },
            containerColor = Surface2,
            shape = RoundedCornerShape(20.dp)
        )
    }
    if (state.error != null) {
        LaunchedEffect(state.error) {
            kotlinx.coroutines.delay(3000)
            vm.clearError()
        }
    }
}

@Composable
private fun PremiumMaterialCard(
    material: LearningMaterial,
    vm: LearningMaterialsViewModel,
    onStudyModeClick: (LearningMaterial) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(0.5.dp, Divider, RoundedCornerShape(16.dp))
            .background(Surface2)
    ) {
        // Card header with accent gradient
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(AccentGreen.copy(alpha = 0.06f), Color.Transparent)
                    )
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AccentGreen.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Book, null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                }
                Column {
                    Text(
                        material.title,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (material.category.isNotBlank()) {
                        Text(material.category, color = AccentGreen.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                }
            }
            Row {
                IconButton(onClick = {
                    vm.markAccessed(material)
                    onStudyModeClick(material)
                }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Outlined.Book, stringResource(R.string.study), tint = AccentBlue, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { vm.showEditDialog(material) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Edit, stringResource(R.string.edit), tint = TextMuted, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { vm.showDeleteDialog(material) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = AccentRed.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                }
            }
        }

        // Content preview
        if (material.content.isNotBlank()) {
            SelectionContainer {
                Text(
                    material.content,
                    color = TextMuted,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun PremiumFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
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

@Composable
private fun EmptyMaterialsState(isFiltered: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Surface3),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MenuBook, null, tint = TextMuted, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (isFiltered) stringResource(R.string.no_matching_materials) else stringResource(R.string.no_learning_materials_yet),
            color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (isFiltered) stringResource(R.string.try_different_search) else stringResource(R.string.tap_add_first_material),
            color = TextMuted, fontSize = 13.sp
        )
    }
}

@Composable
private fun MaterialDialog(
    title: String, titleValue: String, contentValue: String, categoryValue: String,
    onTitleChange: (String) -> Unit, onContentChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit, onConfirm: () -> Unit,
    onDismiss: () -> Unit, confirmLabel: String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = titleValue, onValueChange = onTitleChange,
                    label = { Text(stringResource(R.string.title_required), color = TextMuted) },
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentGreen, unfocusedBorderColor = Divider,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )
                OutlinedTextField(
                    value = categoryValue, onValueChange = onCategoryChange,
                    label = { Text(stringResource(R.string.category_optional), color = TextMuted) },
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentGreen, unfocusedBorderColor = Divider,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )
                OutlinedTextField(
                    value = contentValue, onValueChange = onContentChange,
                    label = { Text(stringResource(R.string.content), color = TextMuted) },
                    modifier = Modifier.heightIn(min = 100.dp, max = 200.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentGreen, unfocusedBorderColor = Divider,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(confirmLabel, color = BgDark, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                Text(stringResource(R.string.cancel), color = TextMuted)
            }
        },
        containerColor = Surface2,
        shape = RoundedCornerShape(20.dp)
    )
}
