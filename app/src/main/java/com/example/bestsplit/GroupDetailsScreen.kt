package com.example.bestsplit

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bestsplit.data.entity.Expense
import com.example.bestsplit.data.entity.Group
import com.example.bestsplit.data.repository.UserRepository
import com.example.bestsplit.ui.viewmodel.ExpenseViewModel
import com.example.bestsplit.ui.viewmodel.ExpenseViewModel.ExpenseDeletionState
import com.example.bestsplit.ui.viewmodel.GroupViewModel
import com.example.bestsplit.ui.viewmodel.SettlementViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun GroupDetailsScreen(
    groupId: Long,
    groupViewModel: GroupViewModel = viewModel(),
    expenseViewModel: ExpenseViewModel = viewModel(),
    settlementViewModel: SettlementViewModel = viewModel(),
    onNavigateBack: () -> Unit = {},
    onAddExpense: (Long, List<UserRepository.User>) -> Unit = { _, _ -> },
    onEditExpense: (Expense, List<UserRepository.User>) -> Unit = { _, _ -> }
) {
    val scope = rememberCoroutineScope()
    var group by remember { mutableStateOf<Group?>(null) }
    var members by remember { mutableStateOf<List<UserRepository.User>>(emptyList()) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    // Initial sync on screen load
    LaunchedEffect(Unit) {
        try {
            Log.d("GroupDetailsScreen", "Performing initial sync")
            expenseViewModel.syncExpensesForGroup(groupId)
        } catch (e: Exception) {
            Log.e("GroupDetailsScreen", "Error during initial sync", e)
        }
    }

    // Observe expenses for this group
    val expenses by expenseViewModel.getExpensesForGroup(groupId)
        .collectAsState(initial = emptyList())

    // Observe delete state
    val deletionState by expenseViewModel.expenseDeletionState.collectAsState()

    // Reset deletion state when leaving the screen
    LaunchedEffect(Unit) {
        expenseViewModel.resetExpenseDeletionState()
    }

    // Handle deletion state changes
    LaunchedEffect(deletionState) {
        when (deletionState) {
            is ExpenseDeletionState.Success -> {
                // Show success message or refresh data
                expenseViewModel.resetExpenseDeletionState()
                expenseViewModel.syncExpensesForGroup(groupId)
                // Recalculate balances after expense deletion
                if (group != null) {
                    expenseViewModel.recalculateBalances(groupId)
                }
            }

            is ExpenseDeletionState.Error -> {
                // Could show error message here
                expenseViewModel.resetExpenseDeletionState()
            }

            else -> {}
        }
    }

    // Sort expenses by date (most recent first)
    val sortedExpenses = remember(expenses) {
        expenses.sortedByDescending { it.createdAt }
    }

    // Calculated balances
    var balances by remember { mutableStateOf<Map<String, Map<String, Double>>>(emptyMap()) }

    // Track if we're currently syncing expenses
    var isSyncing by remember { mutableStateOf(false) }

    // Function to sync expenses with loading indicator
    val syncExpenses = {
        scope.launch {
            isSyncing = true
            try {
                expenseViewModel.syncExpensesForGroup(groupId)
                delay(1000)
            } finally {
                isSyncing = false
            }
        }
    }

    // Load group details
    LaunchedEffect(groupId) {
        scope.launch {
            try {
                // Sync from cloud once
                groupViewModel.refreshGroups()

                // Wait a moment to ensure sync completes
                delay(300)

                // Then fetch the group
                group = groupViewModel.getGroupById(groupId)

                // Load member details
                if (group != null) {
                    val memberDetails = group!!.members.mapNotNull { memberId ->
                        // Assuming you have a way to get UserRepository
                        val userRepo = UserRepository()
                        userRepo.getUserById(memberId)
                    }
                    members = memberDetails

                    // Calculate balances
                    balances = expenseViewModel.calculateBalances(groupId, group!!.members)
                }
            } catch (e: Exception) {
                // Log error but don't crash
                Log.e("GroupDetailsScreen", "Error loading group details", e)
            }
        }
    }

    // Update balances when expenses change, with debouncing
    var lastExpenseCount by remember { mutableStateOf(0) }
    LaunchedEffect(expenses) {
        if (expenses.size != lastExpenseCount) {
            Log.d(
                "GroupDetailsScreen",
                "Expenses changed from $lastExpenseCount to ${expenses.size}"
            )
            lastExpenseCount = expenses.size

            if (group != null) {
                try {
                    // Use a try-catch with isActive check to handle composition leaving
                    val currentGroup = group // Capture group in a local variable
                    if (currentGroup == null) {
                        Log.d("GroupDetailsScreen", "Skipping balance calculation - group is null")
                        return@LaunchedEffect
                    }
                    val currentGroupId = groupId // Capture groupId in a local variable

                    scope.launch {

                        try {
                            // Check if still active before each operation
                            if (!isActive) return@launch

                            // Re-sync expenses to make sure we have the latest data
                            expenseViewModel.syncExpensesForGroup(currentGroupId)

                            // Short delay to ensure sync is complete
                            delay(300)
                            if (!isActive) return@launch

                            // Try syncing again to be sure
                            expenseViewModel.syncExpensesForGroup(currentGroupId)
                            delay(200)
                            if (!isActive) return@launch

                            // Recalculate balances
                            val newBalances = expenseViewModel.calculateBalances(
                                currentGroupId,
                                currentGroup.members
                            )

                            // Final check before updating state
                            if (isActive) {
                                balances = newBalances
                            }
                        } catch (e: Exception) {
                            // Check if cancellation exception
                            if (e is CancellationException) {
                                Log.d("GroupDetailsScreen", "Balance calculation cancelled")
                            } else {
                                Log.e("GroupDetailsScreen", "Error calculating balances", e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Log error but don't crash
                    Log.e("GroupDetailsScreen", "Error launching balance calculation", e)
                }
            }
        }
    }

    val userRepository = UserRepository()
    var currentUserId by remember { mutableStateOf("") }
    var currentUser by remember { mutableStateOf<UserRepository.User?>(null) }

    LaunchedEffect(Unit) {
        scope.launch {
            currentUserId = userRepository.getCurrentUserId()
            if (currentUserId.isNotEmpty()) {
                currentUser = userRepository.getUserById(currentUserId)
            }
        }
    }

    var showQrScanner by remember { mutableStateOf(false) }
    var qrScanAmount by remember { mutableStateOf(0.0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.name ?: "Group Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (group != null && members.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { onAddExpense(groupId, members) }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Expense")
                }
            }
        }
    ) { paddingValues ->
        if (group == null) {
            // Loading state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Group details content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Group header
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = group!!.name,
                            style = MaterialTheme.typography.headlineMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (group!!.description.isNotEmpty()) {
                            Text(
                                text = group!!.description,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Text(
                            text = "Created on ${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(group!!.createdAt))}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Tab row
                TabRow(selectedTabIndex = selectedTabIndex) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = {
                            selectedTabIndex = 0
                        },
                        text = { Text("Expenses") }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = { Text("Balances") }
                    )
                    Tab(
                        selected = selectedTabIndex == 2,
                        onClick = { selectedTabIndex = 2 },
                        text = { Text("Members") }
                    )
                }

                when (selectedTabIndex) {
                    0 -> ExpensesTab(
                        expenses = sortedExpenses,
                        members = members,
                        isSyncing = isSyncing,
                        expenseViewModel = expenseViewModel,
                        onEditExpense = onEditExpense
                    )
                    1 -> BalancesTab(
                        balances = balances,
                        members = members,
                        groupId = groupId,
                        currentUserId = currentUserId,
                        currentUser = currentUser,
                        onSettlementAdded = {
                            scope.launch {
                                // Show loading state
                                isSyncing = true

                                try {
                                    val currentGroup = group
                                    if (currentGroup == null) {
                                        Log.d(
                                            "GroupDetailsScreen",
                                            "Skipping balance calculation - group is null"
                                        )
                                        return@launch
                                    }

                                    // Force refresh settlements and expenses
                                    settlementViewModel.syncSettlementsForGroup(groupId)
                                    expenseViewModel.syncExpensesForGroup(groupId)

                                    // Delay to allow sync to complete
                                    delay(500)

                                    // Recalculate balances with null check
                                    if (isActive && currentGroup != null) {
                                        try {
                                            val newBalances = expenseViewModel.calculateBalances(
                                                groupId,
                                                currentGroup.members
                                            )

                                            // Only update if still active
                                            if (isActive) {
                                                balances = newBalances
                                            }
                                        } catch (e: Exception) {
                                            if (e !is CancellationException) {
                                                Log.e("Balances", "Error calculating balances", e)
                                            }
                                        }
                                    }

                                    // One more sync to be absolutely sure
                                    if (isActive) {
                                        expenseViewModel.syncExpensesForGroup(groupId)
                                    }
                                } catch (e: Exception) {
                                    Log.e("Balances", "Error refreshing", e)
                                } finally {
                                    if (isActive) {
                                        delay(300) // Small delay before hiding loading
                                        isSyncing = false
                                    }
                                }
                            }
                        },
                        settlementViewModel = settlementViewModel,
                        showQrScanner = showQrScanner,
                        onShowQrScanner = { amount ->
                            qrScanAmount = amount
                            showQrScanner = true
                        }
                    )
                    2 -> MembersTab(members)
                }
            }
        }
    }

    if (showQrScanner) {
        QRScannerScreen(
            onClose = {
                showQrScanner = false
            },
            onQrCodeDetected = { qrContent, amount ->
                // Process QR code and close scanner
                showQrScanner = false

                // Handle the QR code with a delay to ensure scanner is closed
                scope.launch {
                    delay(100) // Short delay for cleanup
                    UpiPaymentUtils.parseUpiQrCode(qrContent)?.let { upiDetails ->
                        // Generate transaction reference
                        val txnRef = "BestSplit${System.currentTimeMillis()}"

                        // Initiate payment with the passed amount
                        UpiPaymentUtils.initiateUpiPayment(
                            context = context,
                            upiId = upiDetails.upiId,
                            amount = amount,
                            description = "BestSplit Settlement",
                            transactionRef = txnRef
                        )

                        // Show confirmation and possibly record settlement
                        Toast.makeText(
                            context,
                            "Payment initiated. Please confirm when complete.",
                            Toast.LENGTH_LONG
                        ).show()

                        // Wait a moment then show settlement dialog again to confirm
                        delay(1500)
                    } ?: run {
                        // Not a valid UPI QR code
                        Toast.makeText(
                            context,
                            "Not a valid UPI QR code",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            amount = qrScanAmount
        )
    }
}

@Composable
@OptIn(ExperimentalMaterialApi::class)
fun ExpensesTab(
    expenses: List<Expense>,
    members: List<UserRepository.User>,
    isSyncing: Boolean,
    expenseViewModel: ExpenseViewModel,
    onEditExpense: (Expense, List<UserRepository.User>) -> Unit
) {
    val memberMap = remember(members) {
        members.associateBy { it.id }
    }

    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    val viewModel: ExpenseViewModel = viewModel()

    // Pull-to-refresh state
    val pullRefreshState = rememberPullRefreshState(
        refreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                refreshExpenses(scope, expenses, expenseViewModel)
                refreshing = false
            }
        }
    )

    if (expenses.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(pullRefreshState),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No expenses yet. Add one by clicking the + button.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Pull down to refresh",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Pull to refresh indicator
            PullRefreshIndicator(
                refreshing = refreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(pullRefreshState)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                items(expenses) { expense ->
                    ExpenseItem(
                        expense = expense,
                        memberMap = memberMap,
                        expenseViewModel = expenseViewModel,
                        members = members,
                        onEditExpense = onEditExpense
                    )
                }
            }

            // Pull to refresh indicator
            PullRefreshIndicator(
                refreshing = refreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // Loading indicator
            if (isSyncing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

// Helper function to refresh expenses
private suspend fun refreshExpenses(
    scope: CoroutineScope,
    expenses: List<Expense>,
    expenseViewModel: ExpenseViewModel
) {
    try {
        // Find the group ID from the first expense (if any)
        val groupId = expenses.firstOrNull()?.groupId
        if (groupId != null) {
            // Force multiple syncs to ensure we get all data
            repeat(3) {
                expenseViewModel.syncExpensesForGroup(groupId)
                delay(300)
            }
        }
    } catch (e: Exception) {
        Log.e("ExpensesTab", "Error refreshing expenses", e)
    }
}

@Composable
fun ExpenseItem(
    expense: Expense,
    memberMap: Map<String, UserRepository.User>,
    expenseViewModel: ExpenseViewModel,
    members: List<UserRepository.User>,
    onEditExpense: (Expense, List<UserRepository.User>) -> Unit
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance() }
    val payerName = memberMap[expense.paidBy]?.name ?: "Unknown"
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()) }
    val date = dateFormat.format(Date(expense.createdAt))

    // Count how many people are involved in this expense
    val participantCount = expense.paidFor.size

    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = expense.description,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = currencyFormat.format(expense.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )

                Box {
                    IconButton(
                        onClick = { expanded = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    androidx.compose.material3.DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit"
                                )
                            },
                            onClick = {
                                expanded = false
                                onEditExpense(expense, members)
                            }
                        )

                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color.Red
                                )
                            },
                            onClick = {
                                expanded = false
                                scope.launch {
                                    expenseViewModel.deleteExpense(expense.id, expense.groupId)
                                    // The balance recalculation is handled in the LaunchedEffect for deletionState
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Payer information with icon
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Paid by $payerName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = " • $date",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expense.paidFor.size > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Split between $participantCount people:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                expense.paidFor.forEach { (memberId, amount) ->
                    val memberName = memberMap[memberId]?.name ?: "Unknown"
                    val isCurrentUser = memberId == expense.paidBy

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = memberName,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isCurrentUser) FontWeight.Medium else FontWeight.Normal
                        )

                        Text(
                            text = currencyFormat.format(amount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isCurrentUser) MaterialTheme.colorScheme.primary else Color.Unspecified
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BalancesTab(
    balances: Map<String, Map<String, Double>>,
    members: List<UserRepository.User>,
    groupId: Long,
    currentUserId: String,
    currentUser: UserRepository.User?,
    onSettlementAdded: () -> Unit,
    settlementViewModel: SettlementViewModel = viewModel(),
    showQrScanner: Boolean,
    onShowQrScanner: (Double) -> Unit
) {
    val memberMap = remember(members) {
        members.associateBy { it.id }
    }

    val currencyFormat = remember { NumberFormat.getCurrencyInstance() }
    val scope = rememberCoroutineScope()

    var isRefreshing by remember { mutableStateOf(false) }

    // Force refresh function that can be called from multiple places
    val forceRefresh = {
        scope.launch {
            isRefreshing = true
            try {
                // Sync settlements
                settlementViewModel.syncSettlementsForGroup(groupId)
                delay(300)

                // Call callback to recalculate balances
                onSettlementAdded()
            } finally {
                // Allow time for UI update
                delay(500)
                isRefreshing = false
            }
        }
    }

    // Also observe settlement state to refresh when complete
    val settlementState by settlementViewModel.settlementState.collectAsState()

    // When settlement state changes to success, trigger refresh
    LaunchedEffect(settlementState) {
        if (settlementState is SettlementViewModel.SettlementState.Success) {
            // Reset state first to avoid infinite loops
            settlementViewModel.resetSettlementState()

            // Then refresh
            forceRefresh()
        }
    }

    // State for settlement dialog
    var showSettlementDialog by remember { mutableStateOf(false) }
    var selectedSettlementParams by remember { mutableStateOf(TripleData("", "", 0.0)) }

    // Observe settlements
    val settlements by settlementViewModel.getSettlementsForGroup(groupId)
        .collectAsState(initial = emptyList())

    // Sync settlements when tab is shown
    LaunchedEffect(Unit) {
        settlementViewModel.syncSettlementsForGroup(groupId)
    }

    // Show settlement dialog when requested
    if (showSettlementDialog) {
        AddSettlementDialog(
            groupId = groupId,
            members = members,
            fromUserId = selectedSettlementParams.first,
            toUserId = selectedSettlementParams.second,
            predefinedAmount = selectedSettlementParams.third,
            onDismiss = {
                // Force dialog to close
                Log.d("BalancesTab", "Settlement dialog dismissed")
                showSettlementDialog = false
            },
            onSettlementAdded = {
                // Note: this might not get called if there's an issue with the settlement callback
                Log.d("BalancesTab", "Settlement added - refreshing data")
                showSettlementDialog = false
                forceRefresh()
            },
            onStartQrScanner = {
                onShowQrScanner(selectedSettlementParams.third)
                showSettlementDialog = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Header
        item {
            Text(
                text = "Current Balances",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // If we don't have the current user yet, show loading
        if (currentUser == null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (isRefreshing) {
            // Show loading overlay when refreshing
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Updating balances...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        } else {
            item {
                // Single card for the current user's balances
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // Check if the current user has any balances
                        val hasBalances = members.any { other ->
                            other.id != currentUserId &&
                                    ((balances[other.id]?.get(currentUserId) ?: 0.0) > 0 ||
                                            (balances[currentUserId]?.get(other.id) ?: 0.0) > 0)
                        }

                        if (!hasBalances) {
                            Text(
                                text = "You have no outstanding balances",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            // Show balances between current user and each member
                            members.filter { it.id != currentUserId }.forEach { otherMember ->
                                val theyOwe = balances[otherMember.id]?.get(currentUserId) ?: 0.0
                                val userOwes = balances[currentUserId]?.get(otherMember.id) ?: 0.0

                                if (theyOwe > 0 || userOwes > 0) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = otherMember.name,
                                            modifier = Modifier.width(120.dp),
                                            style = MaterialTheme.typography.bodyMedium
                                        )

                                        if (theyOwe > 0) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = "owes you ",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        text = currencyFormat.format(theyOwe),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = Color.Green,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        } else if (userOwes > 0) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = "you owe ",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        Text(
                                                            text = currencyFormat.format(userOwes),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = Color.Red,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }

                                                // Only show settle button for amounts the user owes
                                                Button(
                                                    onClick = {
                                                        selectedSettlementParams = TripleData(
                                                            first = currentUserId,
                                                            second = otherMember.id,
                                                            third = userOwes
                                                        )
                                                        showSettlementDialog = true
                                                    },
                                                    modifier = Modifier.padding(start = 8.dp)
                                                ) {
                                                    Text("Settle")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (members.all { other ->
                            other.id != currentUserId &&
                                    ((balances[other.id]?.get(currentUserId) ?: 0.0) == 0.0 &&
                                            (balances[currentUserId]?.get(other.id) ?: 0.0) == 0.0)
                        }) {
                        Text(
                            text = "All settled up!",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Show recent settlements if any
        if (settlements.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Recent Settlements",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                settlements.take(5).forEach { settlement ->
                    val fromUser = memberMap[settlement.fromUserId]
                    val toUser = memberMap[settlement.toUserId]

                    if (fromUser != null && toUser != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${fromUser.name} paid ${toUser.name}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )

                                    if (settlement.description.isNotEmpty()) {
                                        Text(
                                            text = settlement.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Text(
                                        text = SimpleDateFormat(
                                            "MMM d, yyyy",
                                            Locale.getDefault()
                                        ).format(
                                            Date(settlement.createdAt)
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text(
                                    text = currencyFormat.format(settlement.amount),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MembersTab(members: List<UserRepository.User>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        items(members) { member ->
            MemberItem(member = member)
        }
    }
}

@Composable
fun MemberItem(member: UserRepository.User) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Member avatar
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = member.name.firstOrNull()?.toString() ?: "?",
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = member.name,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = member.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Helper class for settlement data
data class TripleData(
    val first: String,
    val second: String,
    val third: Double
)