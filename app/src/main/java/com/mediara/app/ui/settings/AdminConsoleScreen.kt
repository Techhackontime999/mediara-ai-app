package com.mediara.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediara.app.data.remote.ServerConfigManager
import com.mediara.app.data.repository.MediationRepository
import com.mediara.app.ui.components.MediaraTopBar
import com.mediara.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun AdminConsoleScreen(
    repository: MediationRepository,
    serverConfig: ServerConfigManager,
    onBackClick: () -> Unit,
    onApplied: () -> Unit
) {
    var serverUrl by remember { mutableStateOf(serverConfig.currentBaseUrl()) }
    var adminEmail by remember { mutableStateOf("") }
    var adminPassword by remember { mutableStateOf("") }
    var totpCode by remember { mutableStateOf("") }
    var mfaUri by remember { mutableStateOf<String?>(null) }
    var mfaSecret by remember { mutableStateOf<String?>(null) }
    var codeRequiredUserId by remember { mutableStateOf<Int?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    Scaffold(
        topBar = {
            MediaraTopBar(
                title = "Administrator Console",
                subtitle = "Switch the server this app connects to",
                onBackClick = onBackClick
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MediaraTealContainer.copy(alpha = 0.6f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = MediaraTealDark
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Restricted access",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MediaraTealDark
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Enter the target server address, then sign in with an administrator " +
                            "account. Credentials are verified against that server before switching. " +
                            "Administrator logins always use two-factor authentication.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MediaraTealDark
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it; errorMessage = null },
                label = { Text("Server address") },
                leadingIcon = { Icon(Icons.Default.CloudDone, contentDescription = null) },
                singleLine = true,
                enabled = codeRequiredUserId == null && mfaUri == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_server_url_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = adminEmail,
                onValueChange = { adminEmail = it; errorMessage = null },
                label = { Text("Administrator email") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                enabled = codeRequiredUserId == null && mfaUri == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_email_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = adminPassword,
                onValueChange = { adminPassword = it; errorMessage = null },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = codeRequiredUserId == null && mfaUri == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_password_input")
            )

            if (mfaUri != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MediaraIndigoContainer.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MediaraIndigoAccent
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Set up two-factor authentication",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add this account to any authenticator app (Google Authenticator, Authy). " +
                                "Then enter the 6-digit code to enable MFA and complete the switch.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        val otpauth = mfaUri ?: ""
                        if (otpauth.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(otpauth))
                                    errorMessage = null
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Copy otpauth:// link for your authenticator app",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                        mfaSecret?.let { secret ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Manual key: $secret",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MediaraIndigoAccent,
                                    textAlign = TextAlign.Center
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            if (codeRequiredUserId != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MediaraIndigoContainer.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MediaraIndigoAccent
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Two-factor authentication required",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Enter the 6-digit code from your authenticator app to continue.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }

            if ((codeRequiredUserId != null || mfaUri != null) && totpCode.isNotEmpty()) {
                OutlinedTextField(
                    value = totpCode,
                    onValueChange = { totpCode = it.filter { c -> c.isDigit() }.take(6); errorMessage = null },
                    label = { Text("6-digit code") },
                    leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_totp_input")
                )
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    when {
                        serverUrl.isBlank() || adminEmail.isBlank() || adminPassword.isBlank() -> {
                            errorMessage = "Fill in the server address and administrator credentials."
                        }
                        codeRequiredUserId != null -> {
                            if (totpCode.length != 6) {
                                errorMessage = "Enter the 6-digit authenticator code."
                            } else {
                                scope.launch {
                                    isVerifying = true
                                    errorMessage = null
                                    try {
                                        repository.adminMfaVerify(
                                            serverUrl.trim(), codeRequiredUserId!!, totpCode
                                        )
                                        serverConfig.setServerUrl(serverUrl.trim())
                                        repository.clearSessionForConfigChange()
                                        onApplied()
                                    } catch (e: Exception) {
                                        errorMessage = e.message ?: "MFA verification failed. Please try again."
                                    } finally {
                                        isVerifying = false
                                    }
                                }
                            }
                        }
                        mfaUri != null -> {
                            if (totpCode.length != 6) {
                                errorMessage = "Enter the 6-digit code from your authenticator app."
                            } else {
                                scope.launch {
                                    isVerifying = true
                                    errorMessage = null
                                    try {
                                        repository.adminMfaSetupComplete(
                                            serverUrl.trim(), adminEmail.trim(), adminPassword, totpCode
                                        )
                                        serverConfig.setServerUrl(serverUrl.trim())
                                        repository.clearSessionForConfigChange()
                                        onApplied()
                                    } catch (e: Exception) {
                                        errorMessage = e.message ?: "MFA setup failed. Please try again."
                                    } finally {
                                        isVerifying = false
                                    }
                                }
                            }
                        }
                        else -> {
                            scope.launch {
                                isVerifying = true
                                errorMessage = null
                                mfaUri = null
                                mfaSecret = null
                                codeRequiredUserId = null
                                totpCode = ""
                                try {
                                    when (val result = repository.verifyAdminAgainst(
                                        serverUrl.trim(), adminEmail.trim(), adminPassword
                                    )) {
                                        is MediationRepository.AdminVerifyResult.Success -> {
                                            serverConfig.setServerUrl(serverUrl.trim())
                                            repository.clearSessionForConfigChange()
                                            onApplied()
                                        }
                                        is MediationRepository.AdminVerifyResult.MfaCodeRequired -> {
                                            codeRequiredUserId = result.userId
                                        }
                                        is MediationRepository.AdminVerifyResult.MfaSetupRequired -> {
                                            val setup = repository.adminMfaSetup(
                                                serverUrl.trim(), adminEmail.trim(), adminPassword
                                            )
                                            mfaUri = setup.otpauthUri
                                            mfaSecret = setup.secret
                                        }
                                    }
                                } catch (e: Exception) {
                                    errorMessage = e.message ?: "Verification failed. Please try again."
                                } finally {
                                    isVerifying = false
                                }
                            }
                        }
                    }
                },
                enabled = !isVerifying,
                colors = ButtonDefaults.buttonColors(containerColor = MediaraIndigoAccent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("admin_verify_button")
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = when {
                            codeRequiredUserId != null -> "Verify Code & Switch Server"
                            mfaUri != null -> "Enable MFA & Switch Server"
                            else -> "Verify & Switch Server"
                        },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = {
                    serverConfig.resetToDefault()
                    serverUrl = serverConfig.currentBaseUrl()
                    errorMessage = null
                    mfaUri = null
                    mfaSecret = null
                    codeRequiredUserId = null
                    totpCode = ""
                },
                modifier = Modifier.testTag("admin_reset_button")
            ) {
                Text(
                    text = "Reset to default server (${serverConfig.currentBaseUrl()})",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MediaraIndigoAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}