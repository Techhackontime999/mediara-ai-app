package com.example.ui.mediation

import androidx.compose.foundation.background
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
import com.example.data.model.ConflictAnalysis
import com.example.data.repository.MediationRepository
import com.example.ui.components.MediaraTopBar
import com.example.ui.components.PrivacyGuaranteeBanner
import com.example.ui.theme.*

@Composable
fun ConflictAnalysisScreen(
    mediationId: String,
    repository: MediationRepository,
    onBackClick: () -> Unit,
    onProceedToProposals: () -> Unit
) {
    val mediationFlow = remember(mediationId) { repository.getMediationFlow(mediationId) }
    val mediation by mediationFlow.collectAsState(initial = null)

    val analysis = mediation?.analysis

    Scaffold(
        topBar = {
            MediaraTopBar(
                title = "Conflict Analysis",
                subtitle = "Synthesized Common Ground",
                onBackClick = onBackClick
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (analysis == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Analysis pending generation.")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Compatibility Score Hero Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MediaraTealContainer.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(MediaraTeal),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${analysis.compatibilityScore}%",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Strong Alignment Detected",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MediaraTealDark
                            )
                        )
                        Text(
                            text = "Both parties share deep common objectives. Divergences are operational rather than fundamental.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MediaraTealDark.copy(alpha = 0.9f),
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Privacy reminder
            PrivacyGuaranteeBanner()

            Spacer(modifier = Modifier.height(16.dp))

            // Common Ground Synthesis Box
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MediaraIndigoAccent.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Handshake,
                            contentDescription = null,
                            tint = MediaraIndigoAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Executive Synthesis",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = analysis.commonGroundSummary,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Common Goals
            AnalysisInsightCard(
                title = "Common Goals & Shared Values",
                items = analysis.commonGoals,
                icon = Icons.Default.CheckCircle,
                tint = Color(0xFF16A34A)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Conflicting Goals & Expectation Gaps
            AnalysisInsightCard(
                title = "Expectation Gaps & Divergent Paces",
                items = analysis.conflictingGoals + analysis.expectationGaps,
                icon = Icons.Default.SyncProblem,
                tint = MediaraAmber
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Root Causes & Misunderstandings
            AnalysisInsightCard(
                title = "Root Causes & Silent Misunderstandings",
                items = analysis.rootCauses + analysis.misunderstandings,
                icon = Icons.Default.Search,
                tint = MediaraIndigoAccent
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Emotional Factors
            AnalysisInsightCard(
                title = "Unexpressed Emotional Factors",
                items = analysis.emotionalFactors,
                icon = Icons.Default.FavoriteBorder,
                tint = Color(0xFF9333EA)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 5. Potential Compromises Identified
            AnalysisInsightCard(
                title = "High-Yield Potential Compromise Areas",
                items = analysis.potentialCompromises,
                icon = Icons.Default.Lightbulb,
                tint = MediaraTeal
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onProceedToProposals,
                colors = ButtonDefaults.buttonColors(containerColor = MediaraIndigoAccent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("analysis_proceed_proposals_button")
            ) {
                Icon(Icons.Default.HowToVote, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Review 3 Resolution Proposals", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AnalysisInsightCard(
    title: String,
    items: List<String>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color
) {
    if (items.isEmpty()) return

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            items.forEach { itemText ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                ) {
                    Text(
                        text = "•",
                        color = tint,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = itemText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    )
                }
            }
        }
    }
}
