package com.example.bestsplit

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.bestsplit.data.model.AuthState
import com.example.bestsplit.navigation.AppNavigation
import com.example.bestsplit.navigation.BottomNavigationBar
import com.example.bestsplit.ui.theme.BestSplitTheme
import com.example.bestsplit.ui.viewmodel.ActivityViewModel
import com.example.bestsplit.ui.viewmodel.AuthViewModel
import com.example.bestsplit.ui.viewmodel.ExpenseViewModel
import com.example.bestsplit.ui.viewmodel.GroupViewModel
import com.example.bestsplit.ui.viewmodel.SettlementViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BestSplitTheme {
                AppMain()
            }
        }
    }
}

@Composable
fun AppMain() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Access view models
    val authViewModel: AuthViewModel = viewModel()
    val expenseViewModel: ExpenseViewModel = viewModel()
    val groupViewModel: GroupViewModel = viewModel()
    val activityViewModel: ActivityViewModel = viewModel()
    val settlementViewModel: SettlementViewModel = viewModel()

    // Coroutine scope for launching async operations
    val scope = rememberCoroutineScope()

    // Observe authentication state
    val authState by authViewModel.authState.collectAsState()

    // Track the app's lifecycle
    val lifecycleOwner = LocalLifecycleOwner.current
    var wasInBackground by remember { mutableStateOf(false) }

    // Flag to track first authentication
    var isFirstAuth by remember { mutableStateOf(true) }

    // Observe lifecycle events to refresh data when returning from background
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> { wasInBackground = true }
                Lifecycle.Event.ON_RESUME -> {
                    if (wasInBackground && authState is AuthState.Authenticated) {
                        // Only refresh groups, not expenses
                        groupViewModel.refreshGroups()
                        wasInBackground = false
                    }
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Sync data when authenticated
    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            // Initialize repositories (but don't sync)
            expenseViewModel.initializeRepository()
            groupViewModel.initializeRepository()

            Log.d("MainActivity", "Auth state changed")

            // More focused initial sync - only once at app start
            if (isFirstAuth) {
                Log.d("MainActivity", "First authentication, performing sync")

                // First sync groups
                groupViewModel.refreshGroups()
                // Update activities
                activityViewModel.refreshActivities()

                isFirstAuth = false
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // Only show bottom navigation when user is authenticated
            if (authState is AuthState.Authenticated) {
                BottomNavigationBar(
                    currentRoute = currentRoute,
                    onNavigate = { screen ->
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
    ) { innerPadding ->
        AppNavigation(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}