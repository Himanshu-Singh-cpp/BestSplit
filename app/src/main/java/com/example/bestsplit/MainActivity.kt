package com.example.bestsplit

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

    // Request camera permission at the activity level
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("MainActivity", "Camera permission granted")
        } else {
            Log.d("MainActivity", "Camera permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check and request camera permission early
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("MainActivity", "Requesting camera permission at app start")
            requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }

        setContent {
            val darkTheme = rememberSaveable { mutableStateOf(false) }
            BestSplitTheme(
                useDarkTheme = darkTheme.value
            ) {
                AppMain(darkTheme = darkTheme)
            }
        }
    }
}

@Composable
fun AppMain(darkTheme: androidx.compose.runtime.MutableState<Boolean>) {
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
            modifier = Modifier.padding(innerPadding),
            darkTheme = darkTheme
        )
    }
}