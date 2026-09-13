package com.mediara.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediationDao {

    @Query("SELECT * FROM mediations ORDER BY createdAt DESC")
    fun getAllMediations(): Flow<List<MediationEntity>>

    @Query("SELECT * FROM mediations WHERE id = :id LIMIT 1")
    fun getMediationById(id: String): Flow<MediationEntity?>

    @Query("SELECT * FROM mediations WHERE inviteCode = :code LIMIT 1")
    suspend fun getMediationByInviteCode(code: String): MediationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediation(mediation: MediationEntity)

    @Query("DELETE FROM mediations WHERE id = :id")
    suspend fun deleteMediation(id: String)

    @Query("SELECT * FROM messages WHERE mediationId = :mediationId AND participantId = :participantId ORDER BY timestamp ASC")
    fun getMessagesForParticipant(mediationId: String, participantId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE mediationId = :mediationId AND participantId = :participantId ORDER BY timestamp ASC")
    suspend fun getMessagesList(mediationId: String, participantId: String): List<MessageEntity>

    @Query("DELETE FROM messages WHERE mediationId = :mediationId AND participantId = :participantId")
    suspend fun deleteMessagesForParticipant(mediationId: String, participantId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("SELECT * FROM users LIMIT 1")
    fun getActiveUser(): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)
}
