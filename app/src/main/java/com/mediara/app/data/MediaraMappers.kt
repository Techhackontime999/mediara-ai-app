package com.mediara.app.data

import com.mediara.app.data.model.*
import com.mediara.app.data.remote.*
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

// --- Time ---

internal fun parseIso(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    runCatching { return OffsetDateTime.parse(value).toInstant().toEpochMilli() }
    runCatching { return java.time.Instant.parse(value).toEpochMilli() }
    runCatching {
        return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .toInstant(java.time.ZoneOffset.UTC)
            .toEpochMilli()
    }
    return null
}

// --- Enums ---

internal fun mediationStatusFrom(value: String?): MediationStatus = when (value) {
    "CREATED" -> MediationStatus.CREATED
    "INVITED" -> MediationStatus.INVITED
    "IN_PROGRESS" -> MediationStatus.IN_PROGRESS
    "ANALYZED" -> MediationStatus.ANALYZED
    "PROPOSALS_READY" -> MediationStatus.PROPOSALS_READY
    "NEGOTIATING" -> MediationStatus.NEGOTIATING
    "RESOLVED" -> MediationStatus.RESOLVED
    "FOLLOW_UP" -> MediationStatus.FOLLOW_UP
    "REOPENED" -> MediationStatus.REOPENED
    "CLOSED" -> MediationStatus.CLOSED
    "SAFETY_HOLD" -> MediationStatus.SAFETY_HOLD
    else -> MediationStatus.CREATED
}

internal fun participantStatusFrom(value: String?): ParticipantStatus = when (value) {
    "INVITED" -> ParticipantStatus.INVITED
    "JOINED" -> ParticipantStatus.JOINED
    "IN_SESSION" -> ParticipantStatus.IN_SESSION
    "SESSION_COMPLETED" -> ParticipantStatus.SESSION_COMPLETED
    "REVIEWED" -> ParticipantStatus.REVIEWED
    "SIGNED" -> ParticipantStatus.SIGNED
    else -> ParticipantStatus.JOINED
}

internal fun voteTypeFrom(value: String?): VoteType = when (value?.uppercase()) {
    "REQUEST_CHANGES" -> VoteType.REQUEST_CHANGES
    "REJECT" -> VoteType.REJECT
    else -> VoteType.ACCEPT
}

internal fun VoteType.toDtoValue(): String = when (this) {
    VoteType.ACCEPT -> "ACCEPT"
    VoteType.REQUEST_CHANGES -> "REQUEST_CHANGES"
    VoteType.REJECT -> "REJECT"
}

internal fun sentimentFrom(value: String?): FollowUpSentiment = when (value) {
    "PARTIALLY_WORKING" -> FollowUpSentiment.PARTIALLY_WORKING
    "NOT_WORKING" -> FollowUpSentiment.NOT_WORKING
    else -> FollowUpSentiment.WORKING
}

internal fun FollowUpSentiment.toDtoValue(): String = when (this) {
    FollowUpSentiment.WORKING -> "WORKING"
    FollowUpSentiment.PARTIALLY_WORKING -> "PARTIALLY_WORKING"
    FollowUpSentiment.NOT_WORKING -> "NOT_WORKING"
}

// --- DTO -> domain ---

internal fun UserDto.toDomainUser(): User = User(
    id = (id?.toString() ?: uuid) ?: "",
    name = name ?: "",
    email = email ?: "",
    avatarInitials = avatarInitials ?: (name?.take(2)?.uppercase() ?: ""),
    createdAt = parseIso(date_joined) ?: System.currentTimeMillis()
)

internal fun ParticipantDto.toDomainParticipant(mediationId: String): Participant = Participant(
    id = id?.toString() ?: UUID.randomUUID().toString(),
    mediationId = mediationId,
    userId = userId?.toString(),
    name = name ?: "Participant",
    email = email ?: "",
    role = role ?: "Participant",
    status = participantStatusFrom(status),
    hasCompletedPrivateSession = hasCompletedPrivateSession ?: false
)

internal fun MessageDto.toDomainMessage(mediationId: String, participantId: String): Message = Message(
    id = id?.toString() ?: UUID.randomUUID().toString(),
    mediationId = mediationId,
    participantId = participantId,
    sender = when (sender) {
        "AI_MEDIATOR" -> MessageSender.AI_MEDIATOR
        "SYSTEM" -> MessageSender.SYSTEM
        else -> MessageSender.USER
    },
    content = content ?: "",
    timestamp = parseIso(timestamp) ?: System.currentTimeMillis(),
    isRiskFlagged = isRiskFlagged ?: false,
    detectedCategory = detectedCategory
)

internal fun ConversationDto.toDomainConversationMessages(mediationId: String, participantId: String): List<Message> =
    messages.orEmpty().map { it.toDomainMessage(mediationId, participantId) }

