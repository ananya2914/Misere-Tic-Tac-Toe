package com.example.bluetooth.ai

import android.widget.Button
import com.example.bluetooth.Move
import kotlin.random.Random

class AIManager {

    private val randomStrategy = RandomMoveGenerator()
    private val minimaxStrategy = AiMoveGenerator()

    fun getAiMove(buttons: Array<Array<Button?>>, difficulty: String): Move {

        // Per project spec, AI is player O
        val aiPlayer = "O"

        return when (difficulty) {
            "Easy" -> {
                // Easy: AI takes random actions
                randomStrategy.getMove(buttons, aiPlayer)
            }
            "Hard" -> {
                // Hard: AI takes optimal actions
                minimaxStrategy.getMove(buttons, aiPlayer)
            }
            "Medium" -> {
                // Medium: 50% random, 50% optimal
                if (Random.nextBoolean()) {
                    minimaxStrategy.getMove(buttons, aiPlayer)
                } else {
                    randomStrategy.getMove(buttons, aiPlayer)
                }
            }
            else -> {
                // Default to Easy
                randomStrategy.getMove(buttons, aiPlayer)
            }
        }
    }
}