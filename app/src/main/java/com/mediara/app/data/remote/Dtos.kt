package com.mediara.app.data.remote

/**
 * Wire-format DTOs mirroring the Django REST backend serializers exactly.
 * Backend uses integer ids and ISO-8601 timestamp strings; the mapping layer
 * converts these into the app's domain models (String ids, Long timestamps).
 * All fields are nullable-with-default so the client tolerates summary vs.
 * detail payloads and missing optional data.
 */

data class UserDto(
    val id: Int? = null,
    val uuid: String? = null,
    val name: String? = null,
    val email: String? = null,
    val avatarInitials: String? = null,
    val date_joined: String? = null,
)

data class AuthResponseDto(
    val access: String? = null,
    val refresh: String? = null,
    val user: UserDto? = null,
)

data class RefreshResponseDto(
    val access: String? = null,
    val refresh: String? = null,
)

data class ParticipantDto(
    val id: Int? = null,
    val userId: Int? = null,
    val name: String? = null,
    val email: String? = null,
    val role: String? = null,
    val status: String? = null,
    val hasCompletedPrivateSession: Boolean? = null,
)

data class MediationDto(
    val id: Int? = null,
    val title: String? = null,
    val description: String? = null,
    val category: String? = null,
    val status: String? = null,
    val inviteCode: String? = null,
    val creatorId: Int? = null,
    val createdAt: String? = null,
    val resolvedAt: String? = null,
    val participants: List<ParticipantDto>? = null,
    val analysis: ConflictAnalysisDto? = null,
    val proposals: List<ResolutionProposalDto>? = null,
    val activeProposalId: Int? = null,
    val agreement: AgreementDto? = null,
    val followUps: List<FollowUpReportDto>? = null,
    val safetyAlert: SafetyAlertDto? = null,
)

data class MessageDto(
    val id: Int? = null,
    val sender: String? = null,
    val content: String? = null,
    val messageType: String? = null,
    val isRiskFlagged: Boolean? = null,
    val detectedCategory: String? = null,
    val timestamp: String? = null,
)

data class ConversationDto(
    val id: Int? = null,
    val mediationId: Int? = null,
    val participantId: Int? = null,
    val isPrivate: Boolean? = null,
    val createdAt: String? = null,
    val messages: List<MessageDto>? = null,
)

data class SendMessageResultDto(
    val user: MessageDto? = null,
    val ai: MessageDto? = null,
    val safetyHold: Boolean? = null,
)

data class ConflictAnalysisDto(
    val id: Int? = null,
    val mediationId: Int? = null,
    val commonGoals: List<String>? = null,
    val conflictingGoals: List<String>? = null,
    val rootCauses: List<String>? = null,
    val misunderstandings: List<String>? = null,
    val emotionalFactors: List<String>? = null,
    val expectationGaps: List<String>? = null,
    val nonNegotiableConcerns: List<String>? = null,
    val potentialCompromises: List<String>? = null,
    val commonGroundSummary: String? = null,
    val compatibilityScore: Int? = null,
    val analyzedAt: String? = null,
)

data class ResolutionProposalDto(
    val id: Int? = null,
    val proposalNumber: Int? = null,
    val title: String? = null,
    val modelType: String? = null,
    val description: String? = null,
    val benefits: List<String>? = null,
    val tradeoffs: List<String>? = null,
    val requiredCompromises: Map<String, String>? = null,
    val expectedImpact: String? = null,
    val whyItWorks: String? = null,
    val votes: Map<String, String>? = null,
    val feedback: Map<String, String>? = null,
    val isApproved: Boolean? = null,
    val refinementIteration: Int? = null,
)

data class VoteResultDto(
    val resolution: ResolutionProposalDto? = null,
    val approvedByAll: Boolean? = null,
)

data class NegotiationEntryDto(
    val id: Int? = null,
    val proposalNumber: Int? = null,
    val title: String? = null,
    val modelType: String? = null,
    val description: String? = null,
    val benefits: List<String>? = null,
    val tradeoffs: List<String>? = null,
    val requiredCompromises: Map<String, String>? = null,
    val expectedImpact: String? = null,
    val whyItWorks: String? = null,
    val votes: Map<String, String>? = null,
    val feedback: Map<String, String>? = null,
    val isApproved: Boolean? = null,
    val refinementIteration: Int? = null,
    val approvedByAll: Boolean? = null,
)

data class AnalyzeResultDto(
    val analysis: ConflictAnalysisDto? = null,
    val proposals: List<ResolutionProposalDto>? = null,
)

data class AgreementMilestoneDto(
    val title: String? = null,
    val dueDate: String? = null,
    val assignedTo: String? = null,
)

data class AgreementDto(
    val id: Int? = null,
    val mediationId: Int? = null,
    val resolutionId: Int? = null,
    val resolutionTitle: String? = null,
    val preamble: String? = null,
    val agreedTerms: List<String>? = null,
    val participantResponsibilities: Map<String, List<String>>? = null,
    val milestones: List<AgreementMilestoneDto>? = null,
    val disputeEscalationClause: String? = null,
    val signedAt: String? = null,
    val signatures: Map<String, String>? = null,
    val isFullySigned: Boolean? = null,
)

data class FollowUpReportDto(
    val id: Int? = null,
    val mediationId: Int? = null,
    val participantId: Int? = null,
    val participantName: String? = null,
    val sentiment: String? = null,
    val comments: String? = null,
    val timestamp: String? = null,
    val reopenRequested: Boolean? = null,
)

data class SafetyAlertDto(
    val isHighRisk: Boolean? = null,
    val detectedRisks: List<String>? = null,
    val guidanceMessage: String? = null,
    val emergencyResources: List<EmergencyResourceDto>? = null,
)

data class EmergencyResourceDto(
    val title: String? = null,
    val phoneNumber: String? = null,
    val textNumber: String? = null,
    val description: String? = null,
    val url: String? = null,
)

// --- Request bodies ---

data class RegisterRequestDto(
    val name: String,
    val email: String,
    val password: String,
)

data class LoginRequestDto(
    val email: String,
    val password: String,
)

data class RefreshRequestDto(
    val refresh: String,
)

data class CreateMediationRequestDto(
    val title: String,
    val description: String,
    val category: String,
    val participantNames: List<String>,
    val participantEmails: List<String>,
)

data class InviteParticipantRequestDto(
    val name: String,
    val email: String,
    val role: String,
)

data class JoinMediationRequestDto(
    val inviteCode: String,
    val role: String,
)

data class SendMessageRequestDto(
    val message: String,
    val participantId: Int? = null,
)

data class VoteRequestDto(
    val decision: String,
    val feedback: String = "",
    val participantId: Int? = null,
)

data class RefineRequestDto(
    val feedbackSummary: String = "",
    val feedback: String = "",
)

data class FinalizeRequestDto(
    val resolutionId: Int? = null,
)

data class FollowUpRequestDto(
    val sentiment: String,
    val comments: String = "",
    val reopen: Boolean = false,
    val participantId: Int? = null,
)

class MediaraApiException(
    message: String,
    val statusCode: Int? = null,
) : Exception(message)