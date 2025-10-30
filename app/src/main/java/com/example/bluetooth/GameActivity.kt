package com.example.bluetooth
import android.graphics.Color
import androidx.core.content.ContextCompat


import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.gridlayout.widget.GridLayout

private const val TAG = "[Project 2: Networking]"


class GameActivity : ComponentActivity() {


    private lateinit var gridLayout: GridLayout
    private lateinit var statusText: TextView
    private val buttons = Array(3) { Array<Button?>(3) { null } }
    private var myTurn = false
    private val mySymbol: String
    private val opponentSymbol: String

    init {
        // Server plays as X (goes first), Client plays as O
        if (BluetoothConnection.isServer) {
            mySymbol = "X"
            opponentSymbol = "O"
            myTurn = true
        } else {
            mySymbol = "O"
            opponentSymbol = "X"
            myTurn = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        gridLayout = findViewById(R.id.gridLayout)
        statusText = findViewById(R.id.statusText)



        // Set up message listener
        BluetoothConnection.connectedThread?.messageListener = { message ->
            runOnUiThread {
                handleReceivedMessage(message)
            }
        }

        updateUI()
    }

    private fun onButtonClick(row: Int, col: Int) {
        val button = buttons[row][col] ?: return

        // Check if it's the user's turn and button is empty
        if (!myTurn) {
            Toast.makeText(this, "Wait for your turn!", Toast.LENGTH_SHORT).show()
            return
        }

        if (button.text.isNotEmpty()) {
            Toast.makeText(this, "This cell is already taken!", Toast.LENGTH_SHORT).show()
            return
        }

        // Mark the button
        button.text = mySymbol
        button.isEnabled = false

        // Send move to opponent
        sendMove(row, col)

        // Switch turn
        myTurn = false
        updateUI()

        // Check for win or draw
        checkGameState()
    }

    private fun sendMove(row: Int, col: Int) {
        val message = "MOVE:$row,$col,$mySymbol"
        BluetoothConnection.connectedThread?.write(message)
        Log.d(TAG, "Sent move: $message")
    }

    private fun handleReceivedMessage(message: String) {
        Log.d(TAG, "Received message: $message")

        if (message.startsWith("MOVE:")) {
            val parts = message.substringAfter("MOVE:").split(",")
            if (parts.size == 3) {
                val row = parts[0].toIntOrNull() ?: return
                val col = parts[1].toIntOrNull() ?: return
                val symbol = parts[2]

                // Update the button
                buttons[row][col]?.apply {
                    text = symbol
                    isEnabled = false
                }

                // Switch turn
                myTurn = true
                updateUI()

                // Check for win or draw
                checkGameState()
            }
        } else if (message == "RESET") {
            resetGame()
        }
    }

    private fun updateUI() {
        if (myTurn) {
            statusText.text = "Your turn ($mySymbol)"
            statusText.setTextColor(getColor(android.R.color.holo_green_dark))
        } else {
            statusText.text = "Opponent's turn ($opponentSymbol)"
            statusText.setTextColor(getColor(android.R.color.holo_red_dark))
        }
    }

    private fun checkGameState() {
        val loser = checkWinner()
        if (loser != null) {

            // In Misere, getting 3 in a row means you lose
            if (loser == mySymbol) {
                statusText.text = "You Lost!"
                statusText.setTextColor(getColor(android.R.color.holo_red_dark))
                insertTestData(this, "Lost", "Normal")

            } else {
                statusText.text = "You Won!"
                statusText.setTextColor(getColor(android.R.color.holo_green_dark))
                insertTestData(this, "Won", "Normal")
            }
            disableAllButtons()
            Toast.makeText(this, "Game Over! $loser got 3 in a row and loses!", Toast.LENGTH_LONG).show()
        } else if (isBoardFull()) {
            statusText.text = "It's a Draw!"
            statusText.setTextColor(getColor(android.R.color.holo_orange_dark))
            disableAllButtons()
            Toast.makeText(this, "Game Over! It's a draw!", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkWinner(): String? {
        // Check rows
        for (i in 0..2) {
            if (buttons[i][0]?.text == buttons[i][1]?.text &&
                buttons[i][1]?.text == buttons[i][2]?.text &&
                buttons[i][0]?.text?.isNotEmpty() == true) {
                return buttons[i][0]?.text.toString()
            }
        }

        // Check columns
        for (j in 0..2) {
            if (buttons[0][j]?.text == buttons[1][j]?.text &&
                buttons[1][j]?.text == buttons[2][j]?.text &&
                buttons[0][j]?.text?.isNotEmpty() == true) {
                return buttons[0][j]?.text.toString()
            }
        }

        // Check diagonals
        if (buttons[0][0]?.text == buttons[1][1]?.text &&
            buttons[1][1]?.text == buttons[2][2]?.text &&
            buttons[0][0]?.text?.isNotEmpty() == true) {
            return buttons[0][0]?.text.toString()
        }

        if (buttons[0][2]?.text == buttons[1][1]?.text &&
            buttons[1][1]?.text == buttons[2][0]?.text &&
            buttons[0][2]?.text?.isNotEmpty() == true) {
            return buttons[0][2]?.text.toString()
        }

        return null
    }

    private fun isBoardFull(): Boolean {
        for (i in 0..2) {
            for (j in 0..2) {
                if (buttons[i][j]?.text?.isEmpty() == true) {
                    return false
                }
            }
        }
        return true
    }

    private fun disableAllButtons() {
        for (i in 0..2) {
            for (j in 0..2) {
                buttons[i][j]?.isEnabled = false
            }
        }
    }

    private fun resetGame() {
        for (i in 0..2) {
            for (j in 0..2) {
                buttons[i][j]?.apply {
                    text = ""
                    isEnabled = true
                }
            }
        }

        // Server always goes first
        myTurn = BluetoothConnection.isServer
        updateUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        BluetoothConnection.connectedThread?.messageListener = null
    }
}