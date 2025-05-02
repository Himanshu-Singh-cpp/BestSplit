package com.example.bestsplit

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bestsplit.data.repository.UserRepository
import com.example.bestsplit.ui.viewmodel.SettlementViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSettlementDialog(
    groupId: Long,
    members: List<UserRepository.User>,
    fromUserId: String = "",
    toUserId: String = "",
    predefinedAmount: Double = 0.0,
    onDismiss: () -> Unit,
    onSettlementAdded: () -> Unit,
    viewModel: SettlementViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()

    // State for from/to users
    var fromUserExpanded by remember { mutableStateOf(false) }
    var toUserExpanded by remember { mutableStateOf(false) }
    var selectedFromUserIndex by remember {
        mutableStateOf(members.indexOfFirst { it.id == fromUserId }.takeIf { it >= 0 } ?: 0)
    }
    var selectedToUserIndex by remember {
        mutableStateOf(members.indexOfFirst { it.id == toUserId }.takeIf { it >= 0 } ?: 0)
    }

    // Settlement amount and description
    var amount by remember { mutableStateOf(if (predefinedAmount > 0.0) predefinedAmount.toString() else "") }
    var description by remember { mutableStateOf("Settlement payment") }

    // Track settlement creation state
    val settlementState by viewModel.settlementState.collectAsState()

    // Handle settlement creation completion
    LaunchedEffect(settlementState) {
        when (settlementState) {
            is SettlementViewModel.SettlementState.Success -> {
                Log.d("AddSettlementDialog", "Settlement success - closing dialog")
                // Reset the state first
                viewModel.resetSettlementState()

                // Force sync settlements to ensure data is up to date
                viewModel.syncSettlementsForGroup(groupId)

                // Give the sync some time to complete
                delay(300)

                // Notify parent and close dialog
                onSettlementAdded()
                onDismiss()
            }

            is SettlementViewModel.SettlementState.Error -> {
                // Show error message
                Log.e(
                    "AddSettlementDialog",
                    "Error adding settlement: ${(settlementState as SettlementViewModel.SettlementState.Error).message}"
                )

                // Still dismiss dialog to avoid getting stuck
                scope.launch {
                    // Delay slightly before dismissing
                    delay(200)
                    viewModel.resetSettlementState()
                    onDismiss()
                }
            }

            else -> {} // Do nothing for other states
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Record a Settlement",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(8.dp))

                // From user dropdown
                Text("Who paid?", style = MaterialTheme.typography.bodyLarge)
                ExposedDropdownMenuBox(
                    expanded = fromUserExpanded,
                    onExpandedChange = { fromUserExpanded = !fromUserExpanded },
                ) {
                    TextField(
                        value = if (members.isNotEmpty()) members[selectedFromUserIndex].name else "",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = fromUserExpanded)
                        },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = fromUserExpanded,
                        onDismissRequest = { fromUserExpanded = false },
                    ) {
                        members.forEachIndexed { index, member ->
                            DropdownMenuItem(
                                text = { Text(member.name) },
                                onClick = {
                                    selectedFromUserIndex = index
                                    fromUserExpanded = false
                                }
                            )
                        }
                    }
                }

                // To user dropdown
                Text("Who received?", style = MaterialTheme.typography.bodyLarge)
                ExposedDropdownMenuBox(
                    expanded = toUserExpanded,
                    onExpandedChange = { toUserExpanded = !toUserExpanded },
                ) {
                    TextField(
                        value = if (members.isNotEmpty()) members[selectedToUserIndex].name else "",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = toUserExpanded)
                        },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = toUserExpanded,
                        onDismissRequest = { toUserExpanded = false },
                    ) {
                        members.forEachIndexed { index, member ->
                            DropdownMenuItem(
                                text = { Text(member.name) },
                                onClick = {
                                    selectedToUserIndex = index
                                    toUserExpanded = false
                                }
                            )
                        }
                    }
                }

                // Amount input
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                // Description input
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    // Validate input
                                    if (description.isBlank() || amount.isBlank() || selectedFromUserIndex == selectedToUserIndex) {
                                        return@launch
                                    }

                                    val amountValue = amount.toDoubleOrNull() ?: 0.0
                                    if (amountValue <= 0.0 || selectedFromUserIndex == selectedToUserIndex) {
                                        return@launch
                                    }

                                    // Dismiss dialog immediately after button click for better UX
                                    // The state management will still handle callbacks
                                    Log.d(
                                        "AddSettlementDialog",
                                        "Save button clicked - starting dismissal"
                                    )

                                    // Start dismissal process with a short delay
                                    scope.launch {
                                        delay(300) // Short delay to show feedback
                                        onDismiss() // Force dismiss dialog
                                    }

                                    // Add settlement in background
                                    viewModel.addSettlement(
                                        groupId = groupId,
                                        fromUserId = members[selectedFromUserIndex].id,
                                        toUserId = members[selectedToUserIndex].id,
                                        amount = amountValue,
                                        description = description.trim()
                                    )

                                    // Call the added callback for data refresh
                                    scope.launch {
                                        delay(500) // Short delay
                                        onSettlementAdded() // Force data refresh
                                    }
                                } catch (e: Exception) {
                                    Log.e("AddSettlementDialog", "Error adding settlement", e)
                                }
                            }
                        },
                        enabled = amount.toDoubleOrNull() != null &&
                                amount.toDoubleOrNull()!! > 0.0 &&
                                selectedFromUserIndex != selectedToUserIndex &&
                                settlementState !is SettlementViewModel.SettlementState.Loading
                    ) {
                        if (settlementState is SettlementViewModel.SettlementState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}