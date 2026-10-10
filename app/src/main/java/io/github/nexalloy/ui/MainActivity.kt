package io.github.nexalloy.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.nexalloy.core.R
import io.github.nexalloy.ui.screens.AppPatchesScreen
import io.github.nexalloy.ui.screens.HomeScreen
import io.github.nexalloy.ui.screens.PatchesScreen
import io.github.nexalloy.ui.screens.SettingsScreen
import io.github.nexalloy.ui.theme.NexAlloyTheme

class MainActivity : ComponentActivity() {

    /** Destination requested by a notification, consumed by the navigation. */
    private var requestedDestination by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestedDestination = intent.getStringExtra(EXTRA_DESTINATION)
        setContent {
            NexAlloyTheme {
                ManagerApp(requestedDestination) { requestedDestination = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        requestedDestination = intent.getStringExtra(EXTRA_DESTINATION)
    }

    companion object {
        const val EXTRA_DESTINATION = "destination"
        const val DESTINATION_HOME = "home"
        const val DESTINATION_PATCHES = "patches"
        const val DESTINATION_SETTINGS = "settings"
        const val DESTINATION_APP = "app"
    }
}

private class TopLevel(val route: String, val label: Int, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(MainActivity.DESTINATION_HOME, R.string.nav_home, Icons.Filled.Home),
    TopLevel(MainActivity.DESTINATION_PATCHES, R.string.nav_patches, Icons.Filled.Build),
    TopLevel(MainActivity.DESTINATION_SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

fun NavHostController.navigateTopLevel(route: String) = navigate(route) {
    popUpTo(MainActivity.DESTINATION_HOME) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun ManagerApp(requestedDestination: String?, onDestinationHandled: () -> Unit) {
    val viewModel: ManagerViewModel = viewModel()
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(requestedDestination) {
        if (requestedDestination != null) {
            navController.navigateTopLevel(requestedDestination)
            onDestinationHandled()
        }
    }
    NotificationPermission()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (topLevel.any { it.route == route }) {
                NavigationBar {
                    topLevel.forEach { item ->
                        NavigationBarItem(
                            selected = route == item.route,
                            onClick = { navController.navigateTopLevel(item.route) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = MainActivity.DESTINATION_HOME) {
            composable(MainActivity.DESTINATION_HOME) {
                HomeScreen(viewModel, navController, padding)
            }
            composable(MainActivity.DESTINATION_PATCHES) {
                PatchesScreen(viewModel, padding)
            }
            composable(MainActivity.DESTINATION_SETTINGS) {
                SettingsScreen(viewModel, padding)
            }
            composable(
                "${MainActivity.DESTINATION_APP}/{packageName}",
                arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
            ) { entry ->
                AppPatchesScreen(viewModel, entry.arguments!!.getString("packageName")!!, navController)
            }
        }
    }
}

/** Update notifications need the permission on Android 13 and newer; asked for once. */
@Composable
private fun NotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE)
        val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted && !prefs.getBoolean("notification_permission_asked", false)) {
            prefs.edit().putBoolean("notification_permission_asked", true).apply()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
