package com.obrynex.studyguard.ui.adaptive

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Adaptive layout helpers for responsive tablet/phone layouts.
 *
 * StudyGuard uses these utilities to provide multi-pane layouts on tablets
 * and foldables while keeping a single-column stack on phones.
 */

/** Returns true when the device is in a "two-pane" capable configuration. */
val WindowSizeClass.isTwoPaneCapable: Boolean
    get() = widthSizeClass != WindowWidthSizeClass.Compact

/**
 * Returns the appropriate content padding for a screen based on window size.
 * Tablets get more generous horizontal padding so content doesn't stretch
 * uncomfortably wide.
 */
val WindowSizeClass.contentHorizontalPadding: Dp
    get() = when (widthSizeClass) {
        WindowWidthSizeClass.Compact  -> 16.dp
        WindowWidthSizeClass.Medium   -> 24.dp
        WindowWidthSizeClass.Expanded -> 48.dp
        else -> 16.dp
    }

/**
 * Returns the max width fraction for chat bubbles or cards.
 * On phones: 0.75f (default)
 * On tablets: 0.55f so bubbles don't become too wide and hard to read.
 */
val WindowSizeClass.chatBubbleMaxWidthFraction: Float
    get() = when (widthSizeClass) {
        WindowWidthSizeClass.Compact  -> 0.75f
        WindowWidthSizeClass.Medium   -> 0.65f
        WindowWidthSizeClass.Expanded -> 0.55f
        else -> 0.75f
    }

/**
 * Returns whether the bottom navigation should be shown as a rail instead.
 * Material 3 recommends navigation rail on medium+ width when there are
 * many destinations (StudyGuard has 6 tabs).
 */
val WindowSizeClass.shouldUseNavigationRail: Boolean
    get() = widthSizeClass != WindowWidthSizeClass.Compact
