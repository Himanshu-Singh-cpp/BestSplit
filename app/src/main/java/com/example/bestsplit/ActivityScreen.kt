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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.bestsplit.ui.theme.BestSplitTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Transaction(
    val id: String,
    val title: String,
    val amount: Double,
    val date: Date,
    val participants: List<String>,
    val type: TransactionType
)

enum class TransactionType {
    EXPENSE, PAYMENT, SETTLEMENT
}

@Composable
fun ActivityScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "Recent Activity",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Your transaction history",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Divider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Sample data - replace with actual data in a real app
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val transactions = listOf(
            Transaction(
                "t1",
                "Dinner at Restaurant",
                45.80,
                dateFormat.parse("2023-06-15") ?: Date(),
                listOf("Alex", "Maria", "You"),
                TransactionType.EXPENSE
            ),
            Transaction(
                "t2",
                "Alex paid you",
                25.50,
                dateFormat.parse("2023-06-10") ?: Date(),
                listOf("Alex", "You"),
                TransactionType.PAYMENT
            ),
            Transaction(
                "t3",
                "Movie Tickets",
                32.00,
                dateFormat.parse("2023-06-05") ?: Date(),
                listOf("James", "Sarah", "You"),
                TransactionType.EXPENSE
            ),
            Transaction(
                "t4",
                "You settled with Maria",
                12.75,
                dateFormat.parse("2023-06-01") ?: Date(),
                listOf("Maria", "You"),
                TransactionType.SETTLEMENT
            ),
            Transaction(
                "t5",
                "Groceries",
                78.35,
                dateFormat.parse("2023-05-28") ?: Date(),
                listOf("Michael", "Sarah", "You"),
                TransactionType.EXPENSE
            )
        )

        TransactionList(transactions = transactions)
    }
}

@Composable
fun TransactionList(transactions: List<Transaction>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier) {
        items(transactions) { transaction ->
            TransactionCard(transaction = transaction)
        }
    }
}

@Composable
fun TransactionCard(transaction: Transaction, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Transaction icon
                val (icon, bgColor) = when (transaction.type) {
                    TransactionType.EXPENSE -> Icons.Default.ArrowForward to MaterialTheme.colorScheme.primaryContainer
                    TransactionType.PAYMENT -> Icons.Default.ArrowBack to MaterialTheme.colorScheme.tertiaryContainer
                    TransactionType.SETTLEMENT -> Icons.Default.Done to MaterialTheme.colorScheme.secondaryContainer
                }

                TransactionIcon(icon = icon, backgroundColor = bgColor)

                Spacer(modifier = Modifier.width(16.dp))

                // Transaction details
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = transaction.title,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Format participants string
                    val participantsText = transaction.participants.joinToString(", ")

                    Text(
                        text = participantsText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Transaction amount
                Column(horizontalAlignment = Alignment.End) {
                    val amountPrefix = when (transaction.type) {
                        TransactionType.EXPENSE -> "-$"
                        TransactionType.PAYMENT -> "+$"
                        TransactionType.SETTLEMENT -> ""
                    }

                    val amountColor = when (transaction.type) {
                        TransactionType.EXPENSE -> MaterialTheme.colorScheme.error
                        TransactionType.PAYMENT -> MaterialTheme.colorScheme.tertiary
                        TransactionType.SETTLEMENT -> MaterialTheme.colorScheme.onSurface
                    }

                    Text(
                        text = "$amountPrefix${String.format("%.2f", transaction.amount)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = amountColor,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Format date
                    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    Text(
                        text = dateFormat.format(transaction.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionIcon(
    icon: ImageVector,
    backgroundColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .background(
                color = backgroundColor,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ActivityScreenPreview() {
    BestSplitTheme {
        Surface {
            ActivityScreen()
        }
    }
}