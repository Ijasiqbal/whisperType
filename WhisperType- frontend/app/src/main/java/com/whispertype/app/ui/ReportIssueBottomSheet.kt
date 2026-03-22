package com.whispertype.app.ui

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.whispertype.app.BuildConfig
import com.whispertype.app.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private enum class IssueCategory(val label: String, val value: String) {
    BUG("Bug", "bug"),
    FEATURE_REQUEST("Feature Request", "feature_request"),
    TRANSCRIPTION_ISSUE("Transcription Issue", "transcription_issue"),
    OTHER("Other", "other")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportIssueBottomSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedCategory by remember { mutableStateOf(IssueCategory.BUG) }
    var description by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Title
            Text(
                text = "Report an Issue",
                style = MaterialTheme.typography.headlineMedium,
                color = Slate800
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Describe the problem and we'll look into it",
                style = MaterialTheme.typography.bodyMedium,
                color = Slate500
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Category dropdown
            Text(
                text = "Category",
                style = MaterialTheme.typography.titleSmall,
                color = Slate800
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { categoryExpanded = true },
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedCategory.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Slate800
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = "Select category",
                            tint = Slate500
                        )
                    }
                }

                DropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.88f)
                ) {
                    IssueCategory.entries.forEach { category ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = category.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Slate800
                                )
                            },
                            onClick = {
                                selectedCategory = category
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Description field
            Text(
                text = "Description",
                style = MaterialTheme.typography.titleSmall,
                color = Slate800
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                placeholder = {
                    Text(
                        text = "Describe the issue you're experiencing...",
                        color = Slate400
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Rust,
                    unfocusedBorderColor = Slate200,
                    cursorColor = Rust
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Device info hint
            Text(
                text = "Device info will be attached automatically",
                style = MaterialTheme.typography.bodySmall,
                color = Slate400
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Submit button
            Button(
                onClick = {
                    if (description.isBlank()) {
                        Toast.makeText(context, "Please describe the issue", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isSubmitting = true
                    coroutineScope.launch {
                        try {
                            val user = FirebaseAuth.getInstance().currentUser
                            val issueData = hashMapOf(
                                "userId" to (user?.uid ?: "unknown"),
                                "userEmail" to (user?.email ?: "unknown"),
                                "category" to selectedCategory.value,
                                "description" to description.trim(),
                                "deviceModel" to "${Build.MANUFACTURER} ${Build.MODEL}",
                                "androidVersion" to Build.VERSION.RELEASE,
                                "appVersion" to BuildConfig.VERSION_NAME,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "status" to "open"
                            )

                            FirebaseFirestore.getInstance()
                                .collection("issues")
                                .add(issueData)
                                .await()

                            Toast.makeText(context, "Issue reported. Thank you!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } catch (e: Exception) {
                            android.util.Log.e("ReportIssue", "Failed to submit issue", e)
                            Toast.makeText(context, "Failed to submit. Please try again.", Toast.LENGTH_SHORT).show()
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmitting && description.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Rust,
                    disabledContainerColor = Rust.copy(alpha = 0.5f)
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = if (isSubmitting) "Submitting..." else "Submit",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}
