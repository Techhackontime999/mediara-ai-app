package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.MediationEntity
import com.example.data.local.MessageEntity
import com.example.data.model.*
import com.example.data.remote.*
import com.example.data.toDomainAgreement
import com.example.data.toDomainAnalysis
import com.example.data.toDomainMediation
import com.example.data.toDomainMessage
import com.example.data.toDomainProposal
import com.example.data.toDtoValue
import com.example.data.toDomainConversationMessages
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
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

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

    suspend fun login(email: String, password: String): User {
        val response = try {
            apiService.login(LoginRequestDto(email = email, password = password))
        } catch (e: Throwable) {
            throw e.toMediaraError(statusCode = null)
        }
        val user = response.user?.toDomainUser()
            ?: throw MediaraApiException("Signed in but no profile was returned.")
        sessionManager.setSession(
            access = response.access ?: "",
            refresh = response.refresh ?: "",
            user = user
        )
        refreshMediations()
        return user
    }

    suspend fun logout() {
        val refresh = sessionManager.refreshToken()
        if (refresh.isNotBlank()) {
            runCatching { apiService.logout(RefreshRequestDto(refresh = refresh)) }
        }
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
        val byCode = attempt { apiService.getMediationByCode(code) }
        val medId = byCode.id ?: throw MediaraApiException("No mediation matches that invite code.")
        val dto = attempt {
            apiService.joinMediation(medId, JoinMediationRequestDto(inviteCode = code, role = role))
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
        val conv = attempt { apiService.getMyConversation(mediationId.toIntOrNull() ?: return) }
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
            return body
        }
        return "Request failed (HTTP $code)."
    }
}