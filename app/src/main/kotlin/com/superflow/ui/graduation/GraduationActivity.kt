package com.superflow.ui.graduation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.superflow.data.Repository
import com.superflow.data.model.Habit
import com.superflow.domain.Graduation
import com.superflow.ui.common.snack
import com.superflow.ui.theme.SfThemeFromPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GraduationActivity : ComponentActivity() {

    companion object {
        const val EXTRA_HABIT_ID = "habitId"
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = Repository.get(this)
        val habitId = intent.getStringExtra(EXTRA_HABIT_ID)

        setContent {
            SfThemeFromPrefs {
                var habit by remember { mutableStateOf<Habit?>(null) }
                var status by remember { mutableStateOf<Graduation.Status?>(null) }

                LaunchedEffect(habitId) {
                    val h = withContext(Dispatchers.IO) { repo.habit(habitId) }
                    val st = withContext(Dispatchers.IO) {
                        h?.let { Graduation.status(repo, it) }
                    }
                    habit = h
                    status = st
                }

                Scaffold(
                    topBar = { TopAppBar(title = { Text("Graduation") }) },
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        val h = habit
                        val st = status
                        when {
                            habitId.isNullOrBlank() -> Text("No habit specified.")
                            h == null || st == null -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            else -> GraduationScreen(
                                habit = h,
                                status = st,
                                onConfirm = {
                                    Thread {
                                        repo.saveHabit(
                                            h.copy(graduated = true, graduatedAt = System.currentTimeMillis()),
                                        )
                                    }.start()
                                    window.decorView.snack("Graduated — welcome to alumni.")
                                    finish()
                                },
                                onDismiss = { finish() },
                            )
                        }
                    }
                }
            }
        }
    }
}
