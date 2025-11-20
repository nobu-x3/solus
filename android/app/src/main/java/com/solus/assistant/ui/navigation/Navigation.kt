package com.solus.assistant.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.solus.assistant.data.preferences.SettingsManager
import com.solus.assistant.ui.screens.ChatScreen
import com.solus.assistant.ui.screens.MainScreen
import com.solus.assistant.ui.screens.SettingsScreen
import com.solus.assistant.ui.screens.SetupScreen

/**
 * Navigation routes
 */
object Routes {
    const val SETUP = "setup"
    const val MAIN = "main"
    const val CHAT = "chat"
    const val SETTINGS = "settings"
}

/**
 * Main navigation composable
 */
@Composable
fun Navigation() {
    val context = LocalContext.current
    val settingsManager = SettingsManager(context)
    val navController = rememberNavController()

    val isFirstRunComplete by settingsManager.isFirstRunComplete.collectAsState(initial = false)

    val startDestination = if (isFirstRunComplete) Routes.MAIN else Routes.SETUP

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.SETUP) {
            SetupScreen(
                settingsManager = settingsManager,
                onSetupComplete = {
                    navController.navigate(Routes.MAIN) {
                        // Clear setup from back stack
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.MAIN) {
            MainScreen(
                onNavigateToChat = {
                    navController.navigate(Routes.CHAT)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        composable(Routes.CHAT) {
            ChatScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
