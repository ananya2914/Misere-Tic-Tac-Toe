package com.example.bluetooth

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PastGameDao {
    @Insert
    suspend fun insertGameRecord(game: PastGame)

    @Query("SELECT * FROM past_game_table ORDER BY id DESC")
    suspend fun getAllGames(): List<PastGame>

    @Query("DELETE FROM past_game_table")
    suspend fun clearAllGames()
}