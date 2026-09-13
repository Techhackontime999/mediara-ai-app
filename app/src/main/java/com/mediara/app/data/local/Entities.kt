package com.mediara.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mediara.app.data.model.Message
import com.mediara.app.data.model.MessageSender
import com.mediara.app.data.model.User

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    val avatarInitials: String,
    val createdAt: Long
) {
    fun toUser(): User = User(
        id = id,
        name = name,
        email = email,
        avatarInitials = avatarInitials,
        createdAt = createdAt
    )

    companion object {
        fun fromUser(user: User): UserEntity = UserEntity(
            id = user.id,
            name = user.name,
            email = user.email,
            avatarInitials = user.avatarInitials,
            createdAt = user.createdAt
        )
    }
}

@Entity(tableName = "mediations")
data class MediationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val category: String,
    val status: String,
    val inviteCode: String,
    val creatorId: String,
    val createdAt: Long,
    val jsonPayload: String
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val mediationId: String,
    val participantId: String,
    val sender: String,
    val content: String,
    val timestamp: Long,
    val isRiskFlagged: Boolean,
    val detectedCategory: String?
) {
    fun toMessage(): Message = Message(
        id = id,
        mediationId = mediationId,
        participantId = participantId,
        sender = try { MessageSender.valueOf(sender) } catch (e: Exception) { MessageSender.USER },
        content = content,
        timestamp = timestamp,
        isRiskFlagged = isRiskFlagged,
        detectedCategory = detectedCategory
    )

    companion object {
        fun fromMessage(msg: Message): MessageEntity = MessageEntity(
            id = msg.id,
            mediationId = msg.mediationId,
            participantId = msg.participantId,
            sender = msg.sender.name,
            content = msg.content,
            timestamp = msg.timestamp,
            isRiskFlagged = msg.isRiskFlagged,
            detectedCategory = msg.detectedCategory
        )
    }
}
