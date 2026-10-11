package com.example.bluetooth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import com.example.bluetooth.ui.theme.BluetoothTheme
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import android.content.Context
import kotlinx.coroutines.CoroutineScope

class HistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BluetoothTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DisplayRecords() // ✅ Composable is now in the same file
                }
            }
        }
    }
}


fun insertTestData(context: Context, result: String, difficulty: String) {
    val dao = AppDatabase.getDatabase(context).pastGameDao()
    val currentDateTime = LocalDateTime.now()
    val formattedDateTime = currentDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    CoroutineScope(Dispatchers.IO).launch {
        // To clear the table
        // dao.clearAllGames()
        dao.insertGameRecord(PastGame(dateTime = formattedDateTime, result = result, difficulty = difficulty))
    }
}
@Composable
fun DisplayRecords() {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).pastGameDao() }
    var records by remember { mutableStateOf<List<PastGame>>(emptyList()) }

    // Load from DB on first composition using IO dispatcher
    LaunchedEffect(dao) {
        records = withContext(Dispatchers.IO) { dao.getAllGames() }
    }

    LazyColumn(
        modifier = Modifier
            .padding(16.dp)
    ) {
        if (records.isEmpty()) {
            item { Text("No records found.") }
        } else {
            items(
                items = records,
                key = { it.id } // provide a stable key
            ) { game ->
                val diff = game.difficulty.lowercase()
                val difficultyColor = when (diff) {
                    "easy" -> MaterialTheme.colorScheme.tertiaryContainer
                    "normal" -> MaterialTheme.colorScheme.secondaryContainer
                    "hard" -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }

                Card(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // 📅 Date header
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "📅  ${game.dateTime}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        // 🏆 Result section
                        val resultEmoji = when (game.result.lowercase()) {
                            "won" -> "🏆"
                            "lost" -> "❌"
                            else -> "🤝"
                        }
                        Text(
                            text = "$resultEmoji  Result: ${game.result}",
                            style = MaterialTheme.typography.bodyLarge
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // 💪 Difficulty badge
                        Row {
                            Text(
                                text = "Difficulty",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = difficultyColor),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = " ${game.difficulty} ",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}