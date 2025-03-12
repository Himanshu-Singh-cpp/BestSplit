package com.example.bestsplit.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.bestsplit.ActivityScreen
import com.example.bestsplit.AddGroupScreen
import com.example.bestsplit.FriendsScreen
import com.example.bestsplit.GroupsScreen
import com.example.bestsplit.MyAccountScreen

@Composable
fun AppNavigation(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Screen.Groups.route,
        modifier = modifier
    ) {
        composable(Screen.Groups.route) {
            GroupsScreen(
                onNavigateToAddGroup = {
                    navController.navigate(Screen.AddGroup.route)
                }
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
        composable(Screen.AddGroup.route) {
            AddGroupScreen(
                onNavigateBack = { navController.popBackStack() },
                onGroupCreated = {
                    navController.popBackStack()
                }
            )
        }
    }
}