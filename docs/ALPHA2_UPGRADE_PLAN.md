# SuperFlow Alpha2 — Feature Upgrade Plan

**Version:** alpha2 · August 2026
**Theme:** "From tracker to growth companion"
**Tagline:** Every feature in alpha2 exists to answer one question: *what should I do next?*

---

## What's New in Alpha2

### 1. Global Search

A search bar accessible from every screen (swipe down on toolbar or tap search icon).

**Searches across:**
- Habits (title, cue, anchor)
- Identities (statement)
- Goals (title, why)
- Systems (title, description)
- Reviews (all fields)
- Journal entries (content)
- AI conversation history
- Audit trail (command, summary)
- Obstacle plans (if/then text)

**Results grouped by type, ranked by relevance.** Tapping a result navigates directly to the entity.

```kotlin
data class SearchResult(
    val type: String,          // "habit", "goal", "identity", etc.
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: Int,
    val relevance: Float       // 0.0–1.0
)

object Search {
    fun search(repo: Repository, query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val q = query.trim().lowercase()
        val results = mutableListOf<SearchResult>()

        // Exact match first, then prefix, then contains
        repo.habits(true).forEach { h ->
            val score = relevance(q, h.title, h.cueTime, h.anchorText)
            if (score > 0) results.add(SearchResult("habit", h.id, h.title,
                "${Capabilities.daysLabel(h)} · ${h.cueTime}", R.drawable.ic_bolt, score))
        }
        // ... same for identities, goals, systems, reviews, obstacles, journal
        return results.sortedByDescending { it.relevance }
    }

    private fun relevance(query: String, vararg fields: String): Float {
        val lower = fields.map { it.lowercase() }
        return when {
            lower.any { it == query } -> 1.0f
            lower.any { it.startsWith(query) } -> 0.8f
            lower.any { it.contains(query) } -> 0.5f
            lower.any { query.contains(it) && it.length > 3 } -> 0.3f
            levenshtein(query, lower.first()) < 3 -> 0.2f  // Fuzzy
            else -> 0f
        }
    }
}
```

---

### 2. Habit Templates Library

Pre-built, research-backed habit templates organized by life area.

**100+ templates across 8 areas:**

| Area | Example Templates |
|------|------------------|
| **Health** | Morning walk, Drink water, Stretch, Sleep by 10pm, Take vitamins, 10K steps |
| **Learning** | Read 20 min, Language practice, Online course, Take notes, Review flashcards |
| **Relationships** | Call a friend, Family dinner, Express gratitude, Active listening, No phone during meals |
| **Work** | Plan tomorrow tonight, Deep work block, Inbox zero, Stand-up prep, End-of-day shutdown |
| **Creativity** | Write 500 words, Sketch for 15 min, Practice instrument, Brainstorm ideas, Create something |
| **Finance** | Track spending, No-spend day, Review budget, Save automatic transfer, Invest research |
| **Mindfulness** | Meditate 10 min, Gratitude journal, Body scan, Digital sunset, Breathing exercise |
| **Home** | Make bed, Tidy for 10 min, Meal prep, Laundry, Water plants |

**Each template includes:**
- Pre-filled Four Laws (benefit, cue, reward, friction, environment prep)
- Suggested ladder (Tiny/Min/Std/Stretch)
- Suggested schedule
- Difficulty rating
- Estimated time
- Common obstacle plans

**Integration:** Template picker appears as first step in Habit Designer. Also accessible from Journey's "Add habit" button.

---

### 3. Guided Checkpoint Screens

Morning, Midday, and Evening checkpoints become **guided 30-second experiences** instead of empty buttons.

**Morning Checkpoint:**
```
┌──────────────────────────────────┐
│  ☀ Good morning                  │
│                                  │
│  How's your energy?              │
│  ○  ○  ●  ○  ○   3/5            │
│                                  │
│  TODAY'S PLAN                    │
│  ┌────────────────────────────┐  │
│  │ 07:30  Walk 10 min         │  │
│  │ 08:00  Meditate 5 min      │  │
│  │ 12:00  Journal             │  │
│  │ 21:00  Read 20 min         │  │
│  └────────────────────────────┘  │
│                                  │
│  FOCUS (pick up to 3)            │
│  [ ] ____________________        │
│  [ ] ____________________        │
│  [ ] ____________________        │
│                                  │
│  [Start the day]                 │
└──────────────────────────────────┘
```

