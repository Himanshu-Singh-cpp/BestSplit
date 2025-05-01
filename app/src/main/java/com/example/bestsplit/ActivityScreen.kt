package com.example.bestsplit

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bestsplit.ui.theme.BestSplitTheme
import com.example.bestsplit.ui.viewmodel.ActivityItem
import com.example.bestsplit.ui.viewmodel.ActivityType
import com.example.bestsplit.ui.viewmodel.ActivityViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActivityScreen(
    modifier: Modifier = Modifier,
    viewModel: ActivityViewModel = viewModel(),
    onNavigateToGroupDetails: (Long) -> Unit = {}
) {
    val activities by viewModel.activities.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Recent Activity",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )

            // Add refresh button
            IconButton(
                onClick = {
                    scope.launch {
                        viewModel.refreshActivities()
                    }
                },
                enabled = !isLoading
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

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

        if (isLoading && activities.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (activities.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No activity yet. Create an expense to see it here.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            ActivityList(
                activities = activities,
                onActivityClick = { activity ->
                    onNavigateToGroupDetails(activity.groupId)
                }
            )
        }
    }
}

@Composable
fun ActivityList(
    activities: List<ActivityItem>,
    modifier: Modifier = Modifier,
    onActivityClick: (ActivityItem) -> Unit
) {
    LazyColumn(modifier = modifier) {
        items(activities) { activity ->
            ActivityCard(
                activity = activity,
                onClick = { onActivityClick(activity) }
            )
        }
    }
}

@Composable
fun ActivityCard(
    activity: ActivityItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                val (icon, bgColor, contentColor) = when (activity.type) {
                    ActivityType.EXPENSE -> Triple(
                        Icons.Default.ArrowForward,
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer
                    )
                    ActivityType.YOUR_PAYMENT -> Triple(
                        Icons.Default.ArrowBack,
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }

                TransactionIcon(icon = icon, backgroundColor = bgColor, contentColor = contentColor)

                Spacer(modifier = Modifier.width(16.dp))

                // Transaction details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activity.title,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Group name
                    Text(
                        text = "in ${activity.groupName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Payment details
                    val detailText = when (activity.type) {
                        ActivityType.EXPENSE -> {
                            "${activity.payerName} paid"
                        }
                        ActivityType.YOUR_PAYMENT -> {
                            "You paid"
                        }
                    }

                    Text(
                        text = detailText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Show date
                    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    Text(
                        text = dateFormat.format(activity.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Payment amounts
                Column(horizontalAlignment = Alignment.End) {
                    when (activity.type) {
                        ActivityType.EXPENSE -> {
                            Text(
                                text = "You owe",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = "$${String.format("%.2f", activity.amount)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        ActivityType.YOUR_PAYMENT -> {
                            Text(
                                text = "Others owe you",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = "$${String.format("%.2f", activity.amount)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Show participant count
                    Spacer(modifier = Modifier.height(4.dp))
                    val participantsText = "${activity.participants.size} people"
                    Text(
                        text = participantsText,
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
    contentColor: androidx.compose.ui.graphics.Color,
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
            tint = contentColor,
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