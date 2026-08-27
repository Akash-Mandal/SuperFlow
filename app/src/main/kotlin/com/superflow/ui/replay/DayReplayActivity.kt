package com.superflow.ui.replay

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
import com.superflow.core.time.SfTime
import com.superflow.data.Repository
import com.superflow.domain.DayReplay
import com.superflow.ui.theme.SfThemeFromPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

class DayReplayActivity : ComponentActivity() {

    companion object {
        const val EXTRA_DATE = "date"
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = Repository.get(this)
        val dateStr = intent.getStringExtra(EXTRA_DATE)
        val date = SfTime.parseDate(dateStr ?: "") ?: repo.clock.today()
        val dateIso = SfTime.format(date)
        val dayLabel = try {
            SfTime.humanDay(date)
        } catch (_: Exception) {
            dateIso
        }

        setContent {
            SfThemeFromPrefs {
                var events by remember { mutableStateOf<List<DayReplay.DayEvent>?>(null) }

                LaunchedEffect(dateIso) {
                    val loaded = withContext(Dispatchers.IO) {
                        val habitTitle: (String) -> String = { id -> repo.habit(id)?.title ?: id }
                        DayReplay.build(
                            checkIns = repo.checkInsFor(dateIso),
                            journal = repo.journalEntriesFor(dateIso),
                            focus = repo.focusFor(dateIso),
                            energy = repo.energyFor(dateIso),
                            habitTitle = habitTitle,
                        )
                    }
                    events = loaded
                }

                Scaffold(
                    topBar = {
                        TopAppBar(title = { Text("Replay · $dayLabel") })
                    },
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        val ev = events
                        if (ev == null) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        } else {
                            DayReplayScreen(dateLabel = dayLabel, events = ev)
                        }
                    }
                }
            }
        }
    }
}
