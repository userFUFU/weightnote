package com.weightnote.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.weightnote.ui.chart.ChartScreen
import com.weightnote.ui.home.HomeScreen
import com.weightnote.ui.list.RecordsScreen
import com.weightnote.ui.profile.OnboardingScreen
import com.weightnote.ui.profile.ProfileEditScreen
import com.weightnote.ui.profile.ProfilesScreen
import com.weightnote.ui.record.EntryMode
import com.weightnote.ui.record.EntrySheet
import com.weightnote.ui.record.MeasureEntryScreen
import com.weightnote.ui.settings.BackupScreen
import com.weightnote.ui.settings.GroupEditScreen
import com.weightnote.ui.settings.GroupsScreen
import com.weightnote.ui.settings.MetricsScreen
import com.weightnote.ui.settings.SettingsScreen
import com.weightnote.ui.theme.WeightNoteTheme
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val CHART = "chart"
    const val RECORDS = "records"
    const val SETTINGS = "settings"
    const val MEASURE = "measure"
    const val PROFILES = "profiles"
    const val PROFILE = "profile/{id}"
    const val GROUPS = "groups"
    const val GROUP = "group/{id}"
    const val METRICS = "metrics"
    const val BACKUP = "backup"

    fun profile(id: Long) = "profile/$id"
    fun group(id: Long) = "group/$id"
}

private data class TopLevel(val route: String, val label: String, val icon: ImageVector)

private val topLevels = listOf(
    TopLevel(Routes.HOME, "首页", Icons.Outlined.Home),
    TopLevel(Routes.CHART, "趋势", Icons.AutoMirrored.Outlined.ShowChart),
    TopLevel(Routes.RECORDS, "记录", Icons.AutoMirrored.Outlined.List),
    TopLevel(Routes.SETTINGS, "设置", Icons.Outlined.Settings),
)

@Composable
fun AppRoot(vm: MainViewModel) {
    val theme by vm.themeMode.collectAsStateWithLifecycle()
    val state by vm.uiState.collectAsStateWithLifecycle()
    WeightNoteTheme(theme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (val s = state) {
                MainUiState.Loading -> Box(Modifier.fillMaxSize())
                MainUiState.Onboarding -> OnboardingScreen(onCreate = { vm.createProfile(it) })
                is MainUiState.Ready -> MainScaffold(s.session, vm)
            }
        }
    }
}

@Composable
private fun MainScaffold(session: Session, vm: MainViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    var entryMode by remember { mutableStateOf<EntryMode?>(null) }
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val entryRequest by vm.entryRequest.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            snackbar.currentSnackbarData?.dismiss()
            launch {
                val result = snackbar.showSnackbar(
                    message = msg.text,
                    actionLabel = msg.actionLabel,
                    duration = if (msg.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) msg.action?.invoke()
            }
        }
    }

    // 从提醒通知进入：切到对应身份后打开记录面板
    LaunchedEffect(entryRequest, session.profile.id) {
        val req = entryRequest ?: return@LaunchedEffect
        if (session.groupById.containsKey(req.groupId)) {
            entryMode = EntryMode.NewWeight(req.groupId)
            vm.entryRequest.value = null
        }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = topLevels.any { it.route == currentRoute }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevels.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = { nav.navigateTopLevel(item.route) },
                            icon = { Icon(item.icon, null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier
                .padding(inner)
                .consumeWindowInsets(inner),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    session = session,
                    vm = vm,
                    onRecordWeight = { entryMode = EntryMode.NewWeight(it) },
                    onRecordMeasure = { nav.navigate(Routes.MEASURE) },
                    onOpenChart = { nav.navigateTopLevel(Routes.CHART) },
                    onManageProfiles = { nav.navigate(Routes.PROFILES) },
                )
            }
            composable(Routes.CHART) { ChartScreen(session) }
            composable(Routes.RECORDS) {
                RecordsScreen(session, vm, onEdit = { entryMode = EntryMode.Edit(it) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    session = session,
                    themeMode = themeMode,
                    onThemeChange = { vm.setTheme(it) },
                    onEditProfile = { nav.navigate(Routes.profile(it)) },
                    onProfiles = { nav.navigate(Routes.PROFILES) },
                    onGroups = { nav.navigate(Routes.GROUPS) },
                    onMetrics = { nav.navigate(Routes.METRICS) },
                    onBackup = { nav.navigate(Routes.BACKUP) },
                )
            }
            composable(Routes.MEASURE) {
                MeasureEntryScreen(session, vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.PROFILES) {
                ProfilesScreen(
                    session = session,
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.profile(it)) },
                )
            }
            composable(Routes.PROFILE, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                ProfileEditScreen(
                    session = session,
                    profileId = entry.arguments?.getLong("id") ?: 0L,
                    vm = vm,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.GROUPS) {
                GroupsScreen(
                    session = session,
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onEditGroup = { nav.navigate(Routes.group(it)) },
                )
            }
            composable(Routes.GROUP, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                GroupEditScreen(
                    session = session,
                    groupId = entry.arguments?.getLong("id") ?: 0L,
                    vm = vm,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.METRICS) {
                MetricsScreen(session, vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.BACKUP) {
                BackupScreen(session, vm, onBack = { nav.popBackStack() })
            }
        }
    }

    entryMode?.let { mode ->
        key(mode) {
            EntrySheet(session = session, mode = mode, vm = vm, onDismiss = { entryMode = null })
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
