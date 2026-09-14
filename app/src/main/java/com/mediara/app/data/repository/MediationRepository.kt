package com.mediara.app.data.repository

import android.content.Context
import com.mediara.app.data.local.AppDatabase
import com.mediara.app.data.local.MediationEntity
import com.mediara.app.data.local.MessageEntity
import com.mediara.app.data.model.*
import com.mediara.app.data.remote.*
import com.mediara.app.data.toDomainAgreement
import com.mediara.app.data.toDomainAnalysis
import com.mediara.app.data.toDomainMediation
import com.mediara.app.data.toDomainMessage
import com.mediara.app.data.toDomainProposal
import com.mediara.app.data.toDomainUser
import com.mediara.app.data.toDtoValue
import com.mediara.app.data.toDomainConversationMessages
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * API-backed facade over the Mediara backend.
 *
 * Holds the reactive in-memory state for the signed-in user and the mediation
 * list, keeps a Room JSON cache for offline/startup rendering, and forwards
 * every mutation to the Django REST backend.
 */
class MediationRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val apiService: MediaraApiService,
    private val sessionManager: SessionManager,
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val mediationAdapter = moshi.adapter(Mediation::class.java)

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The signed-in account; null until login/register or session restore. */
    val activeUser: StateFlow<User?> = sessionManager.user

    // In-memory reactive cache backed by Room JSON rows
    private val _mediations = MutableStateFlow<List<Mediation>>(emptyList())
    val mediations: StateFlow<List<Mediation>> = _mediations.asStateFlow()

    // (mediationId:participantId) -> conversationId for the confidential session
    private val conversationKeys = ConcurrentHashMap<String, String>()

    init {
        repositoryScope.launch { loadCachedMediations() }
        repositoryScope.launch {
            sessionManager.isLoggedIn.filter { isLoggedIn -> isLoggedIn }.collect { refreshMediations() }
        }
    }

    fun isAuthenticated(): Boolean = sessionManager.isLoggedInNow()

    /** Resolves the signed-in user's Participant id within a mediation (from cache). */
    fun myParticipantIdNow(mediationId: String): String? {
        val user = sessionManager.user.value ?: return null
        return _mediations.value.firstOrNull { it.id == mediationId }
            ?.participants?.firstOrNull { it.userId == user.id }?.id
    }

    // --- Auth ---

    suspend fun register(name: String, email: String, password: String): User {
        val response = try {
            apiService.register(RegisterRequestDto(name = name, email = email, password = password))
        } catch (e: Throwable) {
            throw e.toMediaraError(statusCode = null)
        }
        val user = response.user?.toDomainUser()
            ?: throw MediaraApiException("Account created but no profile was returned.")
        sessionManager.setSession(
            access = response.access ?: "",
            refresh = response.refresh ?: "",
            user = user
        )
        refreshMediations()
        return user
    }

    /** Outcome produced by the login endpoint. */
    sealed interface LoginResult {
        data class Success(val user: User) : LoginResult
        data class EmailVerificationRequired(val email: String) : LoginResult
    }

    suspend fun login(email: String, password: String): LoginResult {
        val response = try {
            apiService.login(LoginRequestDto(email = email, password = password))
        } catch (e: Throwable) {
            throw e.toMediaraError(statusCode = null)
        }
        if (response.emailVerificationRequired == true) {
            return LoginResult.EmailVerificationRequired(response.email ?: email)
        }
        val user = response.user?.toDomainUser()
            ?: throw MediaraApiException("Signed in but no profile was returned.")
        sessionManager.setSession(
            access = response.access ?: "",
            refresh = response.refresh ?: "",
            user = user
        )
        refreshMediations()
        return LoginResult.Success(user)
    }

    /** Verify the 6-digit email verification code, then mark the local user verified. */
    suspend fun verifyEmail(email: String, code: String) {
        val emailNormalized = email.trim().lowercase()
        try {
            apiService.verifyEmail(VerifyEmailRequestDto(email = emailNormalized, code = code.trim()))
        } catch (e: Throwable) {
            throw e.toMediaraError(statusCode = null)
        }
        sessionManager.user.value?.copy(emailVerified = true)?.let {
            sessionManager.setUser(it)
        }
    }

    /** Re-send the email verification code (accessible via the dev endpoint in DEBUG). */
    suspend fun resendVerificationCode(email: String): String? {
        val emailNormalized = email.trim().lowercase()
        val response = try {
            apiService.devResendCode(DevResendCodeRequestDto(email = emailNormalized))
        } catch (e: Throwable) {
            throw e.toMediaraError(statusCode = null)
        }
        return if (response.code.isNullOrBlank()) null else response.code
    }

    /** Request a password-reset code. Always succeeds (address existence is hidden). */
    suspend fun requestPasswordReset(email: String) {
        try {
            apiService.requestPasswordReset(PasswordResetRequestDto(email = email.trim()))
        } catch (e: Throwable) {
            throw e.toMediaraError(statusCode = null)
        }
    }

    /** Validate the reset code and set a new password. */
    suspend fun confirmPasswordReset(email: String, code: String, newPassword: String) {
        try {
            apiService.confirmPasswordReset(
                PasswordResetConfirmRequestDto(
                    email = email.trim(),
                    code = code.trim(),
                    password = newPassword
                )
            )
        } catch (e: Throwable) {
            throw e.toMediaraError(statusCode = null)
        }
    }

    suspend fun logout() {
        val refresh = sessionManager.refreshToken()
        if (refresh.isNotBlank()) {
            runCatching { apiService.logout(RefreshRequestDto(refresh = refresh)) }
        }
        sessionManager.clear()
        _mediations.value = emptyList()
    }

    /** Result of an admin credential check against a target server. */
    sealed interface AdminVerifyResult {
        /** Credentials are valid and MFA is satisfied (or not required). */
        data class Success(val user: User) : AdminVerifyResult
        /** MFA is required but not yet enrolled; setup the authenticator first. */
        data class MfaSetupRequired(val otpauthUri: String?, val secret: String?, val userId: Int) : AdminVerifyResult
        /** MFA is enrolled; a TOTP code is needed to finish the login. */
        data class MfaCodeRequired(val userId: Int) : AdminVerifyResult
    }

    /**
     * Validates administrator credentials against a *target* server before the
     * app switches to it (enterprise "admin gateway" pattern). Uses a dedicated
     * one-off client so the current session and config are never touched.
     */
    suspend fun verifyAdminAgainst(url: String, email: String, password: String): AdminVerifyResult {
        val targetBase = url.trim().trimEnd('/') + "/"
        val service: MediaraApiService = try {
            buildAdminService(targetBase)
        } catch (e: Throwable) {
            throw MediaraApiException("Invalid server address: ${url.trim()}")
        }
        val response = try {
            service.login(LoginRequestDto(email = email.trim(), password = password))
        } catch (e: Throwable) {
            throw e.toMediaraError(statusCode = null)
        }
        response.user?.toDomainUser()?.let { user ->
            if (user.isStaff) return AdminVerifyResult.Success(user)
            throw MediaraApiException("This account is not an administrator.")
        }
        if (response.mfaSetupRequired == true && response.userId != null) {
            return AdminVerifyResult.MfaSetupRequired(otpauthUri = null, secret = null, userId = response.userId!!)
        }
        if (response.mfaRequired == true && response.userId != null) {
            return AdminVerifyResult.MfaCodeRequired(response.userId!!)
        }
        throw MediaraApiException("Signed in but the response had no profile.")
    }

    /** Fetch a TOTP enrollment URI for a staff account that has no MFA yet. */
    suspend fun adminMfaSetup(url: String, email: String, password: String):
            AdminVerifyResult.MfaSetupRequired {
        val targetBase = url.trim().trimEnd('/') + "/"
        val service = buildAdminService(targetBase)
        val payload = try {
            service.adminMfaSetup(MfaSetupRequestDto(email = email.trim(), password = password))
        } catch (e: Throwable) {
            throw e.toMediaraError(statusCode = null)
        }
        val userId = payload.userId ?: throw MediaraApiException("MFA enrollment failed to return an account id.")
        return AdminVerifyResult.MfaSetupRequired(
            otpauthUri = payload.otpauthUri,
            secret = payload.secret,
            userId = userId,
        )
    }

    /** Complete MFA enrollment (fresh TOTP code) and return the verified admin. */
    suspend fun adminMfaSetupComplete(url: String, email: String, password: String, code: String): User {
        val service = buildAdminService(url.trim().trimEnd('/') + "/")
        val response = try {
            service.adminMfaSetupComplete(MfaSetupCompleteRequestDto(email = email.trim(), password = password, code = code.trim()))
        } catch (e: Throwable) {
            throw e.toMediaraError(statusCode = null)
        }
        val user = response.user?.toDomainUser()
            ?: throw MediaraApiException("MFA enabled but no profile was returned.")
        if (!user.isStaff) throw MediaraApiException("This account is not an administrator.")
        return user
    }

    /** Finish an MFA-gated admin login with a TOTP code. */
    suspend fun adminMfaVerify(url: String, userId: Int, code: String): User {
        val service = buildAdminService(url.trim().trimEnd('/') + "/")
        val response = try {
            service.adminMfaVerify(MfaVerifyRequestDto(userId = userId, code = code.trim()))
        } catch (e: Throwable) {
            throw e.toMediaraError(statusCode = null)
        }
        val user = response.user?.toDomainUser()
            ?: throw MediaraApiException("MFA verified but no profile was returned.")
        if (!user.isStaff) throw MediaraApiException("This account is not an administrator.")
        return user
    }

    private fun buildAdminService(targetBase: String): MediaraApiService =
        Retrofit.Builder()
            .baseUrl(targetBase)
            .client(OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(MediaraApiService::class.java)

    /** Called after a successful server-address change: drop any old-session tokens. */
    suspend fun clearSessionForConfigChange() {
        sessionManager.clear()
        _mediations.value = emptyList()
    }

    // --- Mediation list & fetch ---

    suspend fun refreshMediations() {
        val dtos = attempt { apiService.listMediations() }
        val mapped = dtos.map { it.toDomainMediation() }
        _mediations.value = mapped
        mapped.forEach { cacheMediation(it) }
    }

    suspend fun refreshMediation(id: String) {
        val intId = id.toIntOrNull() ?: return
        val mapped = attempt { apiService.getMediation(intId) }.toDomainMediation()
        cacheMediation(mapped)
        _mediations.value = _mediations.value.map { if (it.id == mapped.id) mapped else it }
    }

    fun getMediationFlow(id: String): kotlinx.coroutines.flow.Flow<Mediation?> =
        _mediations.map { list -> list.find { it.id == id } }

    fun getParticipantMessages(mediationId: String, participantId: String): kotlinx.coroutines.flow.Flow<List<Message>> =
        database.mediationDao().getMessagesForParticipant(mediationId, participantId)
            .map { entities -> entities.map { it.toMessage() } }

    suspend fun getMediationDetail(id: String): Mediation {
        refreshMediation(id)
        return _mediations.value.firstOrNull { it.id == id }
            ?: throw MediaraApiException("Mediation not found.")
    }

    // --- Mediation lifecycle ---

    suspend fun createMediation(
        title: String,
        description: String,
        category: String,
        participantNames: List<String>
    ): Mediation = withContext(Dispatchers.IO) {
        val names = (participantNames ?: emptyList()).filter { it.isNotBlank() }.map { it.trim() }
        val dto = attempt {
            apiService.createMediation(
                CreateMediationRequestDto(
                    title = title.trim(),
                    description = description.trim(),
                    category = category,
                    participantNames = names,
                    participantEmails = emptyList()
                )
            )
        }
        val med = dto.toDomainMediation()
        cacheMediation(med)
        _mediations.value = listOf(med) + _mediations.value.filterNot { it.id == med.id }
        med
    }

    suspend fun joinMediationByCode(
        inviteCode: String,
        role: String
    ): Mediation = withContext(Dispatchers.IO) {
        val code = inviteCode.trim()
        val dto = attempt {
            apiService.joinMediation(JoinMediationRequestDto(inviteCode = code, role = role))
        }
        val med = dto.toDomainMediation()
        cacheMediation(med)
        _mediations.value = listOf(med) + _mediations.value.filterNot { it.id == med.id }
        med
    }

    // --- Confidential private session ---

    private fun conversationKey(mediationId: String, participantId: String) = "$mediationId:$participantId"

    private fun cachedConversationId(mediationId: String, participantId: String): String? =
        conversationKeys[conversationKey(mediationId, participantId)]?.takeIf { it.isNotBlank() }

    suspend fun ensureConversationLoaded(mediationId: String, participantId: String) {
        val key = conversationKey(mediationId, participantId)
        if (conversationKeys.containsKey(key)) return
        val mediationIdAsInt = mediationId.toIntOrNull() ?: return
        val conv = attempt { apiService.getMyConversation(mediationIdAsInt) }
        conversationKeys[key] = conv.id?.toString() ?: ""
        val messages = conv.toDomainConversationMessages(mediationId, participantId)
        database.mediationDao().deleteMessagesForParticipant(mediationId, participantId)
        messages.forEach { database.mediationDao().insertMessage(MessageEntity.fromMessage(it)) }
        // Ensure the mediation (with its participant) is present in the cache.
        val hasSelf = _mediations.value.firstOrNull { it.id == mediationId }
            ?.participants?.any { it.id == participantId } == true
        if (!hasSelf) refreshMediation(mediationId)
    }

    suspend fun sendPrivateMessage(
        mediationId: String,
        participantId: String,
        content: String
    ): Pair<Message, Message?> = withContext(Dispatchers.IO) {
        val convId = cachedConversationId(mediationId, participantId)
            ?: run {
                ensureConversationLoaded(mediationId, participantId)
                cachedConversationId(mediationId, participantId)
                    ?: throw MediaraApiException("Confidential session unavailable. Try again.")
            }
        val result = attempt {
            apiService.sendMessage(
                convId.toIntOrNull() ?: throw MediaraApiException("Confidential session unavailable."),
                SendMessageRequestDto(message = content.trim())
            )
        }
        val userMsg = result.user?.toDomainMessage(mediationId, participantId)
            ?: throw MediaraApiException("No response from the server.")
        val aiMsg = result.ai?.toDomainMessage(mediationId, participantId)

        database.mediationDao().insertMessage(MessageEntity.fromMessage(userMsg))
        if (aiMsg != null) database.mediationDao().insertMessage(MessageEntity.fromMessage(aiMsg))

        if (result.safetyHold == true) {
            _mediations.value = _mediations.value.map { mediation ->
                if (mediation.id == mediationId) mediation.copy(status = MediationStatus.SAFETY_HOLD) else mediation
            }
        }

        Pair(userMsg, aiMsg)
    }

    suspend fun completePrivateSession(
        mediationId: String,
        participantId: String
    ): Mediation = withContext(Dispatchers.IO) {
        var convId = cachedConversationId(mediationId, participantId)
        if (convId == null) {
            ensureConversationLoaded(mediationId, participantId)
            convId = cachedConversationId(mediationId, participantId)
        }
        val id = convId?.toIntOrNull()
            ?: throw MediaraApiException("Confidential session unavailable. Send a message first.")
        attempt { apiService.completeSession(id) }
        refreshMediation(mediationId)
        _mediations.value.firstOrNull { it.id == mediationId }
            ?: throw MediaraApiException("Mediation not found.")
    }

    // --- AI mediation pipeline ---

    suspend fun runConflictAnalysis(mediationId: String): Mediation = withContext(Dispatchers.IO) {
        val result = attempt { apiService.analyze(mediationId.toIntOrNull() ?: throw MediaraApiException("Invalid mediation.")) }
        val current = _mediations.value.firstOrNull { it.id == mediationId } ?: refreshMediationD(mediationId)
        val analysis = result.analysis?.toDomainAnalysis(mediationId)
        val proposals = result.proposals.orEmpty().map { it.toDomainProposal(mediationId) }

        val updated = current.copy(
            analysis = analysis ?: current.analysis,
            proposals = if (proposals.isNotEmpty()) proposals else current.proposals,
            activeProposalId = proposals.firstOrNull()?.id ?: current.activeProposalId,
            status = if (proposals.isNotEmpty()) MediationStatus.PROPOSALS_READY else MediationStatus.ANALYZED
        )
        cacheMediation(updated)
        _mediations.value = _mediations.value.map { if (it.id == updated.id) updated else it }
        updated
    }

    suspend fun voteOnProposal(
        mediationId: String,
        proposalId: String,
        participantId: String,
        vote: VoteType,
        feedback: String?
    ): Mediation = withContext(Dispatchers.IO) {
        val result = attempt {
            apiService.vote(
                proposalId.toIntOrNull() ?: throw MediaraApiException("Invalid proposal."),
                VoteRequestDto(
                    decision = vote.toDtoValue(),
                    feedback = feedback.orEmpty(),
                    participantId = participantId.toIntOrNull()
                )
            )
        }
        val voted = result.resolution?.toDomainProposal(mediationId)
        val current = _mediations.value.firstOrNull { it.id == mediationId } ?: refreshMediationD(mediationId)
        val updated = if (voted != null) {
            current.copy(proposals = current.proposals.map { if (it.id == voted.id) voted else it })
        } else current
        cacheMediation(updated)
        _mediations.value = _mediations.value.map { if (it.id == updated.id) updated else it }
        updated
    }

    suspend fun refineProposalWithFeedback(
        mediationId: String,
        proposalId: String,
        feedbackSummary: String
    ): Mediation = refineProposalWithFeedback(
        mediationId = mediationId,
        proposalId = proposalId,
        feedbackSummary = feedbackSummary,
        feedback = feedbackSummary
    )

    suspend fun refineProposalWithFeedback(
        mediationId: String,
        proposalId: String,
        feedbackSummary: String,
        feedback: String
    ): Mediation = withContext(Dispatchers.IO) {
        attempt {
            apiService.refine(
                proposalId.toIntOrNull() ?: throw MediaraApiException("Invalid proposal."),
                RefineRequestDto(feedbackSummary = feedbackSummary, feedback = feedback)
            )
        }
        refreshMediation(mediationId)
        _mediations.value.firstOrNull { it.id == mediationId }
            ?: throw MediaraApiException("Mediation not found.")
    }

    // --- Agreement, PDF, follow-up ---

    suspend fun finalizeMediation(
        mediationId: String,
        resolutionId: String? = null
    ): Mediation = withContext(Dispatchers.IO) {
        val agreement = attempt {
            apiService.finalizeMediation(
                mediationId.toIntOrNull() ?: throw MediaraApiException("Invalid mediation."),
                FinalizeRequestDto(resolutionId = resolutionId?.toIntOrNull())
            )
        }.toDomainAgreement(mediationId)

        refreshMediation(mediationId)
        val current = _mediations.value.firstOrNull { it.id == mediationId } ?: refreshMediationD(mediationId)
        val updated = current.copy(agreement = agreement, status = MediationStatus.RESOLVED)
        cacheMediation(updated)
        _mediations.value = _mediations.value.map { if (it.id == updated.id) updated else it }
        updated
    }

    suspend fun signAgreement(
        mediationId: String,
        participantId: String
    ): Mediation = withContext(Dispatchers.IO) {
        // The backend records every participant's signature at finalize time, so
        // signing is a server-side refresh rather than a client-side mutation.
        refreshMediation(mediationId)
        _mediations.value.firstOrNull { it.id == mediationId }
            ?: throw MediaraApiException("Mediation not found.")
    }

    suspend fun downloadAgreementPdf(mediationId: String): File {
        val body = attempt { apiService.downloadMediationPdf(mediationId.toIntOrNull() ?: throw MediaraApiException("Invalid mediation.")) }
        val file = File(context.cacheDir, "agreement_$mediationId.pdf")
        file.outputStream().use { output -> body.byteStream().use { input -> input.copyTo(output) } }
        return file
    }

    suspend fun submitFollowUp(
        mediationId: String,
        participantId: String,
        sentiment: FollowUpSentiment,
        comments: String,
        requestReopen: Boolean
    ): Mediation = withContext(Dispatchers.IO) {
        attempt {
            apiService.submitFollowUp(
                mediationId.toIntOrNull() ?: throw MediaraApiException("Invalid mediation."),
                FollowUpRequestDto(
                    sentiment = sentiment.toDtoValue(),
                    comments = comments.trim(),
                    reopen = requestReopen || sentiment == FollowUpSentiment.NOT_WORKING,
                    participantId = participantId.toIntOrNull()
                )
            )
        }
        refreshMediation(mediationId)
        _mediations.value.firstOrNull { it.id == mediationId }
            ?: throw MediaraApiException("Mediation not found.")
    }

    suspend fun reopenMediation(mediationId: String): Mediation = withContext(Dispatchers.IO) {
        attempt { apiService.reopenMediation(mediationId.toIntOrNull() ?: throw MediaraApiException("Invalid mediation.")) }
        refreshMediation(mediationId)
        _mediations.value.firstOrNull { it.id == mediationId }
            ?: throw MediaraApiException("Mediation not found.")
    }

    // --- Private helpers ---

    private suspend fun loadCachedMediations() {
        val cached = database.mediationDao().getAllMediations().first().orEmpty()
            .mapNotNull { entity -> runCatching { mediationAdapter.fromJson(entity.jsonPayload) }.getOrNull() }
        if (cached.isNotEmpty()) _mediations.value = cached
    }

    private suspend fun refreshMediationD(id: String): Mediation {
        refreshMediation(id)
        return _mediations.value.firstOrNull { it.id == id }
            ?: throw MediaraApiException("Mediation not found.")
    }

    private suspend fun cacheMediation(mediation: Mediation) {
        database.mediationDao().insertMediation(
            MediationEntity(
                id = mediation.id,
                title = mediation.title,
                description = mediation.description,
                category = mediation.category,
                status = mediation.status.name,
                inviteCode = mediation.inviteCode,
                creatorId = mediation.creatorId,
                createdAt = mediation.createdAt,
                jsonPayload = mediationAdapter.toJson(mediation)
            )
        )
    }

    private suspend fun <T> attempt(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: HttpException) {
            if (e.code() == 401) sessionManager.clear()
            throw MediaraApiException(e.errorMessage(), e.code())
        } catch (e: SocketTimeoutException) {
            throw MediaraApiException("Request timed out. Check your connection and retry.")
        } catch (e: ConnectException) {
            throw MediaraApiException("Could not reach the Mediara server. Is the backend running?")
        } catch (e: UnknownHostException) {
            throw MediaraApiException("Could not resolve the server address.")
        } catch (e: IOException) {
            throw MediaraApiException("Network error: ${e.message ?: "please retry"}")
        } catch (e: MediaraApiException) {
            throw e
        }
    }

    private fun Throwable.toMediaraError(statusCode: Int?): MediaraApiException = when (this) {
        is HttpException -> MediaraApiException(errorMessage(), code())
        is ConnectException -> MediaraApiException("Could not reach the Mediara server. Is the backend running?")
        is UnknownHostException -> MediaraApiException("Could not resolve the server address.")
        is IOException -> MediaraApiException("Network error: ${message ?: "please retry"}")
        is MediaraApiException -> this
        else -> MediaraApiException(message ?: "Unexpected error.", statusCode)
    }

    private fun HttpException.errorMessage(): String {
        val code = code()
        val body = runCatching { response()?.errorBody()?.string() }.getOrNull().orEmpty()
        if (body.isNotBlank()) {
            // Never leak raw (e.g. HTML) bodies to the user. Only trust JSON errors.
            if (!body.trimStart().startsWith("{")) {
                return "Request failed (HTTP $code)."
            }
            runCatching {
                val json = JSONObject(body)
                if (json.has("detail")) return json.optString("detail")
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key == "detail") continue
                    val value = json.opt(key)
                    if (value is JSONArray && value.length() > 0) return value.optString(0)
                }
            }
        }
        return "Request failed (HTTP $code)."
    }
}