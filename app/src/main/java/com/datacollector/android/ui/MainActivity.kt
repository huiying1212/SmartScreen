package com.datacollector.android.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.datacollector.android.ui.theme.NavPill
import com.datacollector.android.ui.theme.PaperWhite
import com.datacollector.android.data.config.AppPreferences
import com.datacollector.android.services.DataCollectionService
import com.datacollector.android.services.FloatingOverlayService
import com.datacollector.android.utils.CollectionConfig
import com.datacollector.android.ui.history.HistoryScreen
import com.datacollector.android.ui.home.HomeScreen
import com.datacollector.android.ui.onboarding.OnboardingScreen
import com.datacollector.android.ui.permissions.DataPermissionsScreen
import com.datacollector.android.ui.reflect.ReflectScreen
import com.datacollector.android.ui.settings.SettingsScreen
import com.datacollector.android.ui.theme.RI4SUTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Single Activity host for the new Compose UI. Handles permission
 * requests and bridges to the existing Java services.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefs: AppPreferences

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored — UI prompts again next launch if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ensureNotificationPermission()
        setContent {
            RI4SUTheme {
                AppRoot(onRequestOverlay = ::requestOverlayPermission)
            }
        }
        startBackgroundServicesIfEnabled()
    }

    override fun onResume() {
        super.onResume()
        // Retry on resume in case the user granted SYSTEM_ALERT_WINDOW from the
        // OS settings page without the activity being recreated — otherwise the
        // floating face never appears until the next cold start.
        startBackgroundServicesIfEnabled()
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED) return
        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) return
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        startActivity(intent)
    }

    private fun startBackgroundServicesIfEnabled() {
        // Honour the user's "global service" toggle — otherwise services are
        // started on every launch only to immediately stopSelf() in their
        // onCreate, which is wasteful and can briefly flash a notification.
        val cfg = CollectionConfig.getInstance(this)
        if (!cfg.getBoolean(CollectionConfig.KEY_RI4SU_ENABLED, true)) return

        try {
            val intent = Intent(this, DataCollectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
        } catch (_: Throwable) { /* no-op */ }

        if (!cfg.getBoolean(CollectionConfig.KEY_OVERLAY_ENABLED, true)) return
        try {
            if (Settings.canDrawOverlays(this)) {
                val intent = Intent(this, FloatingOverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
                else startService(intent)
            }
        } catch (_: Throwable) { /* no-op */ }
    }
}

private object Routes {
    const val Onboarding = "onboarding"
    const val Home = "home"
    const val Reflect = "reflect"
    const val History = "history"
    const val Settings = "settings"
    const val DataPermissions = "data_permissions"
}

@Composable
private fun AppRoot(
    onRequestOverlay: () -> Unit,
) {
    val rootVm: AppRootViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val prefs = rootVm.prefs
    val navController = rememberNavController()
    val onboardingDone by prefs.onboardingCompleted.collectAsStateWithLifecycle(initialValue = true)

    LaunchedEffect(onboardingDone) {
        // We let initial value be true to avoid an onboarding flash on rotate;
        // re-read once and navigate if user actually hasn't completed.
        val real = prefs.onboardingCompleted.first()
        val current = navController.currentBackStackEntry?.destination?.route
        when {
            !real && current != Routes.Onboarding -> navController.navigate(Routes.Onboarding) {
                popUpTo(0) { inclusive = true }
            }
            real && current == Routes.Onboarding -> navController.navigate(Routes.Home) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in listOf(Routes.Home, Routes.Reflect, Routes.History, Routes.Settings)

    val navigate: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Box overlay: NavHost fills the whole screen, FloatingNavBar sits on top
    // with no background behind it so content scrolls visibly underneath.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.Home,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Routes.Onboarding) {
                OnboardingScreen(onFinished = {
                    onRequestOverlay()
                    navController.navigate(Routes.Home) {
                        popUpTo(Routes.Onboarding) { inclusive = true }
                    }
                })
            }
            composable(Routes.Home) { HomeScreen() }
            composable(Routes.Reflect) { ReflectScreen() }
            composable(Routes.History) { HistoryScreen() }
            composable(Routes.Settings) {
                SettingsScreen(
                    onNavigateToPermissions = {
                        navController.navigate(Routes.DataPermissions)
                    },
                )
            }
            composable(Routes.DataPermissions) {
                DataPermissionsScreen(onBack = { navController.popBackStack() })
            }
        }

        if (showBottomBar) {
            FloatingNavBar(
                currentRoute = currentRoute,
                onNavigate = navigate,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private data class NavTab(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val navTabs = listOf(
    NavTab(Routes.Home, "今日", Icons.Default.Home),
    NavTab(Routes.Reflect, "反思", Icons.Default.Spa),
    NavTab(Routes.History, "历史", Icons.Default.Insights),
    NavTab(Routes.Settings, "设置", Icons.Default.Settings),
)

/**
 * Floating black pill nav bar — the selected tab is a filled circle that
 * pops above the bar, the rest are plain icons inside the pill. Mimics the
 * reference education app's dock-style navigation.
 */
@Composable
private fun FloatingNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(36.dp))
                .background(NavPill)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navTabs.forEach { tab ->
                val selected = currentRoute == tab.route
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else androidx.compose.ui.graphics.Color.Transparent
                        )
                        .clickable { onNavigate(tab.route) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (selected) MaterialTheme.colorScheme.onPrimary else PaperWhite,
                    )
                }
            }
        }
    }
}

@dagger.hilt.android.lifecycle.HiltViewModel
class AppRootViewModel @Inject constructor(
    val prefs: AppPreferences,
) : androidx.lifecycle.ViewModel()
