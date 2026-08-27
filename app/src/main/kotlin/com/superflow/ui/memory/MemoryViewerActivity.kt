package com.superflow.ui.memory

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
import com.superflow.data.model.AiMemory
import com.superflow.ui.theme.SfThemeFromPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MemoryViewerActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = Repository.get(this)
        setContent {
            SfThemeFromPrefs {
                var memories by remember { mutableStateOf<List<AiMemory>?>(null) }

                LaunchedEffect(Unit) {
                    memories = withContext(Dispatchers.IO) { repo.memories() }
                }

                Scaffold(
                    topBar = { TopAppBar(title = { Text("AI Memory") }) },
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        val m = memories
                        if (m == null) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        } else {
                            MemoryViewerScreen(
                                memories = m,
                                onDelete = { toDelete ->
                                    memories = m.filterNot { it.id == toDelete.id }
                                    // Fire-and-forget: no await needed for UI
                                    // but keep a scope via lifecycle if needed.
                                    // Using IO directly is safe for this small write.
                                    Thread {
                                        repo.deleteMemory(toDelete.id)
                                    }.start()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
