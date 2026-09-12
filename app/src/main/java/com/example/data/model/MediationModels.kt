package com.example.data.model

enum class MediationStatus {
    CREATED,
    INVITED,
    IN_PROGRESS,
    ANALYZED,
    PROPOSALS_READY,
    NEGOTIATING,
    RESOLVED,
    FOLLOW_UP,
    REOPENED,
    CLOSED,
    SAFETY_HOLD
}

enum class ParticipantStatus {
    INVITED,
    JOINED,
    IN_SESSION,
    SESSION_COMPLETED,
    REVIEWED,
    SIGNED
}

enum class MessageSender {
    USER,
    AI_MEDIATOR,
    SYSTEM
}

enum class VoteType {
    ACCEPT,
    REQUEST_CHANGES,
    REJECT
}

enum class FollowUpSentiment {
    WORKING,            // 😊 Working
    PARTIALLY_WORKING,  // 😐 Partially Working
    NOT_WORKING         // 😞 Not Working
}

data class User(
    val id: String,
    val name: String,
    val email: String,
    val avatarInitials: String = name.take(2).uppercase(),
    val createdAt: Long = System.currentTimeMillis()
)

data class Participant(
    val id: String,
    val mediationId: String,
    val userId: String? = null, // null until the invited slot is claimed by an account
    val name: String,
    val email: String,
    val role: String, // e.g., "Roommate A", "Co-founder", "Team Lead"
    val status: ParticipantStatus = ParticipantStatus.JOINED,
    val hasCompletedPrivateSession: Boolean = false
)

data class Message(
    val id: String,
    val mediationId: String,
    val participantId: String,
    val sender: MessageSender,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRiskFlagged: Boolean = false,
    val detectedCategory: String? = null // GOAL, CONCERN, EMOTION, NEED, COMPROMISE
)

data class Perspective(
    val id: String,
    val mediationId: String,
    val participantId: String,
    val participantName: String,
    val goals: List<String> = emptyList(),
    val concerns: List<String> = emptyList(),
    val needs: List<String> = emptyList(),
    val constraints: List<String> = emptyList(),
    val emotions: List<String> = emptyList(),
    val desiredOutcome: String = "",
    val acceptableCompromises: List<String> = emptyList()
)

data class ConflictAnalysis(
    val id: String,
    val mediationId: String,
    val commonGoals: List<String> = emptyList(),
    val conflictingGoals: List<String> = emptyList(),
    val rootCauses: List<String> = emptyList(),
    val misunderstandings: List<String> = emptyList(),
    val emotionalFactors: List<String> = emptyList(),
    val expectationGaps: List<String> = emptyList(),
    val nonNegotiableConcerns: List<String> = emptyList(),
    val potentialCompromises: List<String> = emptyList(),
    val commonGroundSummary: String = "",
    val compatibilityScore: Int = 75,
    val analyzedAt: Long = System.currentTimeMillis()
)

data class ResolutionProposal(
    val id: String,
    val mediationId: String,
    val proposalNumber: Int, // 1, 2, 3
    val title: String,
    val modelType: String, // "Equal Distribution", "Process & Cadence", "Responsibility Ownership"
    val description: String,
    val benefits: List<String> = emptyList(),
    val tradeoffs: List<String> = emptyList(),
    val requiredCompromises: Map<String, String> = emptyMap(), // participantId/name -> what they give up
    val expectedImpact: String = "",
    val whyItWorks: String = "",
    val votes: Map<String, VoteType> = emptyMap(), // participantId -> VoteType
    val feedback: Map<String, String> = emptyMap(), // participantId -> feedback text
    val isApproved: Boolean = false,
    val refinementIteration: Int = 1
)

data class AgreementMilestone(
    val id: String,
    val title: String,
    val dueDate: String,
    val assignedTo: String,
    val isCompleted: Boolean = false
)

data class MutualAgreement(
    val id: String,
    val mediationId: String,
    val proposalId: String,
    val resolutionTitle: String,
    val preamble: String,
    val agreedTerms: List<String> = emptyList(),
    val participantResponsibilities: Map<String, List<String>> = emptyMap(), // participantName -> list of duties
    val milestones: List<AgreementMilestone> = emptyList(),
    val disputeEscalationClause: String = "If disputes re-emerge, parties agree to re-open Mediara AI mediation before taking external action.",
    val signedAt: Long? = null,
    val signatures: Map<String, Long> = emptyMap(), // participantId -> signedTimestamp
    val isFullySigned: Boolean = false
)

data class FollowUpReport(
    val id: String,
    val mediationId: String,
    val participantId: String,
    val participantName: String,
    val sentiment: FollowUpSentiment,
    val comments: String,
    val timestamp: Long = System.currentTimeMillis(),
    val reopenRequested: Boolean = false
)

data class SafetyAssessment(
    val isHighRisk: Boolean,
    val detectedRisks: List<String>,
    val guidanceMessage: String,
    val emergencyResources: List<EmergencyResource> = emptyList()
)

data class EmergencyResource(
    val title: String,
    val phoneNumber: String,
    val textNumber: String = "",
    val description: String,
    val url: String
)

data class Mediation(
    val id: String,
    val title: String,
    val description: String,
    val category: String, // Roommates, Workplace, Co-founders, Family, Friendship, Project Team
    val status: MediationStatus = MediationStatus.CREATED,
    val inviteCode: String,
    val creatorId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val participants: List<Participant> = emptyList(),
    val analysis: ConflictAnalysis? = null,
    val proposals: List<ResolutionProposal> = emptyList(),
    val activeProposalId: String? = null,
    val agreement: MutualAgreement? = null,
    val followUps: List<FollowUpReport> = emptyList(),
    val safetyAlert: SafetyAssessment? = null
)
