package com.example.ui.mediation

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.window.Dialog
import com.example.data.model.Message
import com.example.data.model.MessageSender
import com.example.data.repository.MediationRepository
import com.example.ui.components.MediaraTopBar
import com.example.ui.components.PrivacyGuaranteeBanner
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PrivateSessionChatScreen(
    mediationId: String,
    participantId: String,
    repository: MediationRepository,
    onBackClick: () -> Unit,
    onSessionCompleted: () -> Unit,
    onSafetyAlertTriggered: () -> Unit
) {
    val mediationFlow = remember(mediationId) { repository.getMediationFlow(mediationId) }
    val mediation by mediationFlow.collectAsState(initial = null)
    val messagesFlow = remember(mediationId, participantId) {
        repository.getParticipantMessages(mediationId, participantId)
    }
    val messages by messagesFlow.collectAsState(initial = emptyList())

    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var showNvcDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val participant = remember(mediation, participantId) {
        mediation?.participants?.find { it.id == participantId }
    }

    // Pull the existing conversation from the server (or create on first run)
    LaunchedEffect(mediationId, participantId) {
        runCatching { repository.ensureConversationLoaded(mediationId, participantId) }
    }

    // Auto-scroll when messages update
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            MediaraTopBar(
                title = "Private Caucus: ${participant?.name ?: "Participant"}",
                subtitle = "🔒 Confidential AI Intake",
                onBackClick = onBackClick,
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                try {
                                    repository.completePrivateSession(mediationId, participantId)
                                    onSessionCompleted()
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        e.message ?: "Could not complete the session.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier.testTag("chat_complete_session_button")
                    ) {
                        Text(
                            text = "Finish Caucus",
                            fontWeight = FontWeight.Bold,
                            color = MediaraIndigoAccent
                        )
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
        ) {
            // Confidentiality reminder & Climate meter
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                PrivacyGuaranteeBanner()

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MediaraTeal)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Intake Climate: Constructive & Open",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 11.sp
                                )
                            )
                        }

                        TextButton(
                            onClick = { showNvcDialog = true },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = MediaraIndigoAccent, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "NVC Coach",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MediaraIndigoAccent,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }

            // Chat Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        InitialAiWelcomeMessage(participantName = participant?.name ?: "there")
                    }
                }

                items(messages, key = { it.id }) { message ->
                    ChatMessageBubble(message = message)
                }

                if (isSending) {
                    item {
                        AiTypingIndicator()
                    }
                }
            }

            // Suggested prompt chips for the user
            SuggestedPromptChips(
                onPromptSelected = { prompt ->
                    inputText = prompt
                },
                onOpenNvc = { showNvcDialog = true }
            )

            // Input Row
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                "Share your perspective, feelings, or needs...",
                                fontSize = 13.sp
                            )
                        },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_message_input")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            val textToSend = inputText.trim()
                            if (textToSend.isNotBlank() && !isSending) {
                                inputText = ""
                                isSending = true
                                scope.launch {
                                    try {
                                        val (userMsg, aiMsg) = repository.sendPrivateMessage(
                                            mediationId,
                                            participantId,
                                            textToSend
                                        )
                                        isSending = false
                                        if (userMsg.isRiskFlagged) {
                                            onSafetyAlertTriggered()
                                        }
                                    } catch (e: Exception) {
                                        isSending = false
                                        Toast.makeText(
                                            context,
                                            e.message ?: "Message failed to send.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        },
                        enabled = inputText.isNotBlank() && !isSending,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (inputText.isNotBlank()) MediaraIndigoAccent else MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("chat_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send message",
                            tint = if (inputText.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showNvcDialog) {
        NvcHelperDialog(
            onDismiss = { showNvcDialog = false },
            onApply = { generatedText ->
                inputText = generatedText
                showNvcDialog = false
            }
        )
    }
}

@Composable
fun InitialAiWelcomeMessage(participantName: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MediaraTeal.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MediaraTealContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = MediaraTealDark,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Mediara AI Intake Facilitator",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Hello $participantName. I'm here to understand your side of the situation without judgment or blame. Everything you share in this chat is completely confidential.",
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "To help us construct a lasting resolution: what happened from your viewpoint, and what has been most frustrating or concerning for you?",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MediaraIndigoAccent
                )
            )
        }
    }
}

