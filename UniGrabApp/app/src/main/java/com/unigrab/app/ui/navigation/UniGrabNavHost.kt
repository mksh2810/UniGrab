package com.unigrab.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.unigrab.app.ui.screens.activity.ActivityScreen
import com.unigrab.app.ui.screens.activity.ActivityViewModel
import com.unigrab.app.ui.screens.download.DownloadScreen
import com.unigrab.app.ui.screens.download.DownloadViewModel
import com.unigrab.app.ui.screens.library.LibraryScreen
import com.unigrab.app.ui.screens.library.LibraryViewModel
import com.unigrab.app.ui.screens.settings.SettingsScreen
import com.unigrab.app.ui.screens.settings.SettingsViewModel
import com.unigrab.app.ui.theme.GlassBorderColor
import com.unigrab.app.ui.theme.GlassColor
import com.unigrab.app.ui.theme.PrimaryCyan
import com.unigrab.app.ui.theme.TextSecondary

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Grab : Screen("grab", "Grab", Icons.Default.Download)
    data object Library : Screen("library", "Library", Icons.Default.Folder)
    data object Activity : Screen("activity", "Activity", Icons.Default.History)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun UniGrabNavHost(
    downloadViewModel: DownloadViewModel,
    libraryViewModel: LibraryViewModel,
    activityViewModel: ActivityViewModel,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val items = listOf(
        Screen.Grab,
        Screen.Library,
        Screen.Activity,
        Screen.Settings
    )

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(GlassColor)
                    .border(1.dp, GlassBorderColor, RoundedCornerShape(32.dp)),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    IconButton(
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title,
                                tint = if (isSelected) PrimaryCyan else TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = screen.title,
                                color = if (isSelected) PrimaryCyan else TextSecondary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Grab.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Grab.route) {
                DownloadScreen(viewModel = downloadViewModel)
            }
            composable(Screen.Library.route) {
                LibraryScreen(viewModel = libraryViewModel)
            }
            composable(Screen.Activity.route) {
                ActivityScreen(viewModel = activityViewModel)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = settingsViewModel)
            }
        }
    }
}
