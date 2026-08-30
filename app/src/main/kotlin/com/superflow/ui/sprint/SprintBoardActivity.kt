package com.superflow.ui.sprint

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import com.superflow.R
import com.superflow.core.time.SfTime
import com.superflow.data.Repository
import com.superflow.data.model.Sprint
import com.superflow.data.model.SprintStatus
import com.superflow.ui.sheets.TextInputSheet
import com.superflow.ui.theme.SfThemeFromPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

class SprintBoardActivity : AppCompatActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = Repository.get(this)

        setContent {
            SfThemeFromPrefs {
                var sprints by remember { mutableStateOf<List<Sprint>?>(null) }

                LaunchedEffect(Unit) {
                    sprints = withContext(Dispatchers.IO) { repo.sprints() }
                }

                Scaffold(
                    topBar = { TopAppBar(title = { Text("Sprints") }) },
                    floatingActionButton = {
                        FloatingActionButton(onClick = {
                            TextInputSheet.show(
                                supportFragmentManager,
                                title = "New sprint",
                                hint = "What are you committing to?",
                            ) { text ->
                                if (text.isNullOrBlank()) return@show
                                val today = SfTime.format(LocalDate.now())
                                val end = SfTime.format(LocalDate.now().plusDays(14))
                                val sprint = Sprint(
                                    title = text.trim(),
                                    startDate = today,
                                    endDate = end,
                                    status = SprintStatus.ACTIVE,
                                )
                                Thread {
                                    repo.saveSprint(sprint)
                                    runOnUiThread {
                                        sprints = (sprints ?: emptyList()) + sprint
                                    }
                                }.start()
                            }
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_add),
                                contentDescription = "New sprint",
                            )
                        }
                    },
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        val list = sprints
                        if (list == null) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        } else {
                            SprintBoardScreen(
                                sprints = list,
                                onSelect = { /* detail sheet later */ },
                            )
                        }
                    }
                }
            }
        }
    }
}
