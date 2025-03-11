package com.example.bestsplit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.bestsplit.ui.theme.BestSplitTheme

data class Friend(
    val name: String,
    val balance: Double // positive: they owe you, negative: you owe them
)

@Composable
fun FriendsScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "Friends",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Sample data - replace with actual data in a real app
        val friends = listOf(
            Friend("Alex Smith", 25.50),
            Friend("Maria Johnson", -12.75),
            Friend("James Brown", 8.30),
            Friend("Sarah Wilson", -30.00),
            Friend("Michael Lee", 15.25)
        )

        FriendsList(friends = friends)
    }
}

@Composable
fun FriendsList(friends: List<Friend>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier) {
        items(friends) { friend ->
            FriendCard(friend = friend)
        }
    }
}

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
                    else -> "All settled up"
                }

                val balanceColor = when {
                    friend.balance > 0 -> MaterialTheme.colorScheme.tertiary
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

@Preview(showBackground = true)
@Composable
fun FriendsScreenPreview() {
    BestSplitTheme {
        FriendsScreen()
    }
}