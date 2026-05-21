package dev.deiv.dear.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.deiv.dear.ui.capture.CaptureScreen
import dev.deiv.dear.ui.chat.ChatScreen
import dev.deiv.dear.ui.jobs.JobsScreen
import dev.deiv.dear.ui.settings.SettingsScreen
import dev.deiv.dear.ui.theme.DearTheme
import dev.deiv.dear.ui.timeline.TimelineScreen

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions()

        setContent {
            DearTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentRoute == "timeline",
                                onClick = { navController.navigate("timeline") { launchSingleTop = true } },
                                icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                                label = { Text("Timeline") },
                            )
                            NavigationBarItem(
                                selected = currentRoute == "capture",
                                onClick = { navController.navigate("capture") { launchSingleTop = true } },
                                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                                label = { Text("Capture") },
                            )
                            NavigationBarItem(
                                selected = currentRoute == "chat",
                                onClick = { navController.navigate("chat") { launchSingleTop = true } },
                                icon = { Icon(Icons.Default.Email, contentDescription = null) },
                                label = { Text("Chat") },
                            )
                            NavigationBarItem(
                                selected = currentRoute == "jobs",
                                onClick = { navController.navigate("jobs") { launchSingleTop = true } },
                                icon = { Icon(Icons.Default.Info, contentDescription = null) },
                                label = { Text("Jobs") },
                            )
                            NavigationBarItem(
                                selected = currentRoute == "settings",
                                onClick = { navController.navigate("settings") { launchSingleTop = true } },
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                label = { Text("Settings") },
                            )
                        }
                    }
                ) { paddingValues ->
                    NavHost(
                        navController = navController,
                        startDestination = "timeline",
                        modifier = Modifier.padding(paddingValues),
                    ) {
                        composable("timeline") { TimelineScreen() }
                        composable("capture") { CaptureScreen() }
                        composable("chat") {
                            ChatScreen(onOpenSettings = {
                                navController.navigate("settings") { launchSingleTop = true }
                            })
                        }
                        composable("jobs") { JobsScreen() }
                        composable("settings") { SettingsScreen() }
                    }
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }
}
