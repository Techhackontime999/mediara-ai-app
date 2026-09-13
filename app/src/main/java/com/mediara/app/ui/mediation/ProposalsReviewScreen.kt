package com.mediara.app.ui.mediation

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediara.app.data.model.ResolutionProposal
import com.mediara.app.data.model.VoteType
import com.mediara.app.data.repository.MediationRepository
import com.mediara.app.ui.components.MediaraTopBar
import com.mediara.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ProposalsReviewScreen(
    mediationId: String,
    repository: MediationRepository,
    onBackClick: () -> Unit,
    onAgreementFinalized: () -> Unit
) {
    val mediationFlow = remember(mediationId) { repository.getMediationFlow(mediationId) }
    val mediation by mediationFlow.collectAsState(initial = null)
    val activeUser by repository.activeUser.collectAsState()

    var selectedProposalIndex by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    var feedbackText by remember { mutableStateOf("") }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var isSubmittingVote by remember { mutableStateOf(false) }
    var isRefining by remember { mutableStateOf(false) }
    var isFinalizing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Refresh so votes/casts from all accounts are reflected.
    LaunchedEffect(mediationId) {
        runCatching { repository.refreshMediation(mediationId) }
    }

    val med = mediation
    if (med == null || med.proposals.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Your own participant slot (the only identity you may vote/sign as).
    val currentParticipant = remember(med, activeUser) {
        val user = activeUser
        med.participants.find { p -> user != null && p.userId == user.id }
            ?: med.participants.find { it.name.equals(user?.name, ignoreCase = true) }
    }

    val currentProposal = med.proposals.getOrNull(selectedProposalIndex) ?: med.proposals.first()

    // Check if any proposal has been accepted by all participants
    val approvedProposal = remember(med.proposals) {
        med.proposals.find { it.isApproved }
    }

    Scaffold(
        topBar = {
            MediaraTopBar(
                title = "Resolution Proposals",
                subtitle = "3 Tailored Mediation Frameworks",
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
                .padding(16.dp)
        ) {
            // Success banner if approved
            if (approvedProposal != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF166534))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Mutual Consensus Achieved!",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF166534)
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "All participants accepted '${approvedProposal.title}'. The official Accord is ready for review, signing, and PDF generation.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF166534))
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (isFinalizing) return@Button
                                isFinalizing = true
                                scope.launch {
                                    try {
                                        repository.finalizeMediation(med.id, approvedProposal.id)
                                        onAgreementFinalized()
                                    } catch (e: Exception) {
                                        errorMessage = e.message ?: "Could not finalize the mediation."
                                    } finally {
                                        isFinalizing = false
                                    }
                                }
                            },
                            enabled = !isFinalizing,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF166534)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("proposal_view_accord_button")
                        ) {
                            if (isFinalizing) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                            } else {
                                Text("Open Ratified Accord & Generate PDF", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Error banner
            errorMessage?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MediaraRoseContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MediaraRoseAlert)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall.copy(color = MediaraRoseAlert),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Proposal Selector Tabs (1, 2, 3)
            TabRow(
                selectedTabIndex = selectedProposalIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                med.proposals.forEachIndexed { index, prop ->
                    Tab(
                        selected = selectedProposalIndex == index,
                        onClick = { selectedProposalIndex = index },
                        text = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Option ${index + 1}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = when (index) {
                                        0 -> "Balanced"
                                        1 -> "Cadence"
                                        else -> "Ownership"
                                    },
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier.testTag("proposal_tab_$index")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Proposal Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MediaraIndigoContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = currentProposal.modelType,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MediaraIndigoAccent
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        if (currentProposal.refinementIteration > 1) {
                            Surface(
                                color = MediaraAmberContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Iter ${currentProposal.refinementIteration}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MediaraAmber
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = currentProposal.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = currentProposal.description,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Benefits
                    Text(
                        text = "Key Benefits:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    currentProposal.benefits.forEach { b ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("✓ ", color = MediaraTeal, fontWeight = FontWeight.Bold)
                            Text(b, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Trade-offs
                    Text(
                        text = "Trade-offs & Considerations:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    currentProposal.tradeoffs.forEach { t ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("• ", color = MediaraAmber, fontWeight = FontWeight.Bold)
                            Text(t, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Required Compromises per Party
                    if (currentProposal.requiredCompromises.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Required Bilateral Compromises:",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                currentProposal.requiredCompromises.forEach { (party, compromise) ->
                                    Text(
                                        text = "$party: $compromise",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        ),
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Why It Works
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MediaraTeal,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Mediara AI Rationale",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MediaraTeal
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentProposal.whyItWorks,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Voting Status Matrix
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Participant Voting Status",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    med.participants.forEach { p ->
                        val vote = currentProposal.votes[p.id]
                        val feedback = currentProposal.feedback[p.id]

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = p.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                if (!feedback.isNullOrBlank()) {
                                    Text(
                                        text = "Note: \"$feedback\"",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }

                            val (label, bg, color) = when (vote) {
                                VoteType.ACCEPT -> Triple("✓ Accepted", Color(0xFFDCFCE7), Color(0xFF166534))
                                VoteType.REQUEST_CHANGES -> Triple("Changes Requested", MediaraAmberContainer, MediaraAmber)
                                VoteType.REJECT -> Triple("Declined", MediaraRoseContainer, MediaraRoseAlert)
                                null -> Triple("Awaiting Vote", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    text = label,
                                    color = color,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Voting Buttons for your own participation
            val myVote = currentProposal.votes[currentParticipant?.id]

            Text(
                text = "Cast Your Decision:",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Accept Button
                Button(
                    onClick = {
                        val pId = currentParticipant?.id ?: return@Button
                        isSubmittingVote = true
                        errorMessage = null
                        scope.launch {
                            try {
                                repository.voteOnProposal(med.id, currentProposal.id, pId, VoteType.ACCEPT, null)
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Could not submit your vote."
                            } finally {
                                isSubmittingVote = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (myVote == VoteType.ACCEPT) Color(0xFF166534) else MediaraTeal
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("proposal_accept_button")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Accept", fontWeight = FontWeight.Bold)
                }

                // Request Changes Button
                OutlinedButton(
                    onClick = { showFeedbackDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (myVote == VoteType.REQUEST_CHANGES) MediaraAmber else MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(48.dp)
                        .testTag("proposal_request_changes_button")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Request Changes", fontSize = 12.sp)
                }

                // Reject Button
                OutlinedButton(
                    onClick = {
                        val pId = currentParticipant?.id ?: return@OutlinedButton
                        isSubmittingVote = true
                        errorMessage = null
                        scope.launch {
                            try {
                                repository.voteOnProposal(
                                    med.id,
                                    currentProposal.id,
                                    pId,
                                    VoteType.REJECT,
                                    "Declined model type."
                                )
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Could not submit your vote."
                            } finally {
                                isSubmittingVote = false
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MediaraRoseAlert),
                    modifier = Modifier
                        .weight(0.9f)
                        .height(48.dp)
                        .testTag("proposal_reject_button")
                ) {
                    Text("Reject", fontSize = 12.sp)
                }
            }

            // AI Refine Button if changes requested
            if (currentProposal.feedback.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        isRefining = true
                        errorMessage = null
                        scope.launch {
                            try {
                                val notes = currentProposal.feedback.values.joinToString("; ")
                                repository.refineProposalWithFeedback(med.id, currentProposal.id, notes)
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Could not refine the proposal."
                            } finally {
                                isRefining = false
                            }
                        }
                    },
                    enabled = !isRefining,
                    colors = ButtonDefaults.buttonColors(containerColor = MediaraIndigoAccent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("proposal_refine_ai_button")
                ) {
                    if (isRefining) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI Refine Proposal with Feedback", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Feedback Dialog
    if (showFeedbackDialog) {
        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            title = { Text("Request Specific Adjustment") },
            text = {
                Column {
                    Text(
                        text = "Explain what modification or compromise would make this proposal acceptable to you:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = feedbackText,
                        onValueChange = { feedbackText = it },
                        placeholder = { Text("e.g., Adjust quiet hours from 10:00 PM to 10:30 PM...") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("feedback_dialog_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pId = currentParticipant?.id ?: return@Button
                        isSubmittingVote = true
                        errorMessage = null
                        scope.launch {
                            try {
                                repository.voteOnProposal(
                                    med.id,
                                    currentProposal.id,
                                    pId,
                                    VoteType.REQUEST_CHANGES,
                                    feedbackText
                                )
                                showFeedbackDialog = false
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Could not submit your vote."
                            } finally {
                                isSubmittingVote = false
                            }
                        }
                    }
                ) {
                    Text("Submit Adjustment")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFeedbackDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