**Evening Checkpoint:**
```
┌──────────────────────────────────┐
│  🌙 Evening reflection           │
│                                  │
│  How was your energy today?      │
│  ○  ○  ○  ●  ○   4/5            │
│                                  │
│  TODAY: 3 of 4 done              │
│  [Progress Ring]                 │
│                                  │
│  What went well?                 │
│  ┌────────────────────────────┐  │
│  │                            │  │
│  └────────────────────────────┘  │
│                                  │
│  What was hard?                  │
│  ┌────────────────────────────┐  │
│  │                            │  │
│  └────────────────────────────┘  │
│                                  │
│  One thing for tomorrow?         │
│  ┌────────────────────────────┐  │
│  │                            │  │
│  └────────────────────────────┘  │
│                                  │
│  [Save reflection]               │
└──────────────────────────────────┘
```

---

### 4. Plan Tomorrow Flow

Triggered from Today menu or evening checkpoint. A guided 2-minute flow:

```
Step 1: Review today
  "You completed 3 of 4 habits. Journal was missed."
  [Carry Journal to tomorrow] [Skip]

Step 2: Set focus
  "What 1-3 things deserve emphasis tomorrow?"
  [Add focus item]

Step 3: Energy forecast
  "Based on your patterns, tomorrow morning tends to be [medium] energy."
  "Schedule harder habits in the morning?"

Step 4: Confirm
  "Tomorrow: 4 habits, 3 focus items, ~55 minutes."
  [Looks good] [Adjust]
```

---

### 5. Pause / Vacation Mode

A simple toggle in Settings: "I'm taking a break."

**Options:**
- Pause all habits for X days (date range picker)
- Pause specific habits only
- Reason (optional): "Vacation", "Illness", "Travel", "Other"
- Auto-resume date
- On resume: "Welcome back. Here's what changed while you were away..."

**Data model:** Uses existing `PauseWindow` — just needs a proper UI.

---

### 6. Habit Graduation

After a habit reaches **66+ days at 90%+ consistency**, the app suggests graduation:

```
┌──────────────────────────────────┐
│  🌱 Walk is becoming automatic   │
│                                  │
│  92% consistency over 78 days.   │
│  That's not effort anymore —     │
│  that's who you are.             │
│                                  │
│  You could:                      │
│  [Graduate it] — move to         │
│    "maintenance" (no daily       │
│    tracking, just weekly check)  │
│  [Keep tracking] — it's working  │
│  [Upgrade it] — increase the     │
│    standard version              │
└──────────────────────────────────┘
```

**Graduated habits:**
- Move to a "Maintenance" section in Journey
- No longer appear on Today by default
- Weekly check-in: "Did you walk this week?" (yes/no)
- Free up capacity for new habits

---

### 7. Smart Notification Actions

Habit reminders get inline action buttons:

```
┌──────────────────────────────────┐
│  Walk 10 min                     │
│  After breakfast · 07:30         │
│                                  │
│  [✓ Standard] [↓ Tiny] [⏭ Skip] │
└──────────────────────────────────┘
```

**Implementation:**
```kotlin
NotificationCompat.Builder(context, CHANNEL_HABITS)
    .setContentTitle(habit.title)
    .setContentText(habit.tinyStart.ifBlank { habit.title })
    .setSmallIcon(R.drawable.ic_bolt)
    .addAction(R.drawable.ic_check, "Done",
        checkInPendingIntent(context, habit.id, "STANDARD"))
    .addAction(R.drawable.ic_check, "Tiny",
        checkInPendingIntent(context, habit.id, "TINY"))
    .addAction(R.drawable.ic_pause, "Skip",
        skipPendingIntent(context, habit.id))
    .setContentIntent(openAppPendingIntent(context))
    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
    .setCategory(NotificationCompat.CATEGORY_REMINDER)
```

