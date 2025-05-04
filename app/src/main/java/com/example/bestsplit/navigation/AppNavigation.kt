// app/src/main/java/com/example/bestsplit/navigation/AppNavigation.kt
package com.example.bestsplit.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.bestsplit.ActivityScreen
import com.example.bestsplit.AddGroupScreen
import com.example.bestsplit.AddExpenseScreen
import com.example.bestsplit.EditExpenseScreen
import com.example.bestsplit.FriendsScreen
import com.example.bestsplit.GroupDetailsScreen
import com.example.bestsplit.GroupsScreen
import com.example.bestsplit.LoginScreen
import com.example.bestsplit.MyAccountScreen
import com.example.bestsplit.data.model.AuthState
import com.example.bestsplit.data.repository.UserRepository
import com.example.bestsplit.ui.viewmodel.AuthViewModel
import com.example.bestsplit.ui.viewmodel.GroupViewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import kotlinx.coroutines.launch

@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = viewModel(),
    darkTheme: MutableState<Boolean>? = null
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
                onNavigateBack = { navController.popBackStack() },
                onAddExpense = { gId, members ->
                    navController.navigate("add_expense/$gId")
                },
                onEditExpense = { expense, members ->
                    navController.navigate("edit_expense/${expense.groupId}/${expense.id}")
                }
            )
        }

        // Add Expense screen
        composable(
            route = "add_expense/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.LongType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getLong("groupId") ?: 0L

            // Get the members list from the previous screen or fetch it again
            val scope = rememberCoroutineScope()
            val groupViewModel: GroupViewModel = viewModel()
            val members = remember { mutableStateListOf<UserRepository.User>() }

            LaunchedEffect(groupId) {
                scope.launch {
                    val group = groupViewModel.getGroupById(groupId)
                    if (group != null) {
                        // Load member details
                        val userRepo = UserRepository()
                        val memberDetails = group.members.mapNotNull { memberId ->
                            userRepo.getUserById(memberId)
                        }
                        members.clear()
                        members.addAll(memberDetails)
                    }
                }
            }

            AddExpenseScreen(
                groupId = groupId,
                members = members,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Edit Expense screen
        composable(
            route = "edit_expense/{groupId}/{expenseId}",
            arguments = listOf(
                navArgument("groupId") { type = NavType.LongType },
                navArgument("expenseId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getLong("groupId") ?: 0L
            val expenseId = backStackEntry.arguments?.getLong("expenseId") ?: 0L

            // Get the members list from the previous screen or fetch it again
            val scope = rememberCoroutineScope()
            val groupViewModel: GroupViewModel = viewModel()
            val members = remember { mutableStateListOf<UserRepository.User>() }

            LaunchedEffect(groupId) {
                scope.launch {
                    val group = groupViewModel.getGroupById(groupId)
                    if (group != null) {
                        // Load member details
                        val userRepo = UserRepository()
                        val memberDetails = group.members.mapNotNull { memberId ->
                            userRepo.getUserById(memberId)
                        }
                        members.clear()
                        members.addAll(memberDetails)
                    }
                }
            }

            EditExpenseScreen(
                groupId = groupId,
                expenseId = expenseId,
                members = members,
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
            ActivityScreen(
                onNavigateToGroupDetails = { groupId ->
                    navController.navigate("group_details/$groupId")
                }
            )
        }
        composable(Screen.Account.route) {
            MyAccountScreen(
                darkTheme = darkTheme
            )
        }
    }
}