internal fun ConflictAnalysisDto.toDomainAnalysis(mediationId: String): ConflictAnalysis = ConflictAnalysis(
    id = id?.toString() ?: UUID.randomUUID().toString(),
    mediationId = mediationId,
    commonGoals = commonGoals ?: emptyList(),
    conflictingGoals = conflictingGoals ?: emptyList(),
    rootCauses = rootCauses ?: emptyList(),
    misunderstandings = misunderstandings ?: emptyList(),
    emotionalFactors = emotionalFactors ?: emptyList(),
    expectationGaps = expectationGaps ?: emptyList(),
    nonNegotiableConcerns = nonNegotiableConcerns ?: emptyList(),
    potentialCompromises = potentialCompromises ?: emptyList(),
    commonGroundSummary = commonGroundSummary ?: "",
    compatibilityScore = compatibilityScore ?: 0,
    analyzedAt = parseIso(analyzedAt) ?: System.currentTimeMillis()
)

internal fun ResolutionProposalDto.toDomainProposal(mediationId: String): ResolutionProposal = ResolutionProposal(
    id = id?.toString() ?: UUID.randomUUID().toString(),
    mediationId = mediationId,
    proposalNumber = proposalNumber ?: 0,
    title = title ?: "Untitled proposal",
    modelType = modelType ?: "AI Proposal",
    description = description ?: "",
    benefits = benefits ?: emptyList(),
    tradeoffs = tradeoffs ?: emptyList(),
    requiredCompromises = requiredCompromises ?: emptyMap(),
    expectedImpact = expectedImpact ?: "",
    whyItWorks = whyItWorks ?: "",
    votes = (votes ?: emptyMap()).mapValues { voteTypeFrom(it.value) },
    feedback = feedback ?: emptyMap(),
    isApproved = isApproved ?: false,
    refinementIteration = refinementIteration ?: 1
)

internal fun AgreementDto.toDomainAgreement(mediationId: String): MutualAgreement = MutualAgreement(
    id = id?.toString() ?: UUID.randomUUID().toString(),
    mediationId = mediationId,
    proposalId = resolutionId?.toString() ?: "",
    resolutionTitle = resolutionTitle ?: "",
    preamble = preamble ?: "",
    agreedTerms = agreedTerms ?: emptyList(),
    participantResponsibilities = participantResponsibilities ?: emptyMap(),
    milestones = (milestones ?: emptyList()).map {
        AgreementMilestone(
            id = UUID.randomUUID().toString(),
            title = it.title ?: "",
            dueDate = it.dueDate ?: "",
            assignedTo = it.assignedTo ?: ""
        )
    },
    disputeEscalationClause = disputeEscalationClause ?: "",
    signedAt = parseIso(signedAt),
    signatures = (signatures ?: emptyMap()).mapValues { parseIso(it.value) ?: System.currentTimeMillis() },
    isFullySigned = isFullySigned ?: false
)

internal fun FollowUpReportDto.toDomainFollowUp(mediationId: String): FollowUpReport = FollowUpReport(
    id = id?.toString() ?: UUID.randomUUID().toString(),
    mediationId = mediationId,
    participantId = participantId?.toString() ?: "",
    participantName = participantName ?: "",
    sentiment = sentimentFrom(sentiment),
    comments = comments ?: "",
    timestamp = parseIso(timestamp) ?: System.currentTimeMillis(),
    reopenRequested = reopenRequested ?: false
)

internal fun SafetyAlertDto.toDomainSafety(): SafetyAssessment = SafetyAssessment(
    isHighRisk = isHighRisk ?: false,
    detectedRisks = detectedRisks ?: emptyList(),
    guidanceMessage = guidanceMessage ?: "",
    emergencyResources = (emergencyResources ?: emptyList()).map {
        EmergencyResource(
            title = it.title ?: "",
            phoneNumber = it.phoneNumber ?: "",
            textNumber = it.textNumber ?: "",
            description = it.description ?: "",
            url = it.url ?: ""
        )
    }
)

internal fun MediationDto.toDomainMediation(): Mediation {
    val id = id?.toString() ?: UUID.randomUUID().toString()
    return Mediation(
        id = id,
        title = title ?: "",
        description = description ?: "",
        category = category ?: "General",
        status = mediationStatusFrom(status),
        inviteCode = inviteCode ?: "",
        creatorId = creatorId?.toString() ?: "",
        createdAt = parseIso(createdAt) ?: System.currentTimeMillis(),
        participants = participants.orEmpty().map { it.toDomainParticipant(id) },
        analysis = analysis?.toDomainAnalysis(id),
        proposals = proposals.orEmpty().map { it.toDomainProposal(id) },
        activeProposalId = activeProposalId?.toString(),
        agreement = agreement?.toDomainAgreement(id),
        followUps = followUps.orEmpty().map { it.toDomainFollowUp(id) },
        safetyAlert = safetyAlert?.toDomainSafety()
    )
}