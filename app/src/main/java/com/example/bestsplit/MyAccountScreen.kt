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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.bestsplit.data.repository.UserRepository
import com.example.bestsplit.ui.theme.BestSplitTheme
import kotlinx.coroutines.launch

@Composable
fun MyAccountScreen(
    modifier: Modifier = Modifier,
    darkTheme: androidx.compose.runtime.MutableState<Boolean>? = null
) {
    val context = LocalContext.current
    val userRepository = remember { UserRepository() }
    val coroutineScope = rememberCoroutineScope()
    
    var userData by remember { mutableStateOf<UserRepository.User?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    var upiId by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        val currentUserId = userRepository.getCurrentUserId()
        if (currentUserId.isNotEmpty()) {
            userData = userRepository.getUserById(currentUserId)
            upiId = userData?.upiId ?: ""
        }
    }
    
    Column(modifier = modifier.padding(16.dp)) {
        // User profile header
        userData?.let {
            UserProfileHeader(
                username = it.name,
                email = it.email
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Theme Toggle
        if (darkTheme != null) {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Dark theme",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.CenterStart)
                )

                Switch(
                    checked = darkTheme.value,
                    onCheckedChange = { darkTheme.value = it },
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }

            Divider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Payment Information
        Text(
            text = "Payment Information",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // UPI ID Field
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "UPI ID",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (isEditing) {
                OutlinedTextField(
                    value = upiId,
                    onValueChange = { upiId = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Email
                    ),
                    trailingIcon = {
                        Button(onClick = {
                            coroutineScope.launch {
                                userData?.let {
                                    val updatedUser = it.copy(upiId = upiId)
                                    userRepository.saveUser(updatedUser)
                                    userData = updatedUser
                                }
                                isEditing = false
                            }
                        }) {
                            Text("Save")
                        }
                    },
                    label = { Text("e.g. yourname@upi") },
                    supportingText = { Text("Adding your UPI ID enables direct payment settlements") }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.align(Alignment.CenterStart)) {
                        Text(
                            text = upiId.ifEmpty { "Not set" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (upiId.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )

                        if (upiId.isNotEmpty()) {
                            Text(
                                text = "Others can pay you directly to settle expenses",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Add your UPI ID to enable direct payments",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = { isEditing = true },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit UPI ID"
                        )
                    }
                }
            }
            
            Divider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
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
    val previewDarkTheme = remember { mutableStateOf(false) }
    BestSplitTheme {
        Surface {
            MyAccountScreen(darkTheme = previewDarkTheme)
        }
    }
}