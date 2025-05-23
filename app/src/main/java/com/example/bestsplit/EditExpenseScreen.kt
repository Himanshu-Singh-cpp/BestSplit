package com.example.bestsplit

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bestsplit.data.entity.Expense
import com.example.bestsplit.data.repository.UserRepository
import com.example.bestsplit.ui.viewmodel.ExpenseViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseScreen(
    groupId: Long,
    expenseId: Long,
    members: List<UserRepository.User>,
    viewModel: ExpenseViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var expense by remember { mutableStateOf<Expense?>(null) }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var selectedPayerIndex by remember { mutableStateOf(0) }

    // Split type: 0 = Equal, 1 = Custom
    var splitType by remember { mutableStateOf(0) }

    // Custom split amounts for each member
    val memberShares = remember { mutableStateMapOf<String, String>() }

    // Load existing expense details
    LaunchedEffect(expenseId) {
        // Fetch expense data
        scope.launch {
            try {
                // We need to add a method to get expense by ID in the viewModel
                val existingExpense = viewModel.getExpenseById(expenseId)
                if (existingExpense != null) {
                    expense = existingExpense
                    description = existingExpense.description
                    amount = existingExpense.amount.toString()

                    // Find payer index
                    val payerIndex = members.indexOfFirst { it.id == existingExpense.paidBy }
                    if (payerIndex >= 0) {
                        selectedPayerIndex = payerIndex
                    }

                    // Determine if it's equal or custom split
                    val equalShare = existingExpense.amount / members.size
                    val isEqualSplit = existingExpense.paidFor.values.all { value ->
                        areAmountsClose(value, equalShare)
                    }

                    splitType = if (isEqualSplit) 0 else 1

                    // Set up the custom shares
                    members.forEach { member ->
                        val share = existingExpense.paidFor[member.id] ?: 0.0
                        memberShares[member.id] = share.toString()
                    }
                }
            } catch (e: Exception) {
                Log.e("EditExpenseScreen", "Error loading expense", e)
            }
        }
    }

    // Initialize with empty shares if not already set
    LaunchedEffect(members) {
        members.forEach { member ->
            if (!memberShares.containsKey(member.id)) {
                memberShares[member.id] = ""
            }
        }
    }

    // For dropdown menu
    var expanded by remember { mutableStateOf(false) }

    // Track expense update state
    val expenseUpdateState by viewModel.expenseUpdateState.collectAsState()

    // Handle expense update completion
    LaunchedEffect(expenseUpdateState) {
        when (expenseUpdateState) {
            is ExpenseViewModel.ExpenseUpdateState.Success -> {
                // Reset the state and navigate back
                viewModel.resetExpenseUpdateState()
                // Sync expenses for this group to ensure Firebase data is up to date
                viewModel.syncExpensesForGroup(groupId)
                onNavigateBack()
            }

            is ExpenseViewModel.ExpenseUpdateState.Error -> {
                // Show error message
                Log.e(
                    "EditExpenseScreen",
                    "Error updating expense: ${(expenseUpdateState as ExpenseViewModel.ExpenseUpdateState.Error).message}"
                )

                // Still navigate back to avoid getting stuck
                scope.launch {
                    // Delay slightly before navigating back
                    delay(200)
                    viewModel.resetExpenseUpdateState()
                    onNavigateBack()
                }
            }

            else -> {} // Do nothing for other states
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Expense") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                try {
                                    // Validate input
                                    if (description.isBlank() || amount.isBlank() || expense == null) {
                                        return@launch
                                    }

                                    val totalAmount = amount.toDoubleOrNull() ?: return@launch

                                    // Ensure we have a valid payer
                                    if (selectedPayerIndex >= members.size) {
                                        return@launch
                                    }
                                    val paidBy = members[selectedPayerIndex].id

                                    // Calculate shares based on split type
                                    val shares = calculateShares(
                                        members = members,
                                        totalAmount = totalAmount,
                                        splitType = splitType,
                                        customShares = memberShares,
                                        paidBy = paidBy
                                    )

                                    // Validate the shares
                                    val sharesTotal = shares.values.sum()
                                    if (splitType == 1 && !areAmountsClose(
                                            sharesTotal,
                                            totalAmount
                                        )
                                    ) {
                                        // Don't proceed if custom split amounts don't add up
                                        return@launch
                                    }

                                    // Update the expense object
                                    val updatedExpense = expense!!.copy(
                                        description = description.trim(),
                                        amount = totalAmount,
                                        paidBy = paidBy,
                                        paidFor = shares
                                    )

                                    // Update the expense in both local database and Firebase
                                    viewModel.updateExpense(updatedExpense)

                                    // Log the expense details for debugging
                                    Log.d(
                                        "EditExpenseScreen",
                                        "Updating expense: id=${updatedExpense.id}, groupId=$groupId, desc='${description.trim()}', amount=$totalAmount, paidBy=$paidBy"
                                    )
                                    Log.d(
                                        "EditExpenseScreen",
                                        "Shares: ${shares.entries.joinToString { "${it.key}=${it.value}" }}"
                                    )

                                    // Force sync after updating expense
                                    viewModel.syncExpensesForGroup(groupId)
                                    // Recalculate balances for this group
                                    viewModel.recalculateBalances(groupId)
                                } catch (e: Exception) {
                                    Log.e("EditExpenseScreen", "Error updating expense", e)
                                }
                            }
                        },
                        enabled = description.isNotBlank() && amount.isNotBlank() && expense != null &&
                                amount.toDoubleOrNull() != null && amount.toDoubleOrNull()!! > 0
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Description input
            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Amount input
            item {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }

            // Paid by selector
            item {
                Text("Paid by", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                // Display dropdown list of members
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                ) {
                    TextField(
                        value = if (members.isNotEmpty()) members[selectedPayerIndex].name else "",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier.menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        members.forEachIndexed { index, member ->
                            DropdownMenuItem(
                                text = { Text(member.name) },
                                onClick = {
                                    selectedPayerIndex = index
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Split type selection
            item {
                Text("Split type", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = splitType == 0,
                        onClick = { splitType = 0 }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Equal")

                    Spacer(Modifier.width(16.dp))

                    RadioButton(
                        selected = splitType == 1,
                        onClick = { splitType = 1 }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Custom")
                }
            }

            // Custom split section (only shown if custom split is selected)
            if (splitType == 1) {
                item {
                    Text("Custom split", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Show an error if the total doesn't match the amount
                    val customTotal = memberShares.values.sumOf { it.toDoubleOrNull() ?: 0.0 }
                    val totalAmount = amount.toDoubleOrNull() ?: 0.0

                    if (customTotal > 0 && totalAmount > 0 && !areAmountsClose(
                            customTotal,
                            totalAmount
                        )
                    ) {
                        Text(
                            "Total split (${
                                String.format(
                                    "%.2f",
                                    customTotal
                                )
                            }) doesn't match expense amount (${
                                String.format(
                                    "%.2f",
                                    totalAmount
                                )
                            })",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Custom split amount input for each member
                items(members) { member ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            member.name,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = memberShares[member.id] ?: "",
                            onValueChange = { memberShares[member.id] = it },
                            modifier = Modifier.width(120.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            label = { Text("Amount") },
                            singleLine = true
                        )
                    }
                }
            }

            // Loading indicator
            if (expenseUpdateState is ExpenseViewModel.ExpenseUpdateState.Loading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

// Helper function to calculate shares based on split type
private fun calculateShares(
    members: List<UserRepository.User>,
    totalAmount: Double,
    splitType: Int,
    customShares: Map<String, String>,
    paidBy: String
): Map<String, Double> {
    return when (splitType) {
        0 -> { // Equal split
            val share = totalAmount / members.size
            members.associate { it.id to share }
        }

        1 -> { // Custom split
            // Make sure all members are included in the result, even with zero amounts
            val shares = customShares.mapValues { (_, value) -> value.toDoubleOrNull() ?: 0.0 }

            // Make sure every member has an entry
            val result = members.associate { it.id to (shares[it.id] ?: 0.0) }

            // Log the shares for debugging
            Log.d(
                "EditExpenseScreen",
                "Custom shares: ${result.entries.joinToString { "${it.key}=${it.value}" }}"
            )

            result
        }

        else -> emptyMap()
    }
}

// Helper function to check if two doubles are close enough (to handle floating point precision)
private fun areAmountsClose(a: Double, b: Double): Boolean {
    return Math.abs(a - b) < 0.01
}