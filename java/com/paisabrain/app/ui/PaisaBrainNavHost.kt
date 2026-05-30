package com.paisabrain.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.paisabrain.app.R
import com.paisabrain.app.ui.screens.*

sealed class Screen(
    val route: String,
    val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Money : Screen("money", R.string.nav_money, Icons.Filled.AccountBalance, Icons.Outlined.AccountBalance)
    data object Vault : Screen("vault", R.string.nav_vault, Icons.Filled.Lock, Icons.Outlined.Lock)
    data object Insights : Screen("insights", R.string.nav_insights, Icons.Filled.Lightbulb, Icons.Outlined.Lightbulb)
    data object Settings : Screen("settings", R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
}

val bottomNavItems = listOf(Screen.Money, Screen.Vault, Screen.Insights, Screen.Settings)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaisaBrainNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Check if onboarding is needed
    var showOnboarding by remember { mutableStateOf(false) } // TODO: Check SharedPrefs

    if (showOnboarding) {
        OnboardingScreen(onComplete = { showOnboarding = false })
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (currentRoute == screen.route)
                                    screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = stringResource(screen.titleRes)
                            )
                        },
                        label = { Text(stringResource(screen.titleRes)) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Money.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Money.route) { MoneyBrainScreen() }
            composable(Screen.Vault.route) { VaultBrainScreen() }
            composable(Screen.Insights.route) { InsightsScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
