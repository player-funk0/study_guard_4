package com.obrynex.studyguard.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.obrynex.studyguard.BuildConfig
import com.obrynex.studyguard.LocalViewModelFactory
import com.obrynex.studyguard.booksummarizer.BookSummarizerScreen
import com.obrynex.studyguard.booksummarizer.BookSummarizerViewModel
import com.obrynex.studyguard.data.prefs.PrefsManager
import com.obrynex.studyguard.di.ServiceLocator
import com.obrynex.studyguard.islamic.ui.IslamicScreen
import com.obrynex.studyguard.islamic.ui.IslamicViewModel
import com.obrynex.studyguard.learningmaterials.LearningMaterialsScreen
import com.obrynex.studyguard.learningmaterials.LearningMaterialsViewModel
import com.obrynex.studyguard.summarizer.ui.SummarizerScreen
import com.obrynex.studyguard.summarizer.ui.SummarizerViewModel
import com.obrynex.studyguard.timer.TimerScreen
import com.obrynex.studyguard.timer.TimerViewModel
import com.obrynex.studyguard.tracker.SessionDetailScreen
import com.obrynex.studyguard.tracker.SessionDetailViewModel
import com.obrynex.studyguard.tracker.TrackerScreen
import com.obrynex.studyguard.tracker.TrackerViewModel
import com.obrynex.studyguard.ui.adaptive.shouldUseNavigationRail
import com.obrynex.studyguard.ui.adaptive.contentHorizontalPadding
import com.obrynex.studyguard.ui.onboarding.OnboardingScreen
import com.obrynex.studyguard.ui.theme.*
import com.obrynex.studyguard.wellbeing.WellbeingScreen
import com.obrynex.studyguard.wellbeing.WellbeingViewModel
import kotlinx.coroutines.launch

/* ─── Bottom nav items ──────────────────────────────────────────────────── */

enum class BottomNavItem(
    val route: String,
    val label: String,
    val compactLabel: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    TIMER(
        route = "timer",
        label = "المؤقت",
        compactLabel = "المؤقت",
        selectedIcon = Icons.Filled.Timer,
        unselectedIcon = Icons.Outlined.Timer
    ),
    LEARNING(
        route = "learning",
        label = "المواد",
        compactLabel = "تعلم",
        selectedIcon = Icons.Filled.MenuBook,
        unselectedIcon = Icons.Outlined.MenuBook
    ),
    SUMMARIZER(
        route = "summarizer",
        label = "الملخص",
        compactLabel = "ذكاء",
        selectedIcon = Icons.Filled.AutoStories,
        unselectedIcon = Icons.Outlined.Article
    ),
    TRACKER(
        route = "tracker",
        label = "التتبع",
        compactLabel = "تتبع",
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Filled.BarChart
    ),
    MORE(
        route = "more",
        label = "المزيد",
        compactLabel = "المزيد",
        selectedIcon = Icons.Filled.MoreHoriz,
        unselectedIcon = Icons.Outlined.MoreHoriz
    );

    companion object {
        val entriesList = listOf(TIMER, LEARNING, SUMMARIZER, TRACKER, MORE)
    }
}

/* ─── NavGraph ──────────────────────────────────────────────────────────── */