@Composable
fun ChatMessageBubble(message: Message) {
    val isUser = message.sender == MessageSender.USER
    val isRisk = message.isRiskFlagged

    val bubbleAlignment = if (isUser) Alignment.End else Alignment.Start
    val bubbleColor = when {
        isRisk -> MediaraRoseContainer
        isUser -> MediaraIndigoAccent
        else -> MaterialTheme.colorScheme.surface
    }
    val textColor = when {
        isRisk -> MediaraRoseAlert
        isUser -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }

    val timeStr = remember(message.timestamp) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = bubbleAlignment
    ) {
        // Tag badge if category was extracted
        if (!message.detectedCategory.isNullOrBlank() && !isRisk) {
            Surface(
                color = MediaraTealContainer.copy(alpha = 0.8f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.padding(bottom = 3.dp)
            ) {
                Text(
                    text = "Insight Captured: ${message.detectedCategory}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MediaraTealDark
                    ),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            tonalElevation = if (isUser) 0.dp else 1.dp,
            border = if (!isUser && !isRisk) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (!isUser) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MediaraTeal,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Mediara AI",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MediaraTeal,
                                fontSize = 10.5.sp
                            )
                        )
                    }
                }

                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = textColor,
                        lineHeight = 20.sp
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = textColor.copy(alpha = 0.7f),
                        fontSize = 9.sp
                    ),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
fun AiTypingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MediaraTeal
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Mediara AI is listening and analyzing...",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

@Composable
fun SuggestedPromptChips(
    onPromptSelected: (String) -> Unit,
    onOpenNvc: () -> Unit
) {
    val prompts = listOf(
        "What feels most unfair to me is...",
        "I would be willing to compromise on...",
        "My primary goal is...",
        "I feel the other party misunderstands..."
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SuggestionChip(
            onClick = onOpenNvc,
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(12.dp), tint = MediaraIndigoAccent)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("NVC Coach", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MediaraIndigoAccent)
                }
            }
        )

        prompts.take(2).forEach { prompt ->
            SuggestionChip(
                onClick = { onPromptSelected(prompt) },
                label = { Text(prompt, fontSize = 11.sp, maxLines = 1) }
            )
        }
    }
}

@Composable
fun NvcHelperDialog(
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    var observation by remember { mutableStateOf("") }
    var feeling by remember { mutableStateOf("") }
    var need by remember { mutableStateOf("") }
    var request by remember { mutableStateOf("") }

    val fullMessage = remember(observation, feeling, need, request) {
        buildString {
            if (observation.isNotBlank()) append("When $observation, ")
            if (feeling.isNotBlank()) append("I feel $feeling ")
            if (need.isNotBlank()) append("because I need $need. ")
            if (request.isNotBlank()) append("Would you be open to $request?")
        }.trim()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Nonviolent Communication Coach",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "De-escalate blame into clear needs",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = observation,
                    onValueChange = { observation = it },
                    label = { Text("1. Observation (Facts without evaluation)", fontSize = 12.sp) },
                    placeholder = { Text("e.g., dishes were left unwashed yesterday", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = feeling,
                    onValueChange = { feeling = it },
                    label = { Text("2. Feeling (Emotional reality)", fontSize = 12.sp) },
                    placeholder = { Text("e.g., overwhelmed and unappreciated", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = need,
                    onValueChange = { need = it },
                    label = { Text("3. Underlying Need (Values)", fontSize = 12.sp) },
                    placeholder = { Text("e.g., predictability and shared order", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = request,
                    onValueChange = { request = it },
                    label = { Text("4. Actionable Request (Positive action)", fontSize = 12.sp) },
                    placeholder = { Text("e.g., rinsing plates right after cooking", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (fullMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MediaraTealContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Reframed Draft:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MediaraTealDark))
                            Text(fullMessage, style = MaterialTheme.typography.bodySmall.copy(color = MediaraTealDark, lineHeight = 16.sp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (fullMessage.isNotBlank()) {
                            onApply(fullMessage)
                        }
                    },
                    enabled = fullMessage.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MediaraIndigoAccent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Insert into Caucus Chat", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
