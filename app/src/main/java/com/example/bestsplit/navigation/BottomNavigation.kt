// app/src/main/java/com/example/bestsplit/navigation/BottomNavigation.kt
package com.example.bestsplit.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    object Groups : Screen("groups", "Groups", Icons.Default.Home)
    object Friends : Screen("friends", "Friends", Icons.Default.Person)
    object Account : Screen("account", "Account", Icons.Default.AccountCircle)
    object Activity : Screen("activity", "Activity", Icons.Default.List)

    // Auth screens
    object Login : Screen("login", "Login")
    object Splash : Screen("splash", "Splash")

    // Add Group screen without an icon since it's not a bottom nav item
    object AddGroup : Screen("add_group", "Add Group")
    object GroupDetails : Screen("group_details/{groupId}", "Group Details")
    object AddExpense : Screen("add_expense/{groupId}", "Add Expense")
}

@Composable
fun BottomNavigationBar(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val screens = listOf(
        Screen.Groups,
        Screen.Friends,
        Screen.Activity,
        Screen.Account
    )

    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        screens.forEach { screen ->
            val selected = currentRoute == screen.route
            NavigationBarItem(
                icon = {
                    screen.icon?.let { icon ->
                        Icon(imageVector = icon, contentDescription = screen.title)
                    }
                },
                label = { Text(screen.title) },
                selected = selected,
                onClick = { onNavigate(screen) }
            )
        }
    }
}