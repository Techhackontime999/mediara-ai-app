package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediationStatus
import com.example.data.model.Participant
import com.example.data.model.ParticipantStatus
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaraTopBar(
    title: String,
    subtitle: String? = null,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1
                    )
                }
            }
        },
        navigationIcon = {
            if (onBackClick != null) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.testTag("top_bar_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Navigate back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun MediationStatusBadge(
    status: MediationStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, label) = when (status) {
        MediationStatus.CREATED -> Triple(Color(0xFFE2E8F0), Color(0xFF334155), "Draft Created")
        MediationStatus.INVITED -> Triple(MediaraIndigoContainer, MediaraIndigoAccent, "Invited")
        MediationStatus.IN_PROGRESS -> Triple(MediaraTealContainer, MediaraTealDark, "Private Sessions")
        MediationStatus.ANALYZED -> Triple(Color(0xFFE0E7FF), Color(0xFF3730A3), "Analyzed")
        MediationStatus.PROPOSALS_READY -> Triple(MediaraAmberContainer, MediaraAmber, "Proposals Ready")
        MediationStatus.NEGOTIATING -> Triple(MediaraAmberContainer, MediaraAmber, "Negotiating")
        MediationStatus.RESOLVED -> Triple(Color(0xFFDCFCE7), Color(0xFF166534), "✓ Resolved")
        MediationStatus.FOLLOW_UP -> Triple(Color(0xFFE0F2FE), Color(0xFF075985), "Follow-Up Active")
        MediationStatus.REOPENED -> Triple(MediaraRoseContainer, MediaraRoseAlert, "↻ Reopened")
        MediationStatus.CLOSED -> Triple(Color(0xFFE2E8F0), Color(0xFF334155), "Closed")
        MediationStatus.SAFETY_HOLD -> Triple(MediaraRoseContainer, MediaraRoseAlert, "Safety Hold")
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = textColor
            ),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun PrivacyGuaranteeBanner(
    modifier: Modifier = Modifier,
    isDismissible: Boolean = false
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MediaraTealContainer.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MediaraTeal.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Confidentiality guarantee",
                    tint = MediaraTeal,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "End-to-End Private Session",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MediaraTealDark
                    )
                )
                Text(
                    text = "Raw messages are strictly confidential. Only synthesized common ground and structured insights are ever shared.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MediaraTealDark.copy(alpha = 0.85f),
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

@Composable
fun ParticipantItemRow(
    participant: Participant,
    isCurrentParticipant: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentParticipant) MediaraIndigoContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (isCurrentParticipant) BorderStroke(1.dp, MediaraIndigoAccent.copy(alpha = 0.5f)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isCurrentParticipant) MediaraIndigoAccent else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = participant.name.take(2).uppercase(),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrentParticipant) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = participant.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    if (isCurrentParticipant) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(You)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MediaraIndigoAccent,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
                Text(
                    text = participant.role,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Status chip
            val (statusText, statusBg, statusColor) = when {
                participant.hasCompletedPrivateSession -> Triple("Session Done", Color(0xFFDCFCE7), Color(0xFF166534))
                participant.status == ParticipantStatus.IN_SESSION -> Triple("In Session", MediaraAmberContainer, MediaraAmber)
                participant.status == ParticipantStatus.JOINED -> Triple("Joined", MediaraIndigoContainer, MediaraIndigoAccent)
                else -> Triple("Pending", Color(0xFFF1F5F9), Color(0xFF64748B))
            }

            Surface(
                color = statusBg,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        fontSize = 10.sp
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}
