package com.mediara.app.ui.mediation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mediara.app.R
import com.mediara.app.data.model.FollowUpSentiment
import com.mediara.app.data.model.MutualAgreement
import com.mediara.app.data.pdf.PdfAgreementGenerator
import com.mediara.app.data.repository.MediationRepository
import com.mediara.app.data.safety.SafetyDetector
import com.mediara.app.ui.components.MediaraTopBar
import com.mediara.app.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AgreementResolvedScreen(
    mediationId: String,
    repository: MediationRepository,
    onBackClick: () -> Unit,
    onOpenPdfPreview: () -> Unit,
    onOpenFollowUp: () -> Unit
) {
    val mediationFlow = remember(mediationId) { repository.getMediationFlow(mediationId) }
    val mediation by mediationFlow.collectAsState(initial = null)
    val activeUser by repository.activeUser.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val agreement = mediation?.agreement
    val med = mediation

    if (med == null || agreement == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val currentParticipant = remember(med, activeUser) {
        val user = activeUser
        med.participants.find { p -> user != null && p.userId == user.id }
            ?: med.participants.find { it.name.equals(user?.name, ignoreCase = true) }
            ?: med.participants.firstOrNull()
    }

    val isSignedByMe = agreement.signatures.containsKey(currentParticipant?.id)
    var showSignaturePad by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            MediaraTopBar(
                title = "Mutual Resolution Accord",
                subtitle = "Ratified & Binding Accord",
                onBackClick = onBackClick,
                actions = {
                    IconButton(
                        onClick = onOpenPdfPreview,
                        modifier = Modifier.testTag("accord_pdf_icon_button")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF document", tint = MediaraIndigoAccent)
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
            // Success Seal Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MediaraIndigo),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .border(2.dp, MediaraTealLight.copy(alpha = 0.8f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.mediara_accord_seal),
                            contentDescription = "Official Resolution Accord Seal",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Official Resolution Accord",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Model: ${agreement.resolutionTitle}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MediaraTealContainer
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Authenticated by Mediara AI Multi-Party Consensus Protocol",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onOpenPdfPreview,
                        colors = ButtonDefaults.buttonColors(containerColor = MediaraTeal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("accord_open_pdf_button")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate & Open Official PDF", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Agreed Terms
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "1. Ratified Terms & Principles",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    agreement.agreedTerms.forEachIndexed { idx, term ->
                        Row(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text("${idx + 1}. ", fontWeight = FontWeight.Bold, color = MediaraIndigoAccent)
                            Text(term, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Individual Duties
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "2. Specific Participant Allocations",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    agreement.participantResponsibilities.forEach { (name, duties) ->
                        Text(
                            text = "Responsibilities for $name:",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MediaraTealDark
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        duties.forEach { duty ->
                            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                Text("• ", color = MediaraTeal, fontWeight = FontWeight.Bold)
                                Text(duty, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Milestones
            if (agreement.milestones.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "3. Review Milestones & Cadence",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        agreement.milestones.forEach { m ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("• ${m.title}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text(m.dueDate, style = MaterialTheme.typography.labelSmall, color = MediaraIndigoAccent)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // 4. Dispute Escalation Protocol
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "4. Dispute Escalation Clause",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = agreement.disputeEscalationClause,
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Electronic Signatures
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "5. Signatures & Verification",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    med.participants.forEach { p ->
                        val signedTime = agreement.signatures[p.id]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(p.role, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            if (signedTime != null) {
                                Surface(color = Color(0xFFDCFCE7), shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        text = "✓ Signed",
                                        color = Color(0xFF166534),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            } else {
                                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        text = "Pending Signature",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (!isSignedByMe && currentParticipant != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                showSignaturePad = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MediaraIndigoAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("sign_agreement_button")
                        ) {
                            Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Draw & Adopt Signature (${currentParticipant.name})", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action: Submit Follow-Up
            OutlinedButton(
                onClick = onOpenFollowUp,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("accord_goto_followup_button")
            ) {
                Icon(Icons.Default.RateReview, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("14-Day Efficacy Check-in & Follow-Up", fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showSignaturePad && currentParticipant != null) {
        SignaturePadDialog(
            participantName = currentParticipant.name,
            onDismiss = { showSignaturePad = false },
            onSignatureAdopted = {
                scope.launch {
                    try {
                        repository.signAgreement(med.id, currentParticipant.id)
                        showSignaturePad = false
                        Toast.makeText(context, "Accord ratified and cryptographically sealed!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            e.message ?: "Could not ratify the accord.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }
}

@Composable
fun SignaturePadDialog(
    participantName: String,
    onDismiss: () -> Unit,
    onSignatureAdopted: () -> Unit
) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }

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
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Digital Signature Pad",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Sign for $participantName",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Interactive Drawing Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFCFCFD))
                        .border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentStroke = listOf(offset)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentStroke = currentStroke + change.position
                                },
                                onDragEnd = {
                                    if (currentStroke.isNotEmpty()) {
                                        strokes.add(currentStroke)
                                        currentStroke = emptyList()
                                    }
                                },
                                onDragCancel = {
                                    currentStroke = emptyList()
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Guide line
                        drawLine(
                            color = Color(0xFFCBD5E1),
                            start = Offset(20f, size.height * 0.75f),
                            end = Offset(size.width - 20f, size.height * 0.75f),
                            strokeWidth = 2f
                        )

                        // Draw completed strokes
                        strokes.forEach { strokePoints ->
                            if (strokePoints.size > 1) {
                                val path = Path().apply {
                                    moveTo(strokePoints.first().x, strokePoints.first().y)
                                    for (i in 1 until strokePoints.size) {
                                        lineTo(strokePoints[i].x, strokePoints[i].y)
                                    }
                                }
                                drawPath(
                                    path = path,
                                    color = Color(0xFF1E1B4B),
                                    style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }

                        // Draw active current stroke
                        if (currentStroke.size > 1) {
                            val path = Path().apply {
                                moveTo(currentStroke.first().x, currentStroke.first().y)
                                for (i in 1 until currentStroke.size) {
                                    lineTo(currentStroke[i].x, currentStroke[i].y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = Color(0xFF1E1B4B),
                                style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }

                    if (strokes.isEmpty() && currentStroke.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✍️ Draw your signature here with finger",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            strokes.clear()
                            currentStroke = emptyList()
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear")
                    }

                    Surface(
                        color = MediaraTealContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "256-Bit Cryptographic Hash",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MediaraTealDark,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onSignatureAdopted,
                    enabled = strokes.isNotEmpty() || currentStroke.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = MediaraIndigoAccent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("signature_dialog_adopt_button")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Adopt Signature & Ratify Accord", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AgreementPdfPreviewScreen(
    mediationId: String,
    repository: MediationRepository,
    onBackClick: () -> Unit
) {
    val mediationFlow = remember(mediationId) { repository.getMediationFlow(mediationId) }
    val mediation by mediationFlow.collectAsState(initial = null)
    val context = LocalContext.current

    var generatedFile by remember { mutableStateOf<File?>(null) }
    var isGenerating by remember { mutableStateOf(false) }

    val med = mediation
    val agreement = med?.agreement

    LaunchedEffect(med?.id, agreement?.id) {
        if (med != null && agreement != null) {
            isGenerating = true
            try {
                // Prefer the official backend-generated PDF; fall back to the local generator.
                generatedFile = runCatching { repository.downloadAgreementPdf(med.id) }.getOrNull()
                    ?: PdfAgreementGenerator.generateAgreementPdf(context, med, agreement)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isGenerating = false
            }
        }
    }

    Scaffold(
        topBar = {
            MediaraTopBar(
                title = "Agreement PDF",
                subtitle = "Official Signed Document",
                onBackClick = onBackClick
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Document Preview Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFEE2E2)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = generatedFile?.name ?: "Mediara_Agreement.pdf",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (generatedFile != null) "Size: ${(generatedFile!!.length() / 1024) + 1} KB • Verified Authentic" else "Generating PDF...",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF166534), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SHA-256 Digital Verification Embedded",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF166534),
                                    fontSize = 10.5.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Action: Open in External PDF Viewer
            Button(
                onClick = {
                    val file = generatedFile ?: return@Button
                    try {
                        val intent = PdfAgreementGenerator.createOpenPdfIntent(context, file)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "No PDF viewer found on device. PDF saved to cache.", Toast.LENGTH_LONG).show()
                    }
                },
                enabled = generatedFile != null,
                colors = ButtonDefaults.buttonColors(containerColor = MediaraIndigoAccent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("pdf_open_intent_button")
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open in PDF Viewer", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action: Share PDF
            OutlinedButton(
                onClick = {
                    val file = generatedFile ?: return@OutlinedButton
                    val intent = PdfAgreementGenerator.createSharePdfIntent(
                        context,
                        file,
                        med?.title ?: "Conflict Resolution"
                    )
                    context.startActivity(Intent.createChooser(intent, "Share Agreement PDF"))
                },
                enabled = generatedFile != null,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("pdf_share_intent_button")
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share Document via Android Sheet", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action: Download Notice
            TextButton(
                onClick = {
                    val path = generatedFile?.absolutePath ?: "Cache Directory"
                    Toast.makeText(context, "Saved at: $path", Toast.LENGTH_LONG).show()
                }
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("View Storage Location (${generatedFile?.parentFile?.name})", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun FollowUpScreen(
    mediationId: String,
    participantId: String,
    repository: MediationRepository,
    onBackClick: () -> Unit,
    onMediationReopened: () -> Unit
) {
    val mediationFlow = remember(mediationId) { repository.getMediationFlow(mediationId) }
    val mediation by mediationFlow.collectAsState(initial = null)

    var selectedSentiment by remember { mutableStateOf(FollowUpSentiment.WORKING) }
    var notes by remember { mutableStateOf("") }
    var requestReopen by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val med = mediation ?: return

    Scaffold(
        topBar = {
            MediaraTopBar(
                title = "14-Day Efficacy Check-in",
                subtitle = "Resolution Accountability",
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
                text = "How is the agreement holding up in practice?",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Periodic check-ins ensure that quiet hours, chores, and communication rules remain functional. If unexpected friction has surfaced, you may re-open the mediation without penalty.",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 3 Sentiments: Working, Partially, Not Working
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SentimentCard(
                    label = "Working",
                    emoji = "😊",
                    color = Color(0xFF166534),
                    isSelected = selectedSentiment == FollowUpSentiment.WORKING,
                    onClick = { selectedSentiment = FollowUpSentiment.WORKING; requestReopen = false },
                    modifier = Modifier.weight(1f)
                )

                SentimentCard(
                    label = "Partially",
                    emoji = "😐",
                    color = MediaraAmber,
                    isSelected = selectedSentiment == FollowUpSentiment.PARTIALLY_WORKING,
                    onClick = { selectedSentiment = FollowUpSentiment.PARTIALLY_WORKING },
                    modifier = Modifier.weight(1f)
                )

                SentimentCard(
                    label = "Not Working",
                    emoji = "😞",
                    color = MediaraRoseAlert,
                    isSelected = selectedSentiment == FollowUpSentiment.NOT_WORKING,
                    onClick = { selectedSentiment = FollowUpSentiment.NOT_WORKING; requestReopen = true },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Describe practical observations or recurring sticking points") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth().testTag("followup_notes_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Reopen checkbox
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { requestReopen = !requestReopen },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = requestReopen,
                    onCheckedChange = { requestReopen = it }
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "Request Mediation Reopening",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "Initiates a new cycle of AI private intake and adjusted proposal calibration.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    isSubmitting = true
                    scope.launch {
                        try {
                            repository.submitFollowUp(
                                mediationId,
                                participantId,
                                selectedSentiment,
                                notes,
                                requestReopen
                            )
                            Toast.makeText(context, "Follow-up recorded successfully!", Toast.LENGTH_SHORT).show()
                            if (requestReopen || selectedSentiment == FollowUpSentiment.NOT_WORKING) {
                                onMediationReopened()
                            } else {
                                onBackClick()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                e.message ?: "Could not submit your follow-up.",
                                Toast.LENGTH_SHORT
                            ).show()
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (requestReopen) MediaraRoseAlert else MediaraIndigoAccent
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("followup_submit_button")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Text(
                        text = if (requestReopen) "Submit & Re-open Mediation" else "Submit Check-in Report",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SentimentCard(
    label: String,
    emoji: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, color) else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = emoji, fontSize = 28.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) color else MaterialTheme.colorScheme.onSurface
                ),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun SafetyInterventionScreen(
    mediationId: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val resources = SafetyDetector.standardEmergencyResources

    Scaffold(
        topBar = {
            MediaraTopBar(
                title = "Safety Protection Protocol",
                subtitle = "Human Crisis & Emergency Support",
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
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MediaraRoseContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MediaraRoseAlert, modifier = Modifier.size(34.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Safety Protection Notice",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = MediaraRoseAlert)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Mediara AI has paused active mediation for this case. Our automated safety checks detected language related to potential harm, coercion, or danger. Mediara AI is an interpersonal facilitator, not a crisis, legal, or physical safety agency.",
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Immediate Crisis & Protection Hotlines:",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(12.dp))

            resources.forEach { resource ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(resource.title, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(resource.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (resource.phoneNumber.isNotBlank()) {
                                Button(
                                    onClick = {
                                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${resource.phoneNumber.replace("-", "")}"))
                                        context.startActivity(dialIntent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MediaraRoseAlert),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Call ${resource.phoneNumber}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (resource.textNumber.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:741741"))
                                        context.startActivity(smsIntent)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(resource.textNumber, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