@Composable
fun NavGraph(windowSizeClass: androidx.compose.material3.windowsizeclass.WindowSizeClass) {
    val navCtrl = rememberNavController()
    val backStack by navCtrl.currentBackStackEntryAsState()
    val current = backStack?.destination
    val useRail = windowSizeClass.shouldUseNavigationRail
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val useCompactBottomLabels =
        windowSizeClass.widthSizeClass ==
            androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Compact &&
            screenWidthDp <= 360
    val showTopLevelNavigation = current == null || BottomNavItem.entriesList.any { item ->
        current.hierarchy.any { it.route == item.route }
    }

    CompositionLocalProvider(
        LocalViewModelFactory provides com.obrynex.studyguard.ViewModelFactory.fromApplication()
    ) {
        val onboardingScope = androidx.compose.runtime.rememberCoroutineScope()
        val appContext = LocalContext.current
        val onboardingDone by PrefsManager.onboardingDone(appContext)
            .collectAsStateWithLifecycle(initialValue = false)

        if (!onboardingDone) {
            OnboardingScreen(
                onComplete = {
                    onboardingScope.launch {
                        PrefsManager.setOnboardingDone(appContext)
                    }
                }
            )
            return@CompositionLocalProvider
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = BgDark,
            bottomBar = {
                if (!useRail && showTopLevelNavigation) {
                    Column {
                        // Gradient glow line above bottom nav
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(
                                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        listOf(
                                            androidx.compose.ui.graphics.Color.Transparent,
                                            Divider,
                                            AccentGreen.copy(alpha = 0.2f),
                                            Divider,
                                            androidx.compose.ui.graphics.Color.Transparent
                                        )
                                    )
                                )
                        )
                        NavigationBar(
                            containerColor = Surface0,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            BottomNavItem.entriesList.forEach { item ->
                                val selected = current?.hierarchy?.any { it.route == item.route } == true

                                NavigationBarItem(
                                    icon = {
                                        Icon(
                                            if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            if (useCompactBottomLabels) item.compactLabel else item.label,
                                            fontSize = if (useCompactBottomLabels) 9.sp else 10.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    alwaysShowLabel = !useCompactBottomLabels,
                                    selected = selected,
                                    onClick = {
                                        navCtrl.navigate(item.route) {
                                            popUpTo(navCtrl.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = AccentGreen,
                                        selectedTextColor = AccentGreen,
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted,
                                        indicatorColor = AccentGreen.copy(alpha = 0.10f)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail && showTopLevelNavigation) {
                    NavigationRail(
                        containerColor = Surface0,
                        windowInsets = WindowInsets.systemBars.only(
                            WindowInsetsSides.Start + WindowInsetsSides.Vertical
                        )
                    ) {
                        Spacer(Modifier.height(12.dp))
                        BottomNavItem.entriesList.forEach { item ->
                            val selected = current?.hierarchy?.any { it.route == item.route } == true

                            NavigationRailItem(
                                icon = {
                                    Icon(
                                        if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = {
                                    Text(
                                        item.label,
                                        fontSize = 10.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                selected = selected,
                                onClick = {
                                    navCtrl.navigate(item.route) {
                                        popUpTo(navCtrl.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = AccentGreen,
                                    selectedTextColor = AccentGreen,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted,
                                    indicatorColor = AccentGreen.copy(alpha = 0.10f)
                                )
                            )
                        }
                    }
                }

                NavHost(
                    navController = navCtrl,
                    startDestination = BottomNavItem.TIMER.route,
                    modifier = Modifier.weight(1f),
                    enterTransition = {
                        fadeIn(
                            animationSpec = tween(
                                durationMillis = 350,
                                easing = FastOutSlowInEasing
                            )
                        ) + slideInHorizontally(
                            animationSpec = tween(
                                durationMillis = 400,
                                easing = FastOutSlowInEasing
                            )
                        ) { it / 6 }
                    },
                    exitTransition = {
                        fadeOut(
                            animationSpec = tween(
                                durationMillis = 250,
                                easing = FastOutSlowInEasing
                            )
                        ) + slideOutHorizontally(
                            animationSpec = tween(
                                durationMillis = 400,
                                easing = FastOutSlowInEasing
                            )
                        ) { -it / 6 }
                    },
                    popEnterTransition = {
                        fadeIn(
                            animationSpec = tween(
                                durationMillis = 350,
                                easing = FastOutSlowInEasing
                            )
                        ) + slideInHorizontally(
                            animationSpec = tween(
                                durationMillis = 400,
                                easing = FastOutSlowInEasing
                            )
                        ) { -it / 6 }
                    },
                    popExitTransition = {
                        fadeOut(
                            animationSpec = tween(
                                durationMillis = 250,
                                easing = FastOutSlowInEasing
                            )
                        ) + slideOutHorizontally(
                            animationSpec = tween(
                                durationMillis = 400,
                                easing = FastOutSlowInEasing
                            )
                        ) { it / 6 }
                    }
                ) {
                    // ── Timer ──────────────────────────────────────────────
                    composable(BottomNavItem.TIMER.route) {
                        val vm: TimerViewModel = viewModel(factory = LocalViewModelFactory.current)
                        TimerScreen(
                            vm = vm,
                            onNavigateToTracker = {
                                navCtrl.navigate(BottomNavItem.TRACKER.route) {
                                    popUpTo(navCtrl.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }

                    // ── Learning Materials ─────────────────────────────────
                    composable(BottomNavItem.LEARNING.route) {
                        val vm: LearningMaterialsViewModel = viewModel(factory = LocalViewModelFactory.current)
                        LearningMaterialsScreen(vm = vm, windowSizeClass = windowSizeClass)
                    }

                    // ── Summarizer ─────────────────────────────────────────
                    composable(BottomNavItem.SUMMARIZER.route) {
                        val vm: SummarizerViewModel = viewModel(factory = LocalViewModelFactory.current)
                        SummarizerScreen(vm = vm, windowSizeClass = windowSizeClass)
                    }

                    // ── Tracker ────────────────────────────────────────────
                    composable(BottomNavItem.TRACKER.route) {
                        val vm: TrackerViewModel = viewModel(factory = LocalViewModelFactory.current)
                        TrackerScreen(
                            vm = vm,
                            windowSizeClass = windowSizeClass,
                            onNavigateToDetail = { id ->
                                navCtrl.navigate("session/$id")
                            },
                            onNavigateToAI = {
                                navCtrl.navigate("aitutor") { launchSingleTop = true }
                            },
                            onNavigateToHadith = {
                                navCtrl.navigate("hadith") { launchSingleTop = true }
                            },
                            onNavigateToBookSummarizer = {
                                navCtrl.navigate("booksummarizer") { launchSingleTop = true }
                            },
                            onNavigateToWellbeing = {
                                navCtrl.navigate("wellbeing") { launchSingleTop = true }
                            },
                            onNavigateToDebug = {
                                navCtrl.navigate("debug") { launchSingleTop = true }
                            }
                        )
                    }

                    // ── More ───────────────────────────────────────────────
                    composable(BottomNavItem.MORE.route) {
                        MoreScreen(
                            onNavigateToAI = {
                                navCtrl.navigate("aitutor") { launchSingleTop = true }
                            },
                            onNavigateToHadith = {
                                navCtrl.navigate("hadith") { launchSingleTop = true }
                            },
                            onNavigateToBookSummarizer = {
                                navCtrl.navigate("booksummarizer") { launchSingleTop = true }
                            },
                            onNavigateToWellbeing = {
                                navCtrl.navigate("wellbeing") { launchSingleTop = true }
                            },
                            onNavigateToDebug = {
                                navCtrl.navigate("debug") { launchSingleTop = true }
                            },
                            windowSizeClass = windowSizeClass
                        )
                    }

                    // ── AI Tutor ───────────────────────────────────────────
                    composable("aitutor") {
                        val vm: com.obrynex.studyguard.aitutor.AiTutorViewModel =
                            viewModel(factory = LocalViewModelFactory.current)
                        com.obrynex.studyguard.aitutor.AiTutorScreen(
                            vm = vm,
                            windowSizeClass = windowSizeClass
                        )
                    }

                    // ── Session Detail ─────────────────────────────────────
                    composable("session/{sessionId}") { entry ->
                        val id = entry.arguments?.getString("sessionId")?.toLongOrNull() ?: return@composable
                        val vm: SessionDetailViewModel = viewModel(factory = LocalViewModelFactory.current)
                        val session by vm.sessionById(id).collectAsStateWithLifecycle(initialValue = null)
                        session?.let { sess ->
                            SessionDetailScreen(
                                session = sess,
                                onBack = { navCtrl.popBackStack() },
                                onDelete = {
                                    vm.delete(sess)
                                    navCtrl.popBackStack()
                                }
                            )
                        }
                    }

                    // ── Hadith ─────────────────────────────────────────────
                    composable("hadith") {
                        val vm: IslamicViewModel = viewModel(factory = LocalViewModelFactory.current)
                        IslamicScreen(vm = vm, onBack = { navCtrl.popBackStack() })
                    }

                    // ── Book Summarizer ────────────────────────────────────
                    composable("booksummarizer") {
                        val vm: BookSummarizerViewModel = viewModel(factory = LocalViewModelFactory.current)
                        BookSummarizerScreen(vm = vm, onBack = { navCtrl.popBackStack() })
                    }

                    // ── Digital Wellbeing ──────────────────────────────────
                    composable("wellbeing") {
                        val vm: WellbeingViewModel = viewModel(factory = LocalViewModelFactory.current)
                        WellbeingScreen(
                            vm = vm,
                            windowSizeClass = windowSizeClass,
                            onBack = { navCtrl.popBackStack() }
                        )
                    }

                    // ── Debug ──────────────────────────────────────────────
                    composable("debug") {
                        val vm: com.obrynex.studyguard.debug.DebugInfoViewModel =
                            viewModel(factory = LocalViewModelFactory.current)
                        com.obrynex.studyguard.debug.DebugInfoScreen(vm = vm)
                    }
                }
            }
        }
    }
}

/* ─── More Screen ──────────────────────────────────────────────────────── */

@Composable
private fun MoreScreen(
    onNavigateToAI: () -> Unit,
    onNavigateToHadith: () -> Unit,
    onNavigateToBookSummarizer: () -> Unit,
    onNavigateToWellbeing: () -> Unit,
    onNavigateToDebug: () -> Unit,
    windowSizeClass: androidx.compose.material3.windowsizeclass.WindowSizeClass? = null
) {
    val hPad = windowSizeClass?.contentHorizontalPadding ?: 16.dp
    val isExpanded = windowSizeClass?.widthSizeClass ==
        androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Expanded

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .then(
                    if (isExpanded) Modifier.widthIn(max = 600.dp)
                    else Modifier.fillMaxWidth()
                )
                .padding(horizontal = hPad, vertical = 0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        Text(
            "المزيد",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
        )

        // Gradient divider
        Box(
            Modifier.fillMaxWidth().height(1.dp).background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(
                        androidx.compose.ui.graphics.Color.Transparent,
                        Divider,
                        AccentGreen.copy(alpha = 0.3f),
                        Divider,
                        androidx.compose.ui.graphics.Color.Transparent
                    )
                )
            )
        )

        Spacer(Modifier.height(8.dp))

        MoreItem(
            icon = Icons.Default.Psychology,
            title = "المدرس الذكي",
            subtitle = "اسأل الذكاء الاصطناعي عن أي مادة",
            accentColor = AccentPurple,
            onClick = onNavigateToAI
        )
        MoreItem(
            icon = Icons.Default.Star,
            title = "حديث اليوم",
            subtitle = "أحاديث ملهمة",
            accentColor = AccentAmber,
            onClick = onNavigateToHadith
        )
        MoreItem(
            icon = Icons.Default.Book,
            title = "ملخص الكتب",
            subtitle = "استيراد وتلخيص النصوص الطويلة",
            accentColor = AccentBlue,
            onClick = onNavigateToBookSummarizer
        )
        MoreItem(
            icon = Icons.Default.PhoneAndroid,
            title = "الصحة الرقمية",
            subtitle = "مراقبة وقت الشاشة",
            accentColor = AccentGreen,
            onClick = onNavigateToWellbeing
        )
        if (BuildConfig.DEBUG) {
            MoreItem(
                icon = Icons.Default.BugReport,
                title = "معلومات التشخيص",
                subtitle = "تشخيص نموذج الذكاء الاصطناعي",
                accentColor = AccentRed,
                onClick = onNavigateToDebug
            )
        }
        } // end Column
    } // end Box
}

@Composable
private fun MoreItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: androidx.compose.ui.graphics.Color = AccentGreen,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface2)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(accentColor.copy(alpha = 0.12f), accentColor.copy(alpha = 0.05f))
                    )
                )
                .then(
                    Modifier.border(0.5.dp, accentColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = accentColor, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextMuted, fontSize = 12.sp)
        }
        Icon(
            Icons.Default.ChevronRight,
            null,
            tint = TextMuted.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp)
        )
    }
}

