package com.mediara.app.data.remote

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming

interface MediaraApiService {

    // --- Auth ---

    @POST("api/auth/register/")
    suspend fun register(@Body body: RegisterRequestDto): AuthResponseDto

    @POST("api/auth/login/")
    suspend fun login(@Body body: LoginRequestDto): AuthResponseDto

    @POST("api/auth/refresh/")
    suspend fun refresh(@Body body: RefreshRequestDto): RefreshResponseDto

    @POST("api/auth/logout/")
    suspend fun logout(@Body body: RefreshRequestDto): Map<String, Any?>

    @GET("api/auth/me/")
    suspend fun me(): UserDto

    // --- Mediations ---

    @GET("api/mediations/")
    suspend fun listMediations(): List<MediationDto>

    @POST("api/mediations/")
    suspend fun createMediation(@Body body: CreateMediationRequestDto): MediationDto

    @GET("api/mediations/{id}/")
    suspend fun getMediation(@Path("id") id: Int): MediationDto

    @GET("api/mediations/by-code/{inviteCode}/")
    suspend fun getMediationByCode(@Path("inviteCode") inviteCode: String): MediationDto

    @POST("api/mediations/{id}/join/")
    suspend fun joinMediation(@Path("id") id: Int, @Body body: JoinMediationRequestDto): MediationDto

    @POST("api/mediations/{id}/invite/")
    suspend fun inviteParticipant(@Path("id") id: Int, @Body body: InviteParticipantRequestDto): ParticipantDto

    @GET("api/mediations/{id}/participants/")
    suspend fun listParticipants(@Path("id") id: Int): List<ParticipantDto>

    // --- Conversations (confidential private session) ---

    @GET("api/conversations/mediations/{mediationId}/my-conversation/")
    suspend fun getMyConversation(@Path("mediationId") mediationId: Int): ConversationDto

    @POST("api/conversations/{conversationId}/message/")
    suspend fun sendMessage(@Path("conversationId") conversationId: Int, @Body body: SendMessageRequestDto): SendMessageResultDto

    @POST("api/conversations/{conversationId}/complete/")
    suspend fun completeSession(@Path("conversationId") conversationId: Int): ParticipantDto

    // --- AI mediation pipeline ---

    @POST("api/mediations/{id}/analyze/")
    suspend fun analyze(@Path("id") id: Int): AnalyzeResultDto

    @GET("api/mediations/{id}/analysis/")
    suspend fun getAnalysis(@Path("id") id: Int): ConflictAnalysisDto

    @POST("api/mediations/{id}/generate-resolutions/")
    suspend fun generateResolutions(@Path("id") id: Int): List<ResolutionProposalDto>

    @GET("api/mediations/{id}/resolutions/")
    suspend fun listResolutions(@Path("id") id: Int): List<ResolutionProposalDto>

    @GET("api/mediations/{id}/negotiation/")
    suspend fun listNegotiation(@Path("id") id: Int): List<NegotiationEntryDto>

    @POST("api/resolutions/{id}/vote/")
    suspend fun vote(@Path("id") id: Int, @Body body: VoteRequestDto): VoteResultDto

    @POST("api/resolutions/{id}/refine/")
    suspend fun refine(@Path("id") id: Int, @Body body: RefineRequestDto): ResolutionProposalDto

    // --- Agreement, PDF, follow-up ---

    @POST("api/mediations/{id}/finalize/")
    suspend fun finalizeMediation(@Path("id") id: Int, @Body body: FinalizeRequestDto): AgreementDto

    @GET("api/mediations/{id}/agreement/")
    suspend fun getAgreement(@Path("id") id: Int): AgreementDto

    @Streaming
    @GET("api/mediations/{id}/agreement/pdf/")
    suspend fun downloadMediationPdf(@Path("id") id: Int): ResponseBody

    @Streaming
    @GET("api/agreements/{id}/pdf/")
    suspend fun downloadAgreementPdf(@Path("id") id: Int): ResponseBody

    @POST("api/mediations/{id}/follow-up/")
    suspend fun submitFollowUp(@Path("id") id: Int, @Body body: FollowUpRequestDto): FollowUpReportDto

    @GET("api/mediations/{id}/follow-ups/")
    suspend fun listFollowUps(@Path("id") id: Int): List<FollowUpReportDto>

    @POST("api/mediations/{id}/reopen/")
    suspend fun reopenMediation(@Path("id") id: Int): Map<String, Any>
}