package com.mediara.app.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediara.app.R
import com.mediara.app.data.repository.MediationRepository
import com.mediara.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Splash (unchanged)
// ---------------------------------------------------------------------------

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) { delay(1600); onTimeout() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(MediaraNavy, MediaraIndigo))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .border(2.dp, MediaraTealLight.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_mediara_logo),
                    contentDescription = "Mediara AI logo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Mediara AI",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 1.sp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.splash_tagline),
                style = MaterialTheme.typography.bodyMedium.copy(color = MediaraTealContainer, textAlign = TextAlign.Center)
            )
            Spacer(Modifier.height(32.dp))
            CircularProgressIndicator(color = MediaraTealLight, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Onboarding (unchanged)
// ---------------------------------------------------------------------------

@Composable
fun OnboardingScreen(
    onGetStarted: () -> Unit,
    onJoinWithCode: () -> Unit,
    onLoginClick: () -> Unit,
    onAdminClick: () -> Unit
) {
    var adminTaps by remember { mutableStateOf(0) }
    var adminTapReset by remember { mutableStateOf(0) }
    LaunchedEffect(adminTapReset) { if (adminTapReset > 0) { delay(1600); adminTaps = 0 } }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            adminTaps++; adminTapReset++
                            if (adminTaps >= 5) { adminTaps = 0; onAdminClick() }
                        }
                    }
            ) {
                Image(painterResource(R.drawable.hero_mediation_calm), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.onboarding_hero_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 32.sp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.onboarding_hero_desc),
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, lineHeight = 22.sp)
            )
            Spacer(Modifier.height(24.dp))
            OnboardingPillar(Icons.Default.Lock, stringResource(R.string.onboarding_pillar_private_title), stringResource(R.string.onboarding_pillar_private_desc))
            Spacer(Modifier.height(12.dp))
            OnboardingPillar(Icons.Default.Balance, stringResource(R.string.onboarding_pillar_impartial_title), stringResource(R.string.onboarding_pillar_impartial_desc))
            Spacer(Modifier.height(12.dp))
            OnboardingPillar(Icons.Default.Description, stringResource(R.string.onboarding_pillar_accord_title), stringResource(R.string.onboarding_pillar_accord_desc))
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onGetStarted,
                colors = ButtonDefaults.buttonColors(containerColor = MediaraIndigoAccent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("onboarding_get_started_button")
            ) { Text(stringResource(R.string.onboarding_get_started), fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onJoinWithCode, shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("onboarding_join_code_button")
            ) {
                Icon(Icons.Default.GroupAdd, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.onboarding_invite_code), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onLoginClick, Modifier.testTag("onboarding_login_button")) {
                Text(stringResource(R.string.onboarding_already_account), style = MaterialTheme.typography.bodyMedium.copy(color = MediaraIndigoAccent, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

@Composable
private fun OnboardingPillar(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(MediaraTealContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MediaraTealDark, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp))
            Text(description, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.5.sp))
        }
    }
}

// ---------------------------------------------------------------------------
// Shared auth components
// ---------------------------------------------------------------------------

private fun isValidEmail(e: String) = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(e)

private enum class PwViolation { NONE, LENGTH, UPPER, LOWER, DIGIT }

