package dev.deiv.dear.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.deiv.dear.R
import dev.deiv.dear.ui.ask.AskScreen
import dev.deiv.dear.ui.collection.CollectionScreen
import dev.deiv.dear.ui.common.Dot
import dev.deiv.dear.ui.common.ScreenHeader
import dev.deiv.dear.ui.entry.EntryScreen
import dev.deiv.dear.ui.entry.EntryViewModel
import dev.deiv.dear.ui.settings.SettingsScreen
import dev.deiv.dear.ui.theme.DearTheme
import dev.deiv.dear.ui.theme.NothingRed

private object Routes {
    const val COLLECTION = "collection"
    const val ASK = "ask"
    const val SETTINGS = "settings"
    const val ENTRY = "entry/{${EntryViewModel.ARG_ID}}"

    fun entry(id: Long) = "entry/$id"
}

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        requestPermissions()

        setContent {
            DearTheme {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val route = backStackEntry?.destination?.route
                val topLevel = route == Routes.COLLECTION || route == Routes.ASK

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        if (topLevel) TopBar(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
                    },
                    bottomBar = {
                        if (topLevel) BottomBar(route, onSelect = { navController.navigateTopLevel(it) })
                    },
                ) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = Routes.COLLECTION,
                        modifier = Modifier
                            .padding(padding)
                            .consumeWindowInsets(padding),
                    ) {
                        composable(Routes.COLLECTION) {
                            CollectionScreen(onOpenEntry = { navController.navigate(Routes.entry(it)) })
                        }
                        composable(Routes.ASK) {
                            AskScreen(
                                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                                onOpenEntry = { navController.navigate(Routes.entry(it)) },
                            )
                        }
                        composable(
                            Routes.ENTRY,
                            arguments = listOf(navArgument(EntryViewModel.ARG_ID) { type = NavType.LongType }),
                        ) {
                            EntryScreen(onBack = { navController.popBackStack() })
                        }
                        composable(Routes.SETTINGS) {
                            SettingsScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(Routes.COLLECTION) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** "DEAR" and the settings gear, identical and in the same place on every top-level page. */
@Composable
private fun TopBar(onOpenSettings: () -> Unit) {
    ScreenHeader(
        title = stringResource(R.string.app_name),
        modifier = Modifier
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp),
    ) {
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
        }
    }
}

/** Two text tabs in mono caps; the active one carries a red dot. */
@Composable
private fun BottomBar(current: String?, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.navigationBarsPadding()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            BottomTab(stringResource(R.string.nav_collection), current == Routes.COLLECTION) { onSelect(Routes.COLLECTION) }
            BottomTab(stringResource(R.string.nav_ask), current == Routes.ASK) { onSelect(Routes.ASK) }
        }
    }
}

@Composable
private fun BottomTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(if (selected) NothingRed else Color.Transparent)
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
