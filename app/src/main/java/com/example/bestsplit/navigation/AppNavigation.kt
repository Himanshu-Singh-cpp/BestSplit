// app/src/main/java/com/example/bestsplit/navigation/AppNavigation.kt
package com.example.bestsplit.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.bestsplit.ActivityScreen
import com.example.bestsplit.AddGroupScreen
import com.example.bestsplit.FriendsScreen
import com.example.bestsplit.GroupsScreen
import com.example.bestsplit.LoginScreen
import com.example.bestsplit.MyAccountScreen
import com.example.bestsplit.data.model.AuthState
import com.example.bestsplit.GroupDetailsScreen
import com.example.bestsplit.ui.viewmodel.AuthViewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument


// Remove this sealed class as it's already defined in BottomNavigation.kt
// Use the imported Screen class instead

@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = when (authState) {
            is AuthState.Authenticated -> Screen.Groups.route
            AuthState.Loading -> Screen.Splash.route
            AuthState.Unauthenticated -> Screen.Login.route
        },
        modifier = modifier
    ) {
        // Auth screens
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = { navController.navigate(Screen.Groups.route) },
//                onNavigateToRegister = { navController.navigate(Screen.Register.route) }
            )
        }

        composable(Screen.Groups.route) {
            GroupsScreen(
                onNavigateToAddGroup = { navController.navigate(Screen.AddGroup.route) },
                onNavigateToGroupDetails = { groupId ->
                    navController.navigate("group_details/$groupId")
                }
            )
        }

        // Add GroupDetails route with parameter
        composable(
            route = "group_details/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.LongType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getLong("groupId") ?: 0L
            GroupDetailsScreen(
                groupId = groupId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AddGroup.route) {
            AddGroupScreen(
                onNavigateBack = { navController.popBackStack() },
                onGroupCreated = { navController.popBackStack() }
            )
        }

        composable(Screen.Friends.route) {
            FriendsScreen()
        }
        composable(Screen.Activity.route) {
            ActivityScreen()
        }
        composable(Screen.Account.route) {
            MyAccountScreen()
        }
    }
}