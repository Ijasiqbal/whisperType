package com.whispertype.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.whispertype.app.ui.theme.*

@Composable
fun SuspendedScreen(
    onSignOut: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = RedLightTint,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Account Suspended",
                        style = MaterialTheme.typography.headlineMedium,
                        color = ErrorDark
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Your account has been suspended due to a policy violation. " +
                            "Your subscription has been cancelled and you will not be charged further.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate600,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "If you believe this is a mistake, please contact support.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = onSignOut,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Sign Out")
            }
        }
    }
}
