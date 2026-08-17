# SuperFlow v2 — Full AndroidX Rebuild

## Toolchain (VERIFIED WORKING)
- JDK 25 (pypi jdk4py), Kotlin 2.4.10 (npm), aapt2 (npm aaptjs3)
- dx dexer (LineageOS prebuilts) — dexes ALL of AndroidX+Material, multidex OK
- apksigner, android.jar API 34
- **Library source: Sketchware-Pro libs.zip = 118 pre-exploded AARs**
  Material 1.13.0, AppCompat 1.7.1, ConstraintLayout 2.2.1, RecyclerView 1.4.0,
  ViewPager2, Fragment 1.6.1, Activity 1.11.0, Lifecycle/ViewModel/LiveData 2.6.2,
  Coroutines 1.8.1, Room 2.2.5, WorkManager 2.7.0, Lottie 6.6.10, Glide 5.0.4,
  Gson, OkHttp 5.1.0, SwipeRefresh, CardView, Drawerlayout, Transition, Emoji2

## Key build discoveries
1. Strip META-INF/versions/** from every jar (dx: "unknown tag byte")
2. aapt2 link needs -R overlays in dependency order (least->most specific),
   NOT positional args — otherwise styleable/SearchView conflict
3. Must include full transitive res closure (drawerlayout, cardview, etc.)
4. minSdk 26 (dx invokedynamic requirement)
5. Multidex required (>65k methods) — needs MultiDexApplication or minSdk>=21 native

## Architecture (real Android, MVVM)
- ViewModel + LiveData/StateFlow + coroutines
- Room DAOs replacing hand-rolled SQLite
- WorkManager for background/reminders (replacing raw AlarmManager)
- Fragment + Navigation via ViewPager2/FragmentManager
- RecyclerView + DiffUtil + ListAdapter for all lists
- Material 3 components + MaterialContainerTransform / shared axis motion

## UI/UX targets
- Material 3 dynamic-ish theming, light+dark, edge-to-edge
- BottomNavigationView, MaterialToolbar, collapsing headers
- MaterialCardView, extended FAB, chips, sliders, switches
- BottomSheetDialogFragment for editors, MaterialDatePicker/TimePicker
- Ripples, elevation, shared-element transitions, spring physics
- Lottie for celebration; custom charts; swipe-to-check RecyclerView

## Omitted features to now implement
1. Voice control (SpeechRecognizer)
2. Home-screen widget (AppWidgetProvider)
3. Share-sheet accountability summary
4. Dark theme
5. OCR-less but better PDF; keep + improve
6. Blueprint amendments/branching + version diff
7. Cloud refinement of ledger
8. Localization scaffolding (strings externalized)
9. App shortcuts / deep links
10. Onboarding polish w/ animations
