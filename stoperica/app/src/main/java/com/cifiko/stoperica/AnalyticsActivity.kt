package com.cifiko.stoperica

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class AnalyticsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Retrieve sessions from the intent
        val sessions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("sessions", Session::class.java) ?: mutableListOf()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Session>("sessions") ?: mutableListOf()
        }

        Log.d("AnalyticsActivity", "Sessions received: ${sessions.size}")

        // Set the content using Jetpack Compose
        setContent {
            AnalyticsScreen(
                sessions = sessions,
                onSessionUpdated = { updatedSessions ->
                    // Save the updated sessions to SharedPreferences
                    saveSessions(updatedSessions)
                    // Return the updated session list to MainActivity
                    val resultIntent = Intent().apply {
                        putParcelableArrayListExtra("sessions", ArrayList(updatedSessions))
                    }
                    setResult(RESULT_OK, resultIntent)
                },
                onDeleteSession = { sessionId, username ->
                    // Call the function to delete the session from the web
                    deleteSessionFromWeb(sessionId, username)
                },
                onUpdateSession = { session ->
                    // Call the function to update the session on the web
                    updateSessionOnWeb(session)
                }
            )
        }
    }

    private fun saveSessions(sessions: List<Session>) {
        val sharedPreferences = getSharedPreferences("StopericaSessions", MODE_PRIVATE)
        sharedPreferences.edit().apply {
            val json = Gson().toJson(sessions)
            putString("sessions", json)
            apply()
        }
        Log.d("AnalyticsActivity", "Sessions saved to SharedPreferences")
    }

    // Function to delete a session from the web
    private fun deleteSessionFromWeb(sessionId: String, username: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://stopwatch-yuik.onrender.com/delete-session/$sessionId")
                    .delete()
                    .addHeader("Username", username)  // Include the username in the headers
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    Log.d("AnalyticsActivity", "Session deleted from web: $sessionId")
                } else {
                    Log.e("AnalyticsActivity", "Failed to delete session from web: ${response.message}")
                }
            } catch (e: Exception) {
                Log.e("AnalyticsActivity", "Failed to delete session from web", e)
            }
        }
    }

    // Function to update a session on the web
    private fun updateSessionOnWeb(session: Session) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val json = Gson().toJson(session)
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = json.toRequestBody(mediaType)
                val request = Request.Builder()
                    .url("https://stopwatch-yuik.onrender.com/upload") // Use the same upload endpoint
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    Log.d("AnalyticsActivity", "Session updated on web: ${session.id}")
                } else {
                    Log.e("AnalyticsActivity", "Failed to update session on web: ${response.message}")
                }
            } catch (e: Exception) {
                Log.e("AnalyticsActivity", "Failed to update session on web", e)
            }
        }
    }
}

@Composable
fun AnalyticsScreen(
    sessions: List<Session>,
    onSessionUpdated: (List<Session>) -> Unit,
    onDeleteSession: (String, String) -> Unit,
    onUpdateSession: (Session) -> Unit
) {
    var fastestSession by remember { mutableStateOf<Session?>(null) }
    var slowestSession by remember { mutableStateOf<Session?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    var newSessionName by remember { mutableStateOf("") }
    var selectedSession by remember { mutableStateOf<Session?>(null) }
    var localSessions by remember { mutableStateOf(sessions) }

    // Reverse the session list to show the last session on top
    val reversedSessions = localSessions.reversed()

    // Calculate fastest and slowest sessions when sessions change
    LaunchedEffect(localSessions) {
        if (localSessions.isNotEmpty()) {
            // Find the session with the fastest lap
            fastestSession = localSessions.minByOrNull { parseTime(it.fastestLap) }

            // Find the session with the slowest lap
            slowestSession = localSessions.maxByOrNull { parseTime(it.slowestLap) }
        }
    }

    // Display session analytics in a scrollable list
    LazyColumn(modifier = Modifier.padding(16.dp)) {
        items(reversedSessions) { session ->
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                // Display session name with edit and delete options
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Session Name: ${session.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            isEditing = true
                            newSessionName = session.name
                            selectedSession = session
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_edit),
                            contentDescription = "Edit Session Name"
                        )
                    }
                    IconButton(
                        onClick = {
                            // Delete the session locally
                            localSessions = localSessions - session
                            onSessionUpdated(localSessions)

                            // Delete the session from the web
                            onDeleteSession(session.id, session.username)
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_delete),
                            contentDescription = "Delete Session"
                        )
                    }
                }

                // Display session details
                Text(text = "Date: ${session.date}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Ride Time: ${session.startTime}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Fastest Lap: ${session.fastestLap}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Slowest Lap: ${session.slowestLap}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Average Lap: ${session.averageLap}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Consistency: ${session.consistency}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Total Time: ${session.totalTime}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Location: ${session.location}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    // Edit Session Name Dialog
    if (isEditing) {
        AlertDialog(
            onDismissRequest = { isEditing = false },
            title = { Text("Edit Session Name") },
            text = {
                OutlinedTextField(
                    value = newSessionName,
                    onValueChange = { newSessionName = it },
                    label = { Text("Session Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedSession?.let { session ->
                            val updatedSession = session.copy(name = newSessionName)
                            localSessions = localSessions.map {
                                if (it.id == session.id) updatedSession else it
                            }
                            onSessionUpdated(localSessions)
                            onUpdateSession(updatedSession) // Update the session on the web
                            isEditing = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                Button(
                    onClick = { isEditing = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

// Helper function to parse lap time strings into milliseconds
private fun parseTime(timeString: String): Long {
    return try {
        val timePortion = timeString.substringAfterLast(": ").trim()
        if (!timePortion.matches(Regex("\\d{2}:\\d{2}:\\d{2}"))) {
            Log.e("AnalyticsActivity", "Invalid time format: $timePortion")
            return Long.MAX_VALUE
        }

        timePortion.split(":").let { parts ->
            val minutes = parts[0].toLong()
            val seconds = parts[1].toLong()
            val milliseconds = parts[2].toLong()
            minutes * 60000 + seconds * 1000 + milliseconds * 10
        }
    } catch (e: Exception) {
        Log.e("AnalyticsActivity", "Failed to parse time: $timeString", e)
        Long.MAX_VALUE
    }
}