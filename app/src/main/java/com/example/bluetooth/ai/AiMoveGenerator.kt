package com.example.bluetooth.ai

import android.widget.Button
import com.example.bluetooth.Move
import kotlin.math.max
import kotlin.math.min

class AiMoveGenerator : MoveStrategy {

    // Internal simple representation of a player
    private enum class InternalPlayer { X, O, EMPTY }

    /**
     * Public entry point required by the MoveStrategy interface.
     */
    override fun getMove(buttons: Array<Array<Button?>>, aiPlayer: String): Move {
        // 1. Convert the complex Button array into a simple internal board
        val internalBoard = convertButtonsToInternalBoard(buttons)
        val internalAiPlayer = if (aiPlayer == "X") InternalPlayer.X else InternalPlayer.O
        val internalHumanPlayer = if (internalAiPlayer == InternalPlayer.X) InternalPlayer.O else InternalPlayer.X

        // 2. Run Minimax on the simple internal board
        var bestScore = Int.MIN_VALUE
        var bestMove: Move? = null
        val emptyCells = getEmptyCells(internalBoard)

        for (move in emptyCells) {
            val newBoard = makeMove(internalBoard, move, internalAiPlayer)
            val score = minimax(
                board = newBoard,
                depth = 0,
                isMaximizingPlayer = false, // false because it's now the human's turn
                alpha = Int.MIN_VALUE,
                beta = Int.MAX_VALUE,
                aiPlayer = internalAiPlayer,
                humanPlayer = internalHumanPlayer
            )

            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
        }

        // 3. Return the best move
        return bestMove ?: emptyCells.random()
    }

    /**
     * Converts the Button array from the UI into a simple 2D Char array
     * that the Minimax algorithm can understand.
     */
    private fun convertButtonsToInternalBoard(buttons: Array<Array<Button?>>): Array<Array<InternalPlayer>> {
        return Array(3) { r ->
            Array(3) { c ->
                when (buttons[r][c]?.text.toString()) {
                    "X" -> InternalPlayer.X
                    "O" -> InternalPlayer.O
                    else -> InternalPlayer.EMPTY
                }
            }
        }
    }

    // --- FROM THIS POINT DOWN, THE LOGIC IS THE SAME, BUT USES `InternalPlayer` ---

    private fun minimax(
        board: Array<Array<InternalPlayer>>,
        depth: Int,
        isMaximizingPlayer: Boolean,
        alpha: Int,
        beta: Int,
        aiPlayer: InternalPlayer,
        humanPlayer: InternalPlayer
    ): Int {
        var currentAlpha = alpha
        var currentBeta = beta

        val score = evaluate(board, depth, aiPlayer, humanPlayer)
        if (score != 0) {
            return score
        }

        if (isBoardFull(board)) {
            return 0
        }

        if (isMaximizingPlayer) {
            var best = Int.MIN_VALUE
            for (move in getEmptyCells(board)) {
                val newBoard = makeMove(board, move, aiPlayer)
                best = max(
                    best,
                    minimax(newBoard, depth + 1, false, currentAlpha, currentBeta, aiPlayer, humanPlayer)
                )
                currentAlpha = max(currentAlpha, best)
                if (currentBeta <= currentAlpha) break
            }
            return best
        } else {
            var best = Int.MAX_VALUE
            for (move in getEmptyCells(board)) {
                val newBoard = makeMove(board, move, humanPlayer)
                best = min(
                    best,
                    minimax(newBoard, depth + 1, true, currentAlpha, currentBeta, aiPlayer, humanPlayer)
                )
                currentBeta = min(currentBeta, best)
                if (currentBeta <= currentAlpha) break
            }
            return best
        }
    }

    private fun evaluate(board: Array<Array<InternalPlayer>>, depth: Int, aiPlayer: InternalPlayer, humanPlayer: InternalPlayer): Int {
        if (checkWinner(board, humanPlayer)) {
            return 10 - depth // Human loses
        }
        if (checkWinner(board, aiPlayer)) {
            return -10 + depth // AI loses
        }
        return 0
    }

    private fun checkWinner(board: Array<Array<InternalPlayer>>, player: InternalPlayer): Boolean {
        // Check rows
        for (i in 0..2) {
            if (board[i][0] == player && board[i][1] == player && board[i][2] == player) return true
        }
        // Check columns
        for (i in 0..2) {
            if (board[0][i] == player && board[1][i] == player && board[2][i] == player) return true
        }
        // Check diagonals
        if (board[0][0] == player && board[1][1] == player && board[2][2] == player) return true
        if (board[0][2] == player && board[1][1] == player && board[2][0] == player) return true
        return false
    }

    private fun getEmptyCells(board: Array<Array<InternalPlayer>>): List<Move> {
        val emptyCells = mutableListOf<Move>()
        for (r in 0..2) {
            for (c in 0..2) {
                if (board[r][c] == InternalPlayer.EMPTY) {
                    emptyCells.add(Move(r, c))
                }
            }
        }
        return emptyCells
    }

    private fun isBoardFull(board: Array<Array<InternalPlayer>>): Boolean {
        return getEmptyCells(board).isEmpty()
    }

    private fun makeMove(board: Array<Array<InternalPlayer>>, move: Move, player: InternalPlayer): Array<Array<InternalPlayer>> {
        val newBoard = Array(3) { r -> board[r].clone() }
        newBoard[move.row][move.col] = player
        return newBoard
    }
}