private fun validatePassword(p: String): PwViolation = when {
    p.length < 8 -> PwViolation.LENGTH
    !p.any { it.isUpperCase() } -> PwViolation.UPPER
    !p.any { it.isLowerCase() } -> PwViolation.LOWER
    !p.any { it.isDigit() } -> PwViolation.DIGIT
    else -> PwViolation.NONE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthFormScaffold(
    title: String, subtitle: String, showBack: Boolean = false, onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    if (showBack && onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)))
                    .padding(vertical = 32.dp), contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                        Image(painterResource(R.drawable.ic_mediara_logo), null, Modifier.size(48.dp), contentScale = ContentScale.Fit)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(title, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary))
                    Spacer(Modifier.height(6.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f), textAlign = TextAlign.Center))
                }
            }
            Spacer(Modifier.height(28.dp))
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AuthButton(text: String, loading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick, enabled = !loading,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth().height(52.dp)
    ) {
        if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        else Text(text, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun PasswordField(
    value: String, onValueChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {}, error: String? = null, isError: Boolean = error != null
) {
    var hidden by remember { mutableStateOf(true) }
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label) }, leadingIcon = { Icon(Icons.Default.Lock, null) },
        trailingIcon = {
            IconButton(onClick = { hidden = !hidden }) {
                Icon(if (hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Toggle password visibility")
            }
        },
        visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onImeAction() }),
        isError = isError,
        supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) } },
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun PasswordStrengthBar(password: String) {
    if (password.isEmpty()) return
    val (level, color) = when (validatePassword(password)) {
        PwViolation.NONE -> when {
            password.length < 10 -> 1 to MediaraAmber
            password.length < 14 -> 2 to MediaraTeal
            else -> 3 to Color(0xFF059669)
        }
        else -> 0 to MaterialTheme.colorScheme.error
    }
    Column(Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { (level + 1) / 4f },
            color = color, trackColor = color.copy(alpha = 0.12f),
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
        )
    }
}

// ---------------------------------------------------------------------------
// Login Screen
// ---------------------------------------------------------------------------

