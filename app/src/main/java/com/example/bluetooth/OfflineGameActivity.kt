package com.example.bluetooth

import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.gridlayout.widget.GridLayout
import com.example.bluetooth.ai.AIManager

class OfflineGameActivity : ComponentActivity() {
    private lateinit var gridLayout: GridLayout
    private lateinit var statusText: TextView
    private val buttons = Array(3) { Array<Button?>(3) { null } }
    private var myTurn = true;
    private val mySymbol: String
    private val opponentSymbol: String
    var difficulty = "Easy"
    private var gameOver = false;
    val resetGameButton = findViewById<Button>(R.id.resetGame)
    val settingsButton = findViewById<Button>(R.id.settings)
    val spinner = findViewById<Spinner>(R.id.spinner)
    val settingsSubmit = findViewById<Button>(R.id.submit)
    val settingsLeave = findViewById<Button>(R.id.leaveSettings)
    val difficulties = listOf("Easy", "Medium", "Hard")
    val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, difficulties)
    private lateinit var aiManager: AIManager



    init {
            mySymbol = "X"
            opponentSymbol = "O"
            myTurn = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)
        aiManager = AIManager()
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        gridLayout = findViewById(R.id.gridLayout)
        statusText = findViewById(R.id.statusText)

        // 3x3 Grid
        for (i in 0..2) {
            for (j in 0..2) {
                val button = Button(this).apply {
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = 0
                        height = 0
                        columnSpec = GridLayout.spec(j, 1f)
                        rowSpec = GridLayout.spec(i, 1f)
                        setMargins(8, 8, 8, 8)
                    }
                    textSize = 36f
                    text = ""
                    tag = "$i,$j" // Store position as tag

                    setOnClickListener {
                        onButtonClick(i, j)
                    }
                }
                buttons[i][j] = button
                gridLayout.addView(button)
            }
        }

        updateUI()

        settingsButton.setOnClickListener {
            setContentView(R.layout.settings)
        }

        resetGameButton.setOnClickListener {
            resetGame()
        }

        settingsSubmit.setOnClickListener {
            difficulty = spinner.selectedItem.toString()
        }

        settingsLeave.setOnClickListener {
            setContentView(R.layout.activity_game_offline)
        }
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

        // Switch turn
        myTurn = false
        updateUI()

        // Check for win or draw
        checkGameState()

        if(!gameOver) {
            aiMove(difficulty)
        }
    }

    private fun aiMove(difficulty: String) {
        // 1. Call the AI manager to get its move
        val move = aiManager.getAiMove(buttons, difficulty)

        // 2. Use the move's row and col to get the button
        val button = buttons[move.row][move.col] ?: return

        // 3. Mark the button
        button.text = opponentSymbol
        button.isEnabled = false

        // 4. Switch turn
        myTurn = true // Switch back to human
        updateUI()

        // 5. Check for win or draw
        checkGameState()
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
            } else {
                statusText.text = "You Won!"
                statusText.setTextColor(getColor(android.R.color.holo_green_dark))
            }
            disableAllButtons()
            Toast.makeText(this, "Game Over! $loser got 3 in a row and loses!", Toast.LENGTH_LONG).show()
            gameOver = true;
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

        myTurn = true;
        updateUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        BluetoothConnection.connectedThread?.messageListener = null
    }
}