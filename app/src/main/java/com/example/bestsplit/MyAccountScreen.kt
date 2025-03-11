// app/src/main/java/com/example/bestsplit/MyAccountScreen.kt
package com.example.bestsplit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.bestsplit.ui.theme.BestSplitTheme

@Composable
fun MyAccountScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp)) {
        // User profile header
        UserProfileHeader(
            username = "John Doe",
            email = "john.doe@example.com"
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Account Information
        Text(
            text = "Account Information",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        AccountInfoItem(label = "Phone", value = "+1 (555) 123-4567")
        AccountInfoItem(label = "Currency", value = "USD")
        AccountInfoItem(label = "Language", value = "English")

        Spacer(modifier = Modifier.height(24.dp))

        // Payment Methods
        Text(
            text = "Payment Methods",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        AccountInfoItem(label = "Default Payment", value = "Visa •••• 4321")
    }
}

@Composable
fun UserProfileHeader(username: String, email: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Profile Avatar
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Profile",
                modifier = Modifier.size(60.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Username
        Text(
            text = username,
            style = MaterialTheme.typography.headlineSmall
        )

        // Email
        Text(
            text = email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AccountInfoItem(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Divider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MyAccountScreenPreview() {
    BestSplitTheme {
        Surface {
            MyAccountScreen()
        }
    }
}