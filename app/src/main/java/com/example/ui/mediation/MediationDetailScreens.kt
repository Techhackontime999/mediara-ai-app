package com.example.ui.mediation

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Mediation
import com.example.data.model.MediationStatus
import com.example.data.model.Participant
import com.example.data.repository.MediationRepository
import com.example.ui.components.MediationStatusBadge
import com.example.ui.components.MediaraTopBar
import com.example.ui.components.ParticipantItemRow
import com.example.ui.components.PrivacyGuaranteeBanner
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun CreateMediationScreen(
    repository: MediationRepository,
    onMediationCreated: (String) -> Unit,
    onBackClick: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Roommates") }
    var participant2Name by remember { mutableStateOf("") }
    var participant3Name by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val categories = listOf("Roommates", "Workplace", "Co-founders", "Family", "Friendship", "Project Team")
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            MediaraTopBar(
                title = "New Mediation",
                subtitle = "Initiate autonomous conflict resolution",
                onBackClick = onBackClick
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(
                text = "Conflict Overview",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it; errorMessage = null },
                label = { Text("Conflict Title (e.g., Apt 4B Noise & Cleaning)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_mediation_title_input")
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it; errorMessage = null },
                label = { Text("What is the core issue or tension?") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_mediation_desc_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Category",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Category Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.take(3).forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat, fontSize = 12.sp) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.drop(3).forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Other Participant(s)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "You will be automatically added as Participant A. Add the other parties below:",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = participant2Name,
                onValueChange = { participant2Name = it },
                label = { Text("Participant B Name (e.g. Jordan Lee)") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_mediation_part2_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = participant3Name,
                onValueChange = { participant3Name = it },
                label = { Text("Participant C Name (Optional)") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )

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
                    if (title.isBlank() || description.isBlank() || participant2Name.isBlank()) {
                        errorMessage = "Please fill in title, description, and at least one other participant."
                    } else {
                        isSubmitting = true
                        errorMessage = null
                        scope.launch {
                            try {
                                val others = listOf(participant2Name, participant3Name).filter { it.isNotBlank() }
                                val created = repository.createMediation(title, description, category, others)
                                onMediationCreated(created.id)
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Unable to create the mediation."
                            } finally {
                                isSubmitting = false
                            }
                        }
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = MediaraIndigoAccent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("create_mediation_submit_button")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Text("Create Mediation & Get Invite Code", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun JoinMediationScreen(
    repository: MediationRepository,
    onMediationJoined: (String) -> Unit,
    onRequireLogin: () -> Unit,
    onBackClick: () -> Unit
) {
    var inviteCode by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("Participant") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isJoining by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val signedIn = remember(repository) { repository.isAuthenticated() }

    Scaffold(
        topBar = {
            MediaraTopBar(
                title = "Join Mediation",
                subtitle = "Enter case with invite code",
                onBackClick = onBackClick
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MediaraTealContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = null,
                    tint = MediaraTealDark,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Enter Case Code",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )

            Text(
                text = "Enter the 5-6 character case code shared by the session initiator.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            )

            if (!signedIn) {
                Spacer(modifier = Modifier.height(20.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MediaraIndigoContainer.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Sign in required to join a mediation",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "You need an account so the invited slot can be tied to your identity.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onRequireLogin,
                            colors = ButtonDefaults.buttonColors(containerColor = MediaraIndigoAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("join_require_login_button")
                        ) {
                            Text("Sign In", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { inviteCode = it.uppercase(); errorMessage = null },
                    label = { Text("Invite Code (e.g. APT4B)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("join_code_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it },
                    label = { Text("Your Role (e.g., Resident B, Co-founder)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

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
                        if (inviteCode.isBlank()) {
                            errorMessage = "Please enter the invite code."
                        } else {
                            isJoining = true
                            errorMessage = null
                            scope.launch {
                                try {
                                    val joined = repository.joinMediationByCode(inviteCode, role)
                                    onMediationJoined(joined.id)
                                } catch (e: Exception) {
                                    errorMessage = e.message ?: "Unable to join that mediation."
                                } finally {
                                    isJoining = false
                                }
                            }
                        }
                    },
                    enabled = !isJoining,
                    colors = ButtonDefaults.buttonColors(containerColor = MediaraIndigoAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("join_submit_button")
                ) {
                    if (isJoining) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Text("Join Case", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun MediationDetailScreen(
    mediationId: String,
    repository: MediationRepository,
    onBackClick: () -> Unit,
    onStartPrivateSession: (participantId: String) -> Unit,
    onViewAnalysis: () -> Unit,
    onViewProposals: () -> Unit,
    onViewAgreement: () -> Unit,
    onFollowUpClick: (participantId: String) -> Unit,
    onSafetyClick: () -> Unit
) {
    val mediationFlow = remember(mediationId) { repository.getMediationFlow(mediationId) }
    val mediation by mediationFlow.collectAsState(initial = null)
    val activeUser by repository.activeUser.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isAnalyzing by remember { mutableStateOf(false) }

    LaunchedEffect(mediationId) {
        runCatching { repository.refreshMediation(mediationId) }
    }

    // The participant belonging to the signed-in account (via userId), with a
    // name fallback for mediation records created before claiming took effect.
    val myParticipant = remember(mediation, activeUser) {
        mediation?.participants?.find { p -> activeUser != null && p.userId == activeUser.id }
            ?: mediation?.participants?.find { p -> p.name.equals(activeUser?.name, ignoreCase = true) }
    }

    val currentParticipant = myParticipant ?: mediation?.participants?.firstOrNull()

    if (mediation == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val med = mediation!!

    Scaffold(
        topBar = {
            MediaraTopBar(
                title = med.title,
                subtitle = "Case Code: ${med.inviteCode}",
                onBackClick = onBackClick,
                actions = {
                    IconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Join Mediara AI Mediation: ${med.title}")
                                putExtra(Intent.EXTRA_TEXT, "You are invited to join confidential AI mediation for '${med.title}'. Case Code: ${med.inviteCode}")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Case Code"))
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share case code")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // High Risk Alert Banner if triggered
            if (med.safetyAlert?.isHighRisk == true) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MediaraRoseContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSafetyClick() }
                        .padding(bottom = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MediaraRoseAlert)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Safety Alert Flagged", fontWeight = FontWeight.Bold, color = MediaraRoseAlert)
                            Text("High-risk language detected. Active mediation paused for participant protection.", fontSize = 11.sp, color = MediaraRoseAlert)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MediaraRoseAlert)
                    }
                }
            }

            // Case Header Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = med.category,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        MediationStatusBadge(status = med.status)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = med.description,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Case Invite Code:",
                            style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                        Text(
                            text = med.inviteCode,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MediaraIndigoAccent,
                                letterSpacing = 2.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Workflow Step Indicator
            WorkflowStepCard(status = med.status)

            Spacer(modifier = Modifier.height(16.dp))

            // Privacy guarantee
            PrivacyGuaranteeBanner()

            Spacer(modifier = Modifier.height(18.dp))

            // Participants Section
            Text(
                text = "Participants & Session Progress",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))

            med.participants.forEach { p ->
                val isMe = p.userId?.let { it == activeUser?.id } == true ||
                    p.name.equals(activeUser?.name, ignoreCase = true)
                ParticipantItemRow(
                    participant = p,
                    isCurrentParticipant = isMe,
                    onClick = {
                        if (isMe) {
                            onStartPrivateSession(p.id)
                        } else {
                            Toast.makeText(
                                context,
                                "Private sessions are strictly tied to your own account.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Contextual Action Button
            when {
                myParticipant != null && myParticipant.hasCompletedPrivateSession != true -> {
                    Button(
                        onClick = {
                            myParticipant?.id?.let { onStartPrivateSession(it) }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MediaraIndigoAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("detail_start_session_button")
                    ) {
                        Icon(Icons.Default.ChatBubble, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enter My Private AI Session", fontWeight = FontWeight.Bold)
                    }
                }

                med.analysis == null -> {
                    Button(
                        onClick = {
                            isAnalyzing = true
                            scope.launch {
                                try {
                                    repository.runConflictAnalysis(med.id)
                                    onViewAnalysis()
                                } catch (e: Exception) {
                                    Toast.makeText(context, e.message ?: "Analysis failed.", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isAnalyzing = false
                                }
                            }
                        },
                        enabled = !isAnalyzing,
                        colors = ButtonDefaults.buttonColors(containerColor = MediaraTeal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("detail_analyze_button")
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.Psychology, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Synthesize Conflict Analysis & Proposals", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                med.status == MediationStatus.RESOLVED ||
                    med.status == MediationStatus.CLOSED ||
                    med.status == MediationStatus.FOLLOW_UP -> {
                    Button(
                        onClick = onViewAgreement,
                        colors = ButtonDefaults.buttonColors(containerColor = MediaraIndigoAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("detail_view_agreement_button")
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View Ratified Accord & PDF", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            currentParticipant?.id?.let { onFollowUpClick(it) }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("detail_follow_up_button")
                    ) {
                        Icon(Icons.Default.RateReview, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Submit 14-Day Efficacy Check-in")
                    }
                }

                else -> {
                    Button(
                        onClick = onViewProposals,
                        colors = ButtonDefaults.buttonColors(containerColor = MediaraAmber),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("detail_review_proposals_button")
                    ) {
                        Icon(Icons.Default.HowToVote, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Review 3 Resolution Proposals", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onViewAnalysis,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Analytics, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View Conflict Analysis & Common Ground")
                    }
                }
            }
        }
    }
}

@Composable
fun WorkflowStepCard(status: MediationStatus) {
    val stepIndex = when (status) {
        MediationStatus.CREATED, MediationStatus.INVITED, MediationStatus.SAFETY_HOLD -> 1
        MediationStatus.IN_PROGRESS -> 2
        MediationStatus.ANALYZED, MediationStatus.PROPOSALS_READY, MediationStatus.NEGOTIATING -> 3
        MediationStatus.RESOLVED, MediationStatus.FOLLOW_UP, MediationStatus.REOPENED, MediationStatus.CLOSED -> 4
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Mediation Pipeline",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepIndicatorItem(number = "1", label = "Sessions", isActive = stepIndex >= 1, isCurrent = stepIndex == 1)
                StepConnector(isActive = stepIndex >= 2)
                StepIndicatorItem(number = "2", label = "Analysis", isActive = stepIndex >= 2, isCurrent = stepIndex == 2)
                StepConnector(isActive = stepIndex >= 3)
                StepIndicatorItem(number = "3", label = "Proposals", isActive = stepIndex >= 3, isCurrent = stepIndex == 3)
                StepConnector(isActive = stepIndex >= 4)
                StepIndicatorItem(number = "4", label = "Accord", isActive = stepIndex >= 4, isCurrent = stepIndex == 4)
            }
        }
    }
}

@Composable
private fun StepIndicatorItem(
    number: String,
    label: String,
    isActive: Boolean,
    isCurrent: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isCurrent -> MediaraIndigoAccent
                        isActive -> MediaraTeal
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isActive || isCurrent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isActive || isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
private fun RowScope.StepConnector(isActive: Boolean) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(2.dp)
            .background(if (isActive) MediaraTeal else MaterialTheme.colorScheme.surfaceVariant)
    )
}
