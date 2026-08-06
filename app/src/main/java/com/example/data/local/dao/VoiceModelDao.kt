package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.VoiceModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceModelDao {
    @Query("SELECT * FROM voice_models")
    fun getVoiceModels(): Flow<List<VoiceModelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoiceModel(model: VoiceModelEntity)

    @Query("DELETE FROM voice_models WHERE id = :modelId")
    suspend fun deleteVoiceModel(modelId: String)
}