@Composable
fun LoginScreen(
    repository: MediationRepository,
    onLoginSuccess: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    onForgotPassword: () -> Unit,
    onEmailVerificationRequired: (email: String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    // Pre-resolve strings outside coroutines
    val fillAll = stringResource(R.string.error_fill_all)
    val invalidEmail = stringResource(R.string.error_email_invalid)
    val genericError = stringResource(R.string.error_generic)

    AuthFormScaffold(stringResource(R.string.login_title), stringResource(R.string.login_subtitle)) {
        OutlinedTextField(
            value = email, onValueChange = { email = it; errorMsg = null },
            label = { Text(stringResource(R.string.field_email)) },
            leadingIcon = { Icon(Icons.Default.Email, null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().testTag("login_email_input")
        )
        Spacer(Modifier.height(14.dp))
        PasswordField(
            value = password, onValueChange = { password = it; errorMsg = null },
            label = stringResource(R.string.field_password),
            imeAction = ImeAction.Done, onImeAction = { focus.clearFocus() },
            modifier = Modifier.testTag("login_password_input")
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onForgotPassword, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.login_forgot_password), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
        }
        if (errorMsg != null) {
            Spacer(Modifier.height(8.dp))
            Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(20.dp))
        AuthButton(
            text = stringResource(R.string.login_sign_in), loading = loading, modifier = Modifier.testTag("login_submit_button"),
            onClick = {
                focus.clearFocus()
                if (email.isBlank() || password.isBlank()) { errorMsg = fillAll; return@AuthButton }
                if (!isValidEmail(email.trim())) { errorMsg = invalidEmail; return@AuthButton }
                scope.launch {
                    loading = true; errorMsg = null
                    try {
                        when (val result = repository.login(email.trim(), password)) {
                            is MediationRepository.LoginResult.Success -> onLoginSuccess()
                            is MediationRepository.LoginResult.EmailVerificationRequired -> onEmailVerificationRequired(result.email)
                        }
                    } catch (e: Exception) { errorMsg = e.message ?: genericError }
                    finally { loading = false }
                }
            }
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.login_no_account), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onNavigateToSignUp, Modifier.testTag("login_goto_signup_button")) {
                Text(stringResource(R.string.login_sign_up), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Sign Up Screen
// ---------------------------------------------------------------------------

@Composable
fun SignUpScreen(
    repository: MediationRepository,
    onSignUpSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onEmailVerificationRequired: (email: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    val fillAll = stringResource(R.string.error_fill_all)
    val invalidEmail = stringResource(R.string.error_email_invalid)
    val pwLength = stringResource(R.string.error_password_length)
    val pwUpper = stringResource(R.string.error_password_upper)
    val pwLower = stringResource(R.string.error_password_lower)
    val pwDigit = stringResource(R.string.error_password_digit)
    val genericError = stringResource(R.string.error_generic)

    AuthFormScaffold(stringResource(R.string.signup_title), stringResource(R.string.signup_subtitle)) {
        OutlinedTextField(
            value = name, onValueChange = { name = it; errorMsg = null },
            label = { Text(stringResource(R.string.field_full_name)) },
            leadingIcon = { Icon(Icons.Default.Person, null) },
            singleLine = true, modifier = Modifier.fillMaxWidth().testTag("signup_name_input")
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = email, onValueChange = { email = it; errorMsg = null },
            label = { Text(stringResource(R.string.field_email)) },
            leadingIcon = { Icon(Icons.Default.Email, null) },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().testTag("signup_email_input")
        )
        Spacer(Modifier.height(14.dp))
        PasswordField(
            value = password, onValueChange = { password = it; errorMsg = null },
            label = stringResource(R.string.field_password), modifier = Modifier.testTag("signup_password_input")
        )
        PasswordStrengthBar(password)
        Spacer(Modifier.height(12.dp))
        if (errorMsg != null) { Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(8.dp)) }
        Text(stringResource(R.string.signup_terms), style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center), modifier = Modifier.padding(horizontal = 12.dp))
        Spacer(Modifier.height(18.dp))
        AuthButton(
            text = stringResource(R.string.signup_create), loading = loading, modifier = Modifier.testTag("signup_submit_button"),
            onClick = {
                focus.clearFocus()
                val pw = password; val em = email.trim(); val nm = name.trim()
                if (nm.isBlank() || em.isBlank() || pw.isBlank()) { errorMsg = fillAll; return@AuthButton }
                if (!isValidEmail(em)) { errorMsg = invalidEmail; return@AuthButton }
                val pwFail = validatePassword(pw)
                if (pwFail != PwViolation.NONE) {
                    errorMsg = when (pwFail) {
                        PwViolation.LENGTH -> pwLength; PwViolation.UPPER -> pwUpper
                        PwViolation.LOWER -> pwLower; PwViolation.DIGIT -> pwDigit
                        else -> genericError
                    }
                    return@AuthButton
                }
                scope.launch {
                    loading = true; errorMsg = null
                    try {
                        val user = repository.register(nm, em, pw)
                        if (user.emailVerified) onSignUpSuccess() else onEmailVerificationRequired(em)
                    } catch (e: Exception) { errorMsg = e.message ?: genericError }
                    finally { loading = false }
                }
            }
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.signup_have_account), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onNavigateToLogin, Modifier.testTag("signup_goto_login_button")) {
                Text(stringResource(R.string.login_sign_in), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Forgot Password Screen
// ---------------------------------------------------------------------------

@Composable
fun ForgotPasswordScreen(
    repository: MediationRepository, onBack: () -> Unit, onCodeSent: (email: String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var successMsg by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    val invalidEmail = stringResource(R.string.error_email_invalid)
    val genericError = stringResource(R.string.error_generic)
    val codeSent = stringResource(R.string.forgot_code_sent)

    AuthFormScaffold(stringResource(R.string.forgot_title), stringResource(R.string.forgot_subtitle), showBack = true, onBack = onBack) {
        OutlinedTextField(
            value = email, onValueChange = { email = it; errorMsg = null; successMsg = null },
            label = { Text(stringResource(R.string.field_email)) },
            leadingIcon = { Icon(Icons.Default.Email, null) },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))
        if (errorMsg != null) { Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(8.dp)) }
        if (successMsg != null) { Text(successMsg!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(12.dp)) }
        AuthButton(text = stringResource(R.string.forgot_send_code), loading = loading, onClick = {
            focus.clearFocus()
            if (!isValidEmail(email.trim())) { errorMsg = invalidEmail; return@AuthButton }
            scope.launch {
                loading = true; errorMsg = null; successMsg = null
                try { repository.requestPasswordReset(email.trim()); successMsg = codeSent; onCodeSent(email.trim()) }
                catch (e: Exception) { errorMsg = e.message ?: genericError }
                finally { loading = false }
            }
        })
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onBack) { Text(stringResource(R.string.forgot_back_to_login), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
    }
}

// ---------------------------------------------------------------------------
// Reset Password Screen
// ---------------------------------------------------------------------------

@Composable
fun ResetPasswordScreen(
    repository: MediationRepository, email: String, onBack: () -> Unit, onResetSuccess: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var successMsg by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    val fillAll = stringResource(R.string.error_fill_all)
    val pwLength = stringResource(R.string.error_password_length)
    val pwUpper = stringResource(R.string.error_password_upper)
    val pwLower = stringResource(R.string.error_password_lower)
    val pwDigit = stringResource(R.string.error_password_digit)
    val genericError = stringResource(R.string.error_generic)
    val resetSuccess = stringResource(R.string.reset_success)

    AuthFormScaffold(stringResource(R.string.reset_title), stringResource(R.string.reset_subtitle), showBack = true, onBack = onBack) {
        OutlinedTextField(
            value = code, onValueChange = { code = it.filter { c -> c.isDigit() }; errorMsg = null },
            label = { Text(stringResource(R.string.field_code)) }, leadingIcon = { Icon(Icons.Default.Key, null) },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))
        PasswordField(value = password, onValueChange = { password = it; errorMsg = null }, label = stringResource(R.string.field_new_password))
        PasswordStrengthBar(password)
        Spacer(Modifier.height(20.dp))
        if (errorMsg != null) { Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(8.dp)) }
        if (successMsg != null) { Text(successMsg!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(12.dp)) }
        AuthButton(text = stringResource(R.string.reset_submit), loading = loading, onClick = {
            focus.clearFocus()
            if (code.isBlank() || password.isBlank()) { errorMsg = fillAll; return@AuthButton }
            val pwFail = validatePassword(password)
            if (pwFail != PwViolation.NONE) {
                errorMsg = when (pwFail) { PwViolation.LENGTH -> pwLength; PwViolation.UPPER -> pwUpper; PwViolation.LOWER -> pwLower; PwViolation.DIGIT -> pwDigit; else -> genericError }
                return@AuthButton
            }
            scope.launch {
                loading = true; errorMsg = null; successMsg = null
                try { repository.confirmPasswordReset(email, code.trim(), password); successMsg = resetSuccess; onResetSuccess() }
                catch (e: Exception) { errorMsg = e.message ?: genericError }
                finally { loading = false }
            }
        })
    }
}

// ---------------------------------------------------------------------------
// Email Verification Screen
// ---------------------------------------------------------------------------

@Composable
fun EmailVerificationScreen(
    repository: MediationRepository, email: String, onBack: () -> Unit,
    onVerified: () -> Unit, onResendSuccess: (devCode: String?) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var successMsg by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    val fillAll = stringResource(R.string.error_fill_all)
    val genericError = stringResource(R.string.error_generic)
    val verified = stringResource(R.string.verify_email_updated)
    val resendSent = stringResource(R.string.verify_resend_success)

    AuthFormScaffold(stringResource(R.string.verify_title), stringResource(R.string.verify_subtitle), showBack = true, onBack = onBack) {
        OutlinedTextField(
            value = code, onValueChange = { code = it.filter { c -> c.isDigit() }; errorMsg = null; successMsg = null },
            label = { Text(stringResource(R.string.field_code)) }, leadingIcon = { Icon(Icons.Default.Key, null) },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))
        if (errorMsg != null) { Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(8.dp)) }
        if (successMsg != null) { Text(successMsg!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(12.dp)) }
        AuthButton(text = stringResource(R.string.verify_submit), loading = loading, onClick = {
            focus.clearFocus()
            if (code.isBlank()) { errorMsg = fillAll; return@AuthButton }
            scope.launch {
                loading = true; errorMsg = null; successMsg = null
                try { repository.verifyEmail(email, code.trim()); successMsg = verified; onVerified() }
                catch (e: Exception) { errorMsg = e.message ?: genericError }
                finally { loading = false }
            }
        })
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = {
            scope.launch {
                loading = true
                try { val devCode = repository.resendVerificationCode(email); onResendSuccess(devCode); if (devCode == null) successMsg = resendSent }
                catch (_: Exception) { }
                finally { loading = false }
            }
        }) { Text(stringResource(R.string.verify_resend), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
    }
}