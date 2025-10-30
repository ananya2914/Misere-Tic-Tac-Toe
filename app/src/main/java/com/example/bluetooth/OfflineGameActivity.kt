package com.example.bluetooth
import android.view.ViewGroup
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.gridlayout.widget.GridLayout
import com.example.bluetooth.ai.AIManager

import android.view.Gravity
import androidx.core.content.res.ResourcesCompat
import android.graphics.drawable.Drawable
import kotlin.math.min

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.core.content.ContextCompat


class OfflineGameActivity : ComponentActivity() {
    private lateinit var gridLayout: GridLayout
    private lateinit var statusText: TextView
    private val buttons = Array(3) { Array<Button?>(3) { null } }
    private var myTurn = true;
    private val mySymbol: String
    private val opponentSymbol: String
    var difficulty = "Easy"
    private var gameOver = false;
    val difficulties = listOf("Easy", "Medium", "Hard")
    private lateinit var aiManager: AIManager



    init {
            mySymbol = "X"
            opponentSymbol = "O"
            myTurn = true
    }
    private fun setMarkIcon(btn: Button, symbol: String, scale: Float = 0.6f) {
        val resId = if (symbol == "X") R.drawable.ic_x else R.drawable.ic_o

        // Wait for size if needed
        if (btn.width == 0 || btn.height == 0) {
            btn.post { setMarkIcon(btn, symbol, scale) }
            return
        }

        val side = (min(btn.width, btn.height) * scale).toInt()  // e.g., 60% of cell
        val d: Drawable = ResourcesCompat.getDrawable(resources, resId, theme)!!.mutate()
        d.setBounds(0, 0, side, side)

        // Center the icon in the cell
        btn.foreground = d
        btn.foregroundGravity = Gravity.CENTER

        // (Optional) hide the text but keep it for your logic
        btn.setTextColor(android.graphics.Color.TRANSPARENT)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_offline)

        val resetGameButton = findViewById<Button>(R.id.resetGame)
        val settingsButton = findViewById<Button>(R.id.settings)
        val settingsPage = findViewById<LinearLayout>(R.id.settingsPage)
        val title = findViewById<TextView>(R.id.title)

        val spinner = findViewById<Spinner>(R.id.spinner)

        val adapter = object : ArrayAdapter<String>(this, R.layout.spinner_item, difficulties) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                val tv = v.findViewById<TextView>(R.id.label)
                tv.text = getItem(position) ?: ""
                return v
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = layoutInflater.inflate(R.layout.spinner_dropdown_item, parent, false)
                val tv = v.findViewById<TextView>(R.id.label)
                tv.text = getItem(position) ?: ""

                val isSelected = (position == spinner.selectedItemPosition)
                v.isActivated = isSelected
                v.isSelected  = isSelected
                return v
            }
        }
        spinner.adapter = adapter


        aiManager = AIManager()
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        val settingsSubmit = findViewById<Button>(R.id.submit)
        val settingsLeave = findViewById<Button>(R.id.leaveSettings)
        settingsPage.visibility = View.GONE
        spinner.visibility = View.GONE
        settingsSubmit.visibility = View.GONE
        settingsLeave.visibility = View.GONE

        gridLayout = findViewById(R.id.gridLayout1)
        statusText = findViewById(R.id.statusText1)

        gridLayout.visibility = View.VISIBLE
        statusText.visibility = View.VISIBLE

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
                    background = ColorDrawable(Color.WHITE)
                    backgroundTintList = null   // Prevent gray tint
                    minHeight = 0
                    minWidth = 0
                    setPadding(0, 0, 0, 0)

                    isAllCaps = false
                    textSize = 36f
                    setTextColor(Color.parseColor("#2E2E2E"))
                    text = ""
                    tag = "$i,$j"

                    setOnClickListener { onButtonClick(i, j) }
                }
                buttons[i][j] = button
                gridLayout.addView(button)
            }
        }

        updateUI()

        settingsButton.setOnClickListener {

            spinner.visibility = View.VISIBLE
            settingsSubmit.visibility = View.VISIBLE
            settingsLeave.visibility = View.VISIBLE
            settingsPage.visibility = View.VISIBLE
            gridLayout.visibility = View.GONE
            statusText.visibility = View.GONE
            title.visibility = View.GONE
            resetGameButton.visibility = View.GONE
            settingsButton.visibility = View.GONE
        }

        settingsSubmit.setOnClickListener {
            difficulty = spinner.selectedItem.toString()
        }

        settingsLeave.setOnClickListener {
            spinner.visibility = View.GONE
            settingsSubmit.visibility = View.GONE
            settingsLeave.visibility = View.GONE
            settingsPage.visibility = View.GONE
            gridLayout.visibility = View.VISIBLE
            statusText.visibility = View.VISIBLE
            title.visibility = View.VISIBLE
            resetGameButton.visibility = View.VISIBLE
            settingsButton.visibility = View.VISIBLE
        }

        resetGameButton.setOnClickListener {
            resetGame()
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
        setMarkIcon(button, mySymbol)   // ← instead of setCompoundDrawables...
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
        setMarkIcon(button, opponentSymbol)   // ← instead of setCompoundDrawables...
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
                insertTestData(this, "Lost", difficulty)
            } else {
                statusText.text = "You Won!"
                statusText.setTextColor(getColor(android.R.color.holo_green_dark))
                insertTestData(this, "Won", difficulty)
            }
            disableAllButtons()
            Toast.makeText(this, "Game Over! $loser got 3 in a row and loses!", Toast.LENGTH_LONG).show()
            gameOver = true;
        } else if (isBoardFull()) {
            statusText.text = "It's a Draw!"
            statusText.setTextColor(getColor(android.R.color.holo_orange_dark))
            insertTestData(this, "Draw", difficulty)
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
        for (i in 0..2) for (j in 0..2) {
            buttons[i][j]?.apply {
                text = ""
                foreground = null            // ← clear the centered icon
                setTextColor(getColor(android.R.color.black)) // or keep transparent
                isEnabled = true
            }
        }
        myTurn = true
        gameOver = false
        updateUI()
    }



    override fun onDestroy() {
        super.onDestroy()
        BluetoothConnection.connectedThread?.messageListener = null
    }
}