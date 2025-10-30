package com.example.bluetooth

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "past_game_table")
data class PastGame(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateTime: String,
    val result: String,
    val difficulty: String
)