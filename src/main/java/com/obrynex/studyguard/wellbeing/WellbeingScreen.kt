package com.obrynex.studyguard.wellbeing

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.obrynex.studyguard.R
import com.obrynex.studyguard.ui.adaptive.contentHorizontalPadding
import com.obrynex.studyguard.ui.theme.*

@Composable
fun WellbeingScreen(
    vm: WellbeingViewModel,
    windowSizeClass: WindowSizeClass? = null,
    onBack: (() -> Unit)? = null
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val hPad = windowSizeClass?.contentHorizontalPadding ?: 16.dp
    val isExpanded = windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Expanded

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // ── Header ──────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = hPad, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.back), tint = TextPrimary)
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Column {
                    Text(
                        stringResource(R.string.digital_wellbeing),
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        stringResource(R.string.monitor_screen_time),
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
            FilledTonalIconButton(
                onClick = vm::refresh,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = AccentBlue.copy(alpha = 0.12f),
                    contentColor = AccentBlue
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.PhoneAndroid, stringResource(R.string.refresh), modifier = Modifier.size(20.dp))
            }
        }

        // Gradient divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Divider, AccentBlue.copy(alpha = 0.3f), Divider, Color.Transparent)
                    )
                )
        )

        if (!state.hasPermission) {
            PremiumPermissionCard(onGrant = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) })
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .then(if (isExpanded) Modifier.widthIn(max = 700.dp) else Modifier.fillMaxWidth())
                ) {
                    // Stat card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = hPad, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PremiumStatCard(
                            title = stringResource(R.string.today),
                            value = WellbeingViewModel.formatDuration(state.todayScreenTimeMs),
                            accentColor = AccentBlue,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Section header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = hPad, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.most_used_apps),
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.3).sp
                        )
                        if (state.topApps.isNotEmpty()) {
                            Text(
                                "${state.topApps.size}",
                                color = TextMuted,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Surface3)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = hPad, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.topApps) { app ->
                            PremiumAppUsageCard(
                                app = app,
                                maxUsage = state.topApps.maxOfOrNull { it.usageTimeMs } ?: 1L
                            )
                        }
                        if (state.topApps.isEmpty()) {
                            item { PremiumEmptyWellbeingState() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumPermissionCard(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(0.5.dp, Divider, RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Surface2, Surface0)
                )
            )
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AccentBlue.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Lock, null, tint = AccentBlue, modifier = Modifier.size(28.dp))
        }
        Text(
            stringResource(R.string.permission_required),
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.3).sp
        )
        Text(
            stringResource(R.string.permission_description),
            color = TextMuted,
            fontSize = 13.sp,
            lineHeight = 20.sp
        )
        Button(
            onClick = onGrant,
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
        ) {
            Text(stringResource(R.string.grant_permission), color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PremiumStatCard(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(0.5.dp, accentColor.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(accentColor.copy(alpha = 0.06f), Surface2)
                )
            )
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Timer, null, tint = accentColor, modifier = Modifier.size(18.dp))
        }
        Text(value, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
        Text(title, color = TextMuted, fontSize = 11.sp, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun PremiumAppUsageCard(app: AppUsageInfo, maxUsage: Long) {
    val proportion = if (maxUsage > 0) (app.usageTimeMs.toFloat() / maxUsage).coerceIn(0f, 1f) else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface2)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Gradient avatar
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(AccentBlue.copy(alpha = 0.15f), Surface3)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    app.appName.firstOrNull()?.uppercase() ?: "?",
                    color = AccentBlue,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                // Usage progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Surface3)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(proportion)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(AccentBlue, AccentGreen)
                                )
                            )
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            WellbeingViewModel.formatDuration(app.usageTimeMs),
            color = AccentBlue,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PremiumEmptyWellbeingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Surface3),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PhoneAndroid, null, tint = TextMuted, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.no_app_usage_data), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.usage_data_will_appear), color = TextMuted, fontSize = 12.sp)
    }
}