**BroadcastReceiver** handles the actions without opening the app:
```kotlin
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getStringExtra("habitId") ?: return
        val action = intent.action ?: return
        val bus = CommandBus.get(context)

        when (action) {
            "CHECK_STANDARD" -> bus.execute("check_in",
                jsonOf("habit" to habitId, "level" to "STANDARD"), Actor.USER)
            "CHECK_TINY" -> bus.execute("check_in",
                jsonOf("habit" to habitId, "level" to "TINY"), Actor.USER)
            "SKIP" -> bus.execute("skip_habit",
                jsonOf("habit" to habitId), Actor.USER)
        }
        TodayWidget.refresh(context)
    }
}
```

---

### 8. Weekly Summary Notification

Every Sunday evening (configurable), a rich notification:

```
┌──────────────────────────────────┐
│  Your Week in SuperFlow          │
│                                  │
│  Consistency: 82% (↑12%)        │
│  Repetitions: 24                 │
│  Best habit: Walk (100%)         │
│  Recoveries: 3                   │
│                                  │
│  [View full report] [Start       │
│   review] [Dismiss]              │
└──────────────────────────────────┘
```

---

### 9. App Lock

Optional PIN or biometric lock on app open.

```kotlin
class AppLockActivity : AppCompatActivity() {
    // Uses BiometricPrompt for fingerprint/face
    // Falls back to PIN entry
    // Stores hashed PIN in encrypted SharedPreferences
    // Auto-locks after configurable timeout (1min, 5min, 15min, always)
}
```

**Settings:**
- Enable/disable app lock
- Choose method: Biometric, PIN, or both
- Lock timeout: Immediately, 1 min, 5 min, 15 min, 30 min
- Unlock with biometric only (no PIN fallback)

---

### 10. Auto-Backup

Scheduled backup to local storage or SAF (Storage Access Framework).

```kotlin
class BackupWorker(context: Context, params: WorkerParameters)
    : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = Repository.get(applicationContext)
        val prefs = Prefs.get(applicationContext)
        if (!prefs.autoBackupEnabled) return Result.success()

        val json = withContext(Dispatchers.IO) { Serial.exportAll(repo) }
        val dir = File(applicationContext.filesDir, "backups").apply { mkdirs() }
        val date = SfTime.format(repo.clock.today())
        val file = File(dir, "superflow-backup-$date.json")
        file.writeText(json.toString(2))

        // Keep max 7 backups
        dir.listFiles()?.sortedByDescending { it.lastModified() }
            ?.drop(7)?.forEach { it.delete() }

        return Result.success()
    }
}
```

**Settings:**
- Auto-backup: Daily, Weekly, Off
- Backup location: App internal, Choose folder (SAF)
- Max backups to keep: 3, 7, 14, 30
- Last backup: [date] [Restore]

---

### 11. Drag-and-Drop Habit Reordering

In Today and Journey, habits can be reordered by long-press and drag.

```kotlin
val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
    ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
) {
    override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder,
                        to: RecyclerView.ViewHolder): Boolean {
        adapter.moveItem(from.adapterPosition, to.adapterPosition)
        return true
    }
    override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}
    override fun isLongPressDragEnabled() = true
    override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
        // Only allow drag on habit items, not sections
        return if (vh is TodayAdapter.HabitVH) super.getMovementFlags(rv, vh) else 0
    }
})
touchHelper.attachToRecyclerView(list)
```

**Persistence:** Habit `orderIndex` is updated on drop and saved via `update_habit_order` command.

---

### 12. Duplicate Habit

Context menu action: "Duplicate" — deep copies a habit with all its design fields.

```kotlin
Capability("duplicate_habit", "Create a copy of an existing habit",
    listOf("habit" to "id"), Risk.LOW) { c ->
    val original = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
    val copy = original.copy(
        id = newId(),
        title = "${original.title} (copy)",
        createdAt = System.currentTimeMillis(),
        orderIndex = original.orderIndex + 1
    )
    c.repo.saveHabit(copy)
    // Also copy obstacle plans
    c.repo.obstacles(original.id).forEach { o ->
        c.repo.saveObstacle(o.copy(id = newId(), habitId = copy.id))
    }
    okResult("Duplicated \"${original.title}\"", jsonOf("id" to copy.id))
}
```

