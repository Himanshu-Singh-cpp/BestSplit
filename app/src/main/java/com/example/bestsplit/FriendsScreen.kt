package com.example.bestsplit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bestsplit.ui.viewmodel.FriendsViewModel

data class Friend(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val balance: Double = 0.0 // positive: they owe you, negative: you owe them
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    modifier: Modifier = Modifier,
    viewModel: FriendsViewModel = viewModel()
) {
    val friends by viewModel.friends.collectAsState()
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var emailInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddFriendDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Friend")
            }
        }
    ) { padding ->
        Column(modifier = Modifier
            .padding(padding)
            .padding(16.dp)) {
            Text(
                text = "Friends",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            FriendsList(friends = friends)
        }

        // Add Friend Dialog
        if (showAddFriendDialog) {
            AlertDialog(
                onDismissRequest = {
                    showAddFriendDialog = false
                    errorMessage = null  // Clear error message when dismissing
                },
                title = { Text("Add Friend") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            label = { Text("Email Address") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (emailInput.isNotBlank()) {
                            viewModel.addFriend(
                                emailInput,
                                onSuccess = {
                                    emailInput = ""
                                    errorMessage = null
                                    showAddFriendDialog = false
                                },
                                onError = {
                                    errorMessage = "User not found or couldn't be added"
                                }
                            )
                        }
                    }) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showAddFriendDialog = false
                        errorMessage = null  // Clear error message when canceling
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun FriendsList(friends: List<Friend>, modifier: Modifier = Modifier) {
    if (friends.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No friends added yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(modifier = modifier) {
            items(friends) { friend ->
                FriendCard(friend = friend)
            }
        }
    }
}

// FriendCard implementation remains the same as your original code

@Composable
fun FriendCard(friend: Friend, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar for friend
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = friend.name.first().toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Friend name and balance information
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = friend.name,
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(4.dp))

                val balanceText = when {
                    friend.balance > 0 -> "Owes you $${String.format("%.2f", friend.balance)}"
                    friend.balance < 0 -> "You owe $${String.format("%.2f", -friend.balance)}"
                    else -> friend.email
                }

                val balanceColor = when {
                    friend.balance > 0 -> MaterialTheme.colorScheme.primary
                    friend.balance < 0 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Text(
                    text = balanceText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = balanceColor
                )
            }
        }
    }
}

//@Preview(showBackground = true)
//@Composable
//fun FriendsScreenPreview() {
//    BestSplitTheme {
//        FriendsScreen()
//    }
//}