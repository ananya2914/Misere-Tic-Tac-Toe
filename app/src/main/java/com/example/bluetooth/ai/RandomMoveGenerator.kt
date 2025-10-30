package com.example.bluetooth.ai

import android.widget.Button
import com.example.bluetooth.Move
import kotlin.random.Random

class RandomMoveGenerator : MoveStrategy {

    override fun getMove(buttons: Array<Array<Button?>>, aiPlayer: String): Move {
        val emptyCells = mutableListOf<Move>()
        for (r in 0..2) {
            for (c in 0..2) {
                if (buttons[r][c]?.text?.isEmpty() == true) {
                    emptyCells.add(Move(r, c))
                }
            }
        }

        return if (emptyCells.isNotEmpty()) {
            emptyCells.random(Random)
        } else {
            throw IllegalStateException("No available moves on the board.")
        }
    }
}