---

### 13. Share Progress as Image

Generate a shareable card image from progress data.

```kotlin
fun generateShareCard(repo: Repository, days: Int = 7): Bitmap {
    val width = 1080
    val height = 1350  // Instagram-friendly 4:5
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Draw background with brand colors
    // Draw habit consistency bars
    // Draw heatmap strip
    // Draw identity statement
    // Draw "Shared from SuperFlow" watermark

    return bitmap
}
```

**Share options:** Instagram Stories, Twitter, WhatsApp, Save to gallery.

---

### 14. Quiet Hours Per Day

Different quiet hours for weekdays vs weekends.

```kotlin
data class QuietHours(
    val weekdaysFrom: String = "22:00",
    val weekdaysTo: String = "07:00",
    val weekendsFrom: String = "23:00",
    val weekendsTo: String = "08:00"
)
```

---

### 15. Notification Channels (Expanded)

| Channel | Purpose | Default |
|---------|---------|---------|
| `habits` | Habit reminders | Sound + vibration |
| `checkpoints` | Morning/Midday/Evening | Sound only |
| `reviews` | Review reminders | Sound only |
| `milestones` | Quiet acknowledgments | Silent (visual only) |
| `ai_suggestions` | Proactive AI suggestions | Silent |
| `weekly_summary` | Weekly report | Sound only |
| `backup` | Backup status | Silent |

---

### 16. Fuzzy Habit Search

`Repository.findHabit()` upgraded with Levenshtein distance for typo tolerance:

```kotlin
fun findHabit(queryText: String): Habit? {
    val q = queryText.trim().lowercase()
    if (q.isEmpty()) return null
    val all = habits(true)
    return all.firstOrNull { it.title.lowercase() == q }
        ?: all.firstOrNull { it.title.lowercase().startsWith(q) }
        ?: all.firstOrNull { it.title.lowercase().contains(q) }
        ?: all.firstOrNull { q.contains(it.title.lowercase()) }
        ?: all.minByOrNull { levenshtein(q, it.title.lowercase()) }
            ?.takeIf { levenshtein(q, it.title.lowercase()) <= 3 }
}

fun levenshtein(a: String, b: String): Int {
    val matrix = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) matrix[i][0] = i
    for (j in 0..b.length) matrix[0][j] = j
    for (i in 1..a.length) for (j in 1..b.length) {
        val cost = if (a[i-1] == b[j-1]) 0 else 1
        matrix[i][j] = minOf(matrix[i-1][j] + 1, matrix[i][j-1] + 1, matrix[i-1][j-1] + cost)
    }
    return matrix[a.length][b.length]
}
```

---

### 17. Data Integrity Diagnostics

In AI Engine → Diagnostics:

```kotlin
fun checkIntegrity(repo: Repository): String {
    val issues = mutableListOf<String>()

    // Orphaned check-ins (habit deleted)
    val habitIds = repo.habits(true).map { it.id }.toSet()
    val orphanCheckIns = repo.checkIns().filter { it.habitId !in habitIds }
    if (orphanCheckIns.isNotEmpty()) {
        issues.add("${orphanCheckIns.size} check-ins for deleted habits")
    }

    // Orphaned obstacles
    val orphanObstacles = repo.obstacles().filter { it.habitId !in habitIds }
    if (orphanObstacles.isNotEmpty()) {
        issues.add("${orphanObstacles.size} obstacle plans for deleted habits")
    }

    // Goals without identities
    val identityIds = repo.identities(true).map { it.id }.toSet()
    val orphanGoals = repo.goals().filter { it.identityId !in identityIds && it.identityId != null }
    if (orphanGoals.isNotEmpty()) {
        issues.add("${orphanGoals.size} goals linked to deleted identities")
    }

    // Habits without systems
    val systemIds = repo.systems().map { it.id }.toSet()
    val orphanHabits = repo.habits().filter { it.systemId !in systemIds && it.systemId != null }
    if (orphanHabits.isNotEmpty()) {
        issues.add("${orphanHabits.size} habits linked to deleted systems")
    }

    return if (issues.isEmpty()) "✓ All data is consistent"
    else "Issues found:\n" + issues.joinToString("\n") { "· $it" }
}
```

