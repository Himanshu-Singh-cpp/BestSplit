package com.example.bestsplit

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current

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

    // Payment transaction reference for tracking
    var transactionReference by remember { mutableStateOf<String?>(null) }
    var showPaymentVerificationDialog by remember { mutableStateOf(false) }

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

                // Show confirmation
                Toast.makeText(context, "Settlement recorded successfully", Toast.LENGTH_SHORT)
                    .show()
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

    // Check if the recipient has a valid UPI ID
    var recipientUpiId by remember { mutableStateOf<String?>(null) }

    // Fetch the recipient's UPI ID when the selected recipient changes
    LaunchedEffect(selectedToUserIndex) {
        if (members.isNotEmpty()) {
            val user = viewModel.getUserDetails(members[selectedToUserIndex].id)
            recipientUpiId = user?.upiId
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

                // UPI Payment Button - show only if recipient has a valid UPI ID
                if (!recipientUpiId.isNullOrEmpty() && selectedFromUserIndex != selectedToUserIndex && amount.toDoubleOrNull() != null && amount.toDoubleOrNull()!! > 0.0) {
                    Button(
                        onClick = {
                            val amountValue = amount.toDoubleOrNull() ?: 0.0
                            // Generate transaction reference
                            val txnRef = "BestSplit${System.currentTimeMillis()}"
                            transactionReference = txnRef

                            // Initiate the UPI payment first, without recording settlement yet
                            initiateUpiPayment(
                                context = context,
                                upiId = recipientUpiId!!,
                                amount = amountValue,
                                description = description.ifEmpty { "BestSplit Settlement" },
                                transactionRef = txnRef
                            )

                            // Show payment verification dialog after a short delay
                            scope.launch {
                                delay(2000) // Wait for user to complete payment
                                showPaymentVerificationDialog = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Pay",
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            "Pay ₹${
                                amount.toDoubleOrNull()?.let { String.format("%.2f", it) } ?: "0.00"
                            } via UPI")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

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

    // Payment verification dialog
    if (showPaymentVerificationDialog) {
        PaymentVerificationDialog(
            onConfirm = {
                // Add settlement only after payment confirmation
                val amountValue = amount.toDoubleOrNull() ?: 0.0
                viewModel.addSettlement(
                    groupId = groupId,
                    fromUserId = members[selectedFromUserIndex].id,
                    toUserId = members[selectedToUserIndex].id,
                    amount = amountValue,
                    description = "UPI Payment: " + description.trim().ifEmpty { "Settlement" }
                )

                // Close both dialogs
                showPaymentVerificationDialog = false
                onDismiss()

                // Refresh settlements
                onSettlementAdded()

                // Show confirmation
                Toast.makeText(context, "Settlement recorded successfully", Toast.LENGTH_SHORT)
                    .show()
            },
            onDismiss = {
                // Just close verification dialog without recording settlement
                showPaymentVerificationDialog = false
                Toast.makeText(
                    context,
                    "Settlement not recorded. You can try again.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
}

@Composable
fun PaymentVerificationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
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
                    text = "Payment Verification",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = "Did you complete the UPI payment successfully?",
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = "The settlement will only be recorded in BestSplit if you confirm the payment was successful.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("No")
                    }

                    Button(onClick = onConfirm) {
                        Text("Yes")
                    }
                }
            }
        }
    }
}

// Function to handle UPI payment
private fun initiateUpiPayment(
    context: Context,
    upiId: String,
    amount: Double,
    description: String,
    transactionRef: String? = null
) {
    try {
        // Format amount properly with 2 decimal places
        val formattedAmount = String.format("%.2f", amount)

        // Create UPI payment URI with all required parameters
        val uri = Uri.parse("upi://pay")
            .buildUpon()
            .appendQueryParameter("pa", upiId)  // payee address (UPI ID)
            .appendQueryParameter("pn", "BestSplit Payment")  // payee name
            .appendQueryParameter(
                "tn",
                description.ifEmpty { "Settlement payment" })  // transaction note
            .appendQueryParameter("am", formattedAmount)  // amount
            .appendQueryParameter("cu", "INR")  // currency
            .appendQueryParameter("mc", "")  // merchant code (optional)
            .appendQueryParameter(
                "tr",
                transactionRef ?: "BestSplit${System.currentTimeMillis()}"
            )  // transaction reference ID
            .build()

        Log.d("UpiPayment", "Payment URI: $uri")

        val upiPayIntent = Intent(Intent.ACTION_VIEW).apply {
            data = uri
            // Ensure URI is not modified by the app
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        }

        // Check if there are apps that can handle this intent
        val packageManager = context.packageManager
        val activities = packageManager.queryIntentActivities(upiPayIntent, PackageManager.MATCH_DEFAULT_ONLY)

        if (activities.isNotEmpty()) {
            // Show payment apps chooser
            val chooser = Intent.createChooser(upiPayIntent, "Pay with...")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

            // Toast to confirm payment initiation
            Toast.makeText(context, "Payment of ₹$formattedAmount initiated", Toast.LENGTH_SHORT)
                .show()
        } else {
            Toast.makeText(context, "No UPI apps found on device", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Log.e("UpiPayment", "Error initiating UPI payment", e)
        Toast.makeText(context, "Error initiating payment: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}