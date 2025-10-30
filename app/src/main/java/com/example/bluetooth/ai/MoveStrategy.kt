package com.example.bluetooth.ai

import android.widget.Button
import com.example.bluetooth.Move

interface MoveStrategy {
    /**
     * Given the current board state, return the AI's next move.
     * @param buttons The current 3x3 array of buttons from the UI.
     * @param aiPlayer The player symbol as a String (e.g., "O").
     * @return The chosen Move (row, col).
     */
    fun getMove(buttons: Array<Array<Button?>>, aiPlayer: String): Move
}