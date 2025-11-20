package com.solus.assistant.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.solus.assistant.ui.screens.ChatScreen
import com.solus.assistant.ui.screens.MainScreen
import com.solus.assistant.ui.screens.SettingsScreen

/**
 * Navigation routes
 */
object Routes {
    const val MAIN = "main"
    const val CHAT = "chat"
    const val SETTINGS = "settings"
}

/**
 * Main navigation composable
 */
@Composable
fun Navigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.MAIN
    ) {
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
