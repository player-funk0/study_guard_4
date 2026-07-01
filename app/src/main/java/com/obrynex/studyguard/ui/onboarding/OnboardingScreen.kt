package com.obrynex.studyguard.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obrynex.studyguard.R
import com.obrynex.studyguard.ui.theme.*

/**
 * Onboarding screen shown on first app launch — premium design with
 * gradient backgrounds, animated icon container, and spring-animated indicators.
 */
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var currentPage by remember { mutableStateOf(0) }
    val totalPages = 4

    // Page-specific accent colors
    val pageColors = listOf(AccentGreen, AccentBlue, AccentAmber, Color(0xFFE879F9))

    val currentColor by animateColorAsState(
        targetValue = pageColors.getOrElse(currentPage) { AccentGreen },
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "pageColor"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(BgDark, Surface0, BgDark)
                )
            )
    ) {
        // Subtle animated glow behind the icon
        val infiniteTransition = rememberInfiniteTransition(label = "glow")
        val glowRadius by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.6f,
            animationSpec = infiniteRepeatable(
                animation = tween(3500, easing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowRad"
        )

        // Background glow circle
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.Center)
                .offset(y = (-80).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            currentColor.copy(alpha = 0.04f * glowRadius * 3),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.weight(0.3f))

            // Page content
            AnimatedContent(
                targetState = currentPage,
                label = "onboarding_page",
                transitionSpec = {
                    val slideEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
                    (fadeIn(
                        animationSpec = tween(400, easing = FastOutSlowInEasing)
                    ) + slideInHorizontally(
                        animationSpec = tween(500, easing = slideEasing)
                    ) { it / 4 }) togetherWith
                    (fadeOut(
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + slideOutHorizontally(
                        animationSpec = tween(500, easing = slideEasing)
                    ) { -it / 4 })
                }
            ) { page ->
                val (icon, titleRes, descRes) = when (page) {
                    0 -> Triple(Icons.Default.Timer, R.string.onboarding_study_timer_title, R.string.onboarding_study_timer_desc)
                    1 -> Triple(Icons.Default.Psychology, R.string.onboarding_ai_tutor_title, R.string.onboarding_ai_tutor_desc)
                    2 -> Triple(Icons.Default.AutoStories, R.string.onboarding_book_summarizer_title, R.string.onboarding_book_summarizer_desc)
                    3 -> Triple(Icons.Default.PhoneAndroid, R.string.onboarding_wellbeing_title, R.string.onboarding_wellbeing_desc)
                    else -> Triple(Icons.Default.Timer, R.string.welcome, R.string.lets_get_started)
                }
                PremiumOnboardingPage(
                    icon = icon,
                    accentColor = pageColors.getOrElse(page) { AccentGreen },
                    title = stringResource(titleRes),
                    description = stringResource(descRes)
                )
            }

            Spacer(Modifier.weight(0.3f))

            // ── Animated page indicators ─────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(totalPages) { index ->
                    val isActive = index == currentPage
                    val width by animateDpAsState(
                        targetValue = if (isActive) 28.dp else 8.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "indicator"
                    )
                    val color by animateColorAsState(
                        targetValue = if (isActive) currentColor else Surface3,
                        animationSpec = tween(400, easing = FastOutSlowInEasing),
                        label = "indicatorColor"
                    )
                    Box(
                        modifier = Modifier
                            .width(width)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color)
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            // ── Gradient navigation button ──────────────────────────
            Button(
                onClick = {
                    if (currentPage < totalPages - 1) {
                        currentPage++
                    } else {
                        onComplete()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = currentColor),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 2.dp
                )
            ) {
                Text(
                    if (currentPage < totalPages - 1) stringResource(R.string.next)
                    else stringResource(R.string.get_started),
                    color = BgDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PremiumOnboardingPage(
    icon: ImageVector,
    accentColor: Color,
    title: String,
    description: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Icon container with gradient + border glow
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(28.dp))
                .border(
                    width = 1.dp,
                    color = accentColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(28.dp)
                )
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accentColor.copy(alpha = 0.12f),
                            accentColor.copy(alpha = 0.04f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(44.dp)
            )
        }

        Text(
            title,
            color = TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            letterSpacing = (-0.5).sp
        )

        Text(
            description,
            color = TextMuted,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

private val EaseInOutCubic = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
