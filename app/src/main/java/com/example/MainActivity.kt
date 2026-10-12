package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.ui.navigation.Screen
import com.example.ui.screens.*
import com.example.ui.theme.CrimsonLight
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as YouTubeLoopLiveApp
        val videoRepo = app.videoRepository
        val destRepo = app.destinationRepository
        val settingsRepo = app.settingsRepository
        val streamManager = app.multiStreamManager

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val bottomBarRoutes = listOf(
                    Screen.Dashboard.route,
                    Screen.VideoLibrary.route,
                    Screen.DestinationManagement.route,
                    Screen.MultiLiveDashboard.route,
                    Screen.Diagnostics.route
                )

                val showBottomBar = currentRoute in bottomBarRoutes

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar(
                                containerColor = SurfaceCard,
                                tonalElevation = 8.dp
                            ) {
                                NavigationBarItem(
                                    selected = currentRoute == Screen.Dashboard.route,
                                    onClick = {
                                        navController.navigate(Screen.Dashboard.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                                    label = { Text("Home", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = CrimsonLight,
                                        selectedTextColor = CrimsonLight,
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted
                                    ),
                                    modifier = Modifier.testTag("nav_item_dashboard")
                                )

                                NavigationBarItem(
                                    selected = currentRoute == Screen.VideoLibrary.route,
                                    onClick = {
                                        navController.navigate(Screen.VideoLibrary.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Videos") },
                                    label = { Text("Videos", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = CrimsonLight,
                                        selectedTextColor = CrimsonLight,
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted
                                    ),
                                    modifier = Modifier.testTag("nav_item_videos")
                                )

                                NavigationBarItem(
                                    selected = currentRoute == Screen.DestinationManagement.route,
                                    onClick = {
                                        navController.navigate(Screen.DestinationManagement.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(Icons.Default.CastConnected, contentDescription = "Destinations") },
                                    label = { Text("Destinations", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = CrimsonLight,
                                        selectedTextColor = CrimsonLight,
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted
                                    ),
                                    modifier = Modifier.testTag("nav_item_destinations")
                                )

                                NavigationBarItem(
                                    selected = currentRoute == Screen.MultiLiveDashboard.route,
                                    onClick = {
                                        navController.navigate(Screen.MultiLiveDashboard.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(Icons.Default.LiveTv, contentDescription = "Multi-Live") },
                                    label = { Text("Live", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = CrimsonLight,
                                        selectedTextColor = CrimsonLight,
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted
                                    ),
                                    modifier = Modifier.testTag("nav_item_live")
                                )

                                NavigationBarItem(
                                    selected = currentRoute == Screen.Diagnostics.route,
                                    onClick = {
                                        navController.navigate(Screen.Diagnostics.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(Icons.Default.BugReport, contentDescription = "Diagnostics") },
                                    label = { Text("Diag", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = CrimsonLight,
                                        selectedTextColor = CrimsonLight,
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted
                                    ),
                                    modifier = Modifier.testTag("nav_item_diagnostics")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Dashboard.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Dashboard.route) {
                            DashboardScreen(
                                multiStreamManager = streamManager,
                                onNavigateToVideos = { navController.navigate(Screen.VideoLibrary.route) },
                                onNavigateToDestinations = { navController.navigate(Screen.DestinationManagement.route) },
                                onNavigateToLiveConfig = { navController.navigate(Screen.LiveConfig.route) },
                                onNavigateToMultiLive = { navController.navigate(Screen.MultiLiveDashboard.route) },
                                onNavigateToDiagnostics = { navController.navigate(Screen.Diagnostics.route) },
                                onNavigateToBatterySaver = { navController.navigate(Screen.BatterySaver.route) }
                            )
                        }

                        composable(Screen.VideoLibrary.route) {
                            VideoLibraryScreen(
                                videoRepository = videoRepo,
                                onNavigateToInspect = { id -> navController.navigate(Screen.VideoInspect.createRoute(id)) },
                                onNavigateToPrepare = { id -> navController.navigate(Screen.PrepareVideo.createRoute(id)) },
                                onNavigateToDetails = { id -> navController.navigate(Screen.PreparedDetails.createRoute(id)) }
                            )
                        }

                        composable(
                            route = Screen.VideoInspect.route,
                            arguments = listOf(navArgument("videoId") { type = NavType.LongType })
                        ) { backStack ->
                            val id = backStack.arguments?.getLong("videoId") ?: 0L
                            VideoInspectionScreen(
                                videoId = id,
                                videoRepository = videoRepo,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToPrepare = { vid -> navController.navigate(Screen.PrepareVideo.createRoute(vid)) }
                            )
                        }

                        composable(
                            route = Screen.PrepareVideo.route,
                            arguments = listOf(navArgument("videoId") { type = NavType.LongType })
                        ) { backStack ->
                            val id = backStack.arguments?.getLong("videoId") ?: 0L
                            PrepareVideoScreen(
                                videoId = id,
                                videoRepository = videoRepo,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToDetails = { vid -> navController.navigate(Screen.PreparedDetails.createRoute(vid)) }
                            )
                        }

                        composable(
                            route = Screen.PreparedDetails.route,
                            arguments = listOf(navArgument("videoId") { type = NavType.LongType })
                        ) { backStack ->
                            val id = backStack.arguments?.getLong("videoId") ?: 0L
                            PreparedVideoDetailsScreen(
                                videoId = id,
                                videoRepository = videoRepo,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToLiveConfig = { navController.navigate(Screen.LiveConfig.route) }
                            )
                        }

                        composable(Screen.DestinationManagement.route) {
                            DestinationManagementScreen(
                                destinationRepository = destRepo,
                                onNavigateToEdit = { destId -> navController.navigate(Screen.EditDestination.createRoute(destId)) }
                            )
                        }

                        composable(
                            route = Screen.EditDestination.route,
                            arguments = listOf(navArgument("destId") { type = NavType.LongType })
                        ) { backStack ->
                            val destId = backStack.arguments?.getLong("destId") ?: 0L
                            EditDestinationScreen(
                                destinationId = destId,
                                destinationRepository = destRepo,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.LiveConfig.route) {
                            LiveConfigScreen(
                                videoRepository = videoRepo,
                                destinationRepository = destRepo,
                                multiStreamManager = streamManager,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToMultiLive = { navController.navigate(Screen.MultiLiveDashboard.route) }
                            )
                        }

                        composable(Screen.MultiLiveDashboard.route) {
                            MultiLiveDashboardScreen(
                                multiStreamManager = streamManager,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToDiagnostics = { navController.navigate(Screen.Diagnostics.route) }
                            )
                        }

                        composable(Screen.Diagnostics.route) {
                            DiagnosticsScreen(
                                multiStreamManager = streamManager,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.BatterySaver.route) {
                            BatterySaverScreen(
                                settingsRepository = settingsRepo,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.StorageManagement.route) {
                            StorageManagementScreen(
                                videoRepository = videoRepo,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.AboutPermissions.route) {
                            AboutPermissionsScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
