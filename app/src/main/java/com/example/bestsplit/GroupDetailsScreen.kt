package com.example.bestsplit

import android.util.Log
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
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bestsplit.data.entity.Expense
import com.example.bestsplit.data.entity.Group
import com.example.bestsplit.data.repository.UserRepository
import com.example.bestsplit.ui.viewmodel.ExpenseViewModel
import com.example.bestsplit.ui.viewmodel.GroupViewModel
import kotlinx.coroutines.delay
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
    onNavigateBack: () -> Unit = {},
    onAddExpense: (Long, List<UserRepository.User>) -> Unit = { _, _ -> }
) {
    val scope = rememberCoroutineScope()
    var group by remember { mutableStateOf<Group?>(null) }
    var members by remember { mutableStateOf<List<UserRepository.User>>(emptyList()) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // Force refresh periodically
    var forceRefresh by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30000) // 30 seconds refresh interval
            forceRefresh++
            Log.d("GroupDetailsScreen", "Triggering periodic refresh")
        }
    }

    // Initial sync on screen load
    LaunchedEffect(Unit) {
        expenseViewModel.syncExpensesForGroup(groupId)

        // Enable aggressive syncing with error handling
        try {
            Log.d("GroupDetailsScreen", "Performing aggressive initial sync")

            // Multiple sync attempts to ensure we get the data
            repeat(3) {
                expenseViewModel.syncExpensesForGroup(groupId)
                delay(500)
            }

            // Try to force a fresh query
            expenseViewModel.syncExpensesForGroup(groupId)
        } catch (e: Exception) {
            Log.e("GroupDetailsScreen", "Error during initial sync", e)
        }
    }

    // Observe expenses for this group
    val expenses by expenseViewModel.getExpensesForGroup(groupId)
        .collectAsState(initial = emptyList())

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
    LaunchedEffect(groupId, forceRefresh) {
        scope.launch {
            try {
                // Sync from cloud first
                expenseViewModel.syncExpensesForGroup(groupId)
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
                    // Re-sync expenses to make sure we have the latest data
                    expenseViewModel.syncExpensesForGroup(groupId)

                    // Short delay to ensure sync is complete
                    delay(300)

                    // Try syncing again to be sure
                    expenseViewModel.syncExpensesForGroup(groupId)
                    delay(200)

                    // Recalculate balances
                    balances = expenseViewModel.calculateBalances(groupId, group!!.members)
                } catch (e: Exception) {
                    // Log error but don't crash
                    Log.e("GroupDetailsScreen", "Error calculating balances", e)
                }
            }
        }
    }

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
                            // Sync expenses when tab is selected
                            syncExpenses()
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
                    0 -> ExpensesTab(sortedExpenses, members, isSyncing)
                    1 -> BalancesTab(balances, members)
                    2 -> MembersTab(members)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterialApi::class)
fun ExpensesTab(expenses: List<Expense>, members: List<UserRepository.User>, isSyncing: Boolean) {
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

                // Find the group ID from the first expense (if any)
                val groupId = expenses.firstOrNull()?.groupId
                if (groupId != null) {
                    viewModel.syncExpensesForGroup(groupId)
                    delay(1000) // Give some time for the sync to complete
                }

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
                    ExpenseItem(expense, memberMap)
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

@Composable
fun ExpenseItem(expense: Expense, memberMap: Map<String, UserRepository.User>) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance() }
    val payerName = memberMap[expense.paidBy]?.name ?: "Unknown"
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()) }
    val date = dateFormat.format(Date(expense.createdAt))

    // Count how many people are involved in this expense
    val participantCount = expense.paidFor.size

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
                    color = MaterialTheme.colorScheme.primary
                )
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
                            text = memberName + if (isCurrentUser) " (you)" else "",
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
fun BalancesTab(balances: Map<String, Map<String, Double>>, members: List<UserRepository.User>) {
    val memberMap = remember(members) {
        members.associateBy { it.id }
    }

    val currencyFormat = remember { NumberFormat.getCurrencyInstance() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        items(members) { member ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Member name header
                    Text(
                        text = member.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Who owes this member money
                    val owedToThisMember = members.filter { other ->
                        other.id != member.id &&
                                (balances[other.id]?.get(member.id) ?: 0.0) > 0
                    }

                    if (owedToThisMember.isNotEmpty()) {
                        Text(
                            text = "Owes you:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        owedToThisMember.forEach { otherMember ->
                            val amount = balances[otherMember.id]?.get(member.id) ?: 0.0
                            if (amount > 0) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = otherMember.name,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium
                                    )

                                    Text(
                                        text = currencyFormat.format(amount),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Green
                                    )
                                }
                            }
                        }
                    }

                    // Who this member owes money to
                    val thisOwesToOthers = members.filter { other ->
                        other.id != member.id &&
                                (balances[member.id]?.get(other.id) ?: 0.0) > 0
                    }

                    if (thisOwesToOthers.isNotEmpty()) {
                        if (owedToThisMember.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        Text(
                            text = "You owe:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        thisOwesToOthers.forEach { otherMember ->
                            val amount = balances[member.id]?.get(otherMember.id) ?: 0.0
                            if (amount > 0) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = otherMember.name,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium
                                    )

                                    Text(
                                        text = currencyFormat.format(amount),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Red
                                    )
                                }
                            }
                        }
                    }

                    if (owedToThisMember.isEmpty() && thisOwesToOthers.isEmpty()) {
                        Text(
                            text = "All settled up!",
                            style = MaterialTheme.typography.bodyMedium
                        )
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