With a "Fix all" button that cleans up orphaned records.

---

### 18. RTL Layout Support

```xml
<!-- AndroidManifest.xml -->
<application android:supportsRtl="true" ...>
```

All layouts use `paddingStart`/`paddingEnd` instead of `paddingLeft`/`paddingRight`. Gravity uses `START`/`END` instead of `LEFT`/`RIGHT`.

---

### 19. Locale-Aware Date Formatting

Replace all `SfTime.humanDay()` and `SfTime.shortDay()` calls with locale-aware formatters:

```kotlin
object SfTime {
    fun humanDay(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))

    fun shortDay(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(DateTimeFormatter.ofPattern("d MMM", locale))

    fun dayLetter(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(DateTimeFormatter.ofPattern("EEE", locale)).take(1)
}
```

---

### 20. Dynamic App Shortcuts

Register shortcuts based on the user's actual habits:

```kotlin
fun updateShortcuts(context: Context) {
    val repo = Repository.get(context)
    val habits = repo.habits().take(3)

    val shortcuts = mutableListOf<ShortcutInfoCompat>()

    // Quick check-in for top 3 habits
    habits.forEach { h ->
        shortcuts.add(ShortcutInfoCompat.Builder(context, "checkin_${h.id}")
            .setShortLabel(h.title)
            .setLongLabel("Check in: ${h.title}")
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_check))
            .setIntent(Intent(context, MainActivity::class.java).apply {
                action = "CHECK_IN"
                putExtra("habitId", h.id)
            })
            .build())
    }

    // Blueprint Studio
    shortcuts.add(ShortcutInfoCompat.Builder(context, "blueprint")
        .setShortLabel("Blueprint")
        .setIcon(IconCompat.createWithResource(context, R.drawable.ic_blueprint))
        .setIntent(Intent(context, BlueprintActivity::class.java))
        .build())

    ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
}
```

---

## Alpha2 Changelog

```
SuperFlow Alpha2 — "From tracker to growth companion"

NEW
• Global search across all entities
• 100+ habit templates organized by life area
• Guided checkpoint screens (morning/midday/evening)
• Plan Tomorrow flow with energy-aware suggestions
• Pause / Vacation mode with date range
• Habit graduation after 66+ consistent days
• Smart notification action buttons (Done/Tiny/Skip)
• Weekly summary notification
• App lock (PIN + biometric)
• Auto-backup with configurable schedule
• Drag-and-drop habit reordering
• Duplicate habit action
• Share progress as image card
• Quiet hours per day (weekday vs weekend)
• Expanded notification channels (7 types)
• Fuzzy habit search with typo tolerance
• Data integrity diagnostics with auto-fix
• RTL layout support
• Locale-aware date formatting
• Dynamic app shortcuts based on your habits

IMPROVED
• AI Engine: 30+ new configurable parameters
• Insights tab: complete redesign with 10 new chart types
• Blueprint Studio: intent-first progressive plan generation
• Four Laws: living tools with evaluation loops
• Ladder: adaptive difficulty based on performance
• Reviews: data-driven pre-fill with tracked action items
• Recovery: preventive nudges and pattern detection
• Check-ins: context tags, difficulty rating, quality rating

FIXED
• TTS (text-to-speech) now implemented
• STT (speech-to-text) works on de-Googled devices
• Energy data now influences recommendations
• Database indexes added for performance
• Orphan record cleanup
• Rapid tap debouncing
• Import validation with specific error messages
```

---

*Alpha2 transforms SuperFlow from a well-built tracker into an intelligent growth companion that knows your patterns, suggests your next move, and quietly celebrates your progress.*
