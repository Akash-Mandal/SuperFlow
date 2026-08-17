# Building SuperFlow

SuperFlow is a **full AndroidX / Material 3 application**. It is built without
Gradle, because the build environment cannot reach Google Maven, Maven Central,
`services.gradle.org` or `plugins.gradle.org`. Instead the script drives the
Android build tools directly, against a local set of pre-exploded AARs.

The result is a normal Android app — real `AppCompatActivity`, `Fragment`,
`ViewModel`, `RecyclerView`, `ViewPager2`, Material 3 components, coroutines,
`androidx.sqlite`, WorkManager, Lottie — not a framework-only app.

## Dependencies

76 libraries, listed in dependency order in [`tools/libs.txt`](../tools/libs.txt):

| Area | Libraries |
|---|---|
| UI | Material **1.13.0**, AppCompat 1.7.1, ConstraintLayout 2.2.1, RecyclerView 1.4.0, ViewPager2, CoordinatorLayout, SwipeRefresh, CardView, Transition |
| Architecture | Fragment 1.6.1, Activity 1.11.0, Lifecycle/ViewModel/LiveData 2.6.2 (+ `-ktx`), SavedState, Startup |
| Async | Kotlin Coroutines 1.8.1 (core + android) |
| Data | `androidx.sqlite` 2.1.0 + framework, Room runtime, Gson |
| Background | WorkManager 2.7.0 (+ `-ktx` for `CoroutineWorker`) |
| Preferences | DataStore 1.0 (core + preferences) |
| Motion | Lottie 6.6.10, DynamicAnimation |
| Net | OkHttp 5.1.0, Okio |

## Toolchain

Expected under `$SUPERFLOW_TOOLCHAIN` (default `~/toolchain`):

```
toolchain/
├── jdk/                        JDK 25 runtime            (PyPI: jdk4py)
├── kotlinc/                    Kotlin 2.4.x compiler     (npm: kotlin-compiler)
├── bin/aapt2                   Android Asset Packaging 2 (npm: aaptjs3)
├── lib/dx.jar                  AOSP dexer                (LineageOS prebuilts)
├── lib/apksigner.jar           APK signer
├── lib/kotlin-stdlib-clean.jar Kotlin stdlib, META-INF/versions stripped
├── platforms/android-34.jar    API 34 android.jar        (Sable/android-platforms)
├── androidlibs/<lib>/          Exploded AAR: res/, AndroidManifest.xml
└── libjars/<lib>.jar           That AAR's classes.jar
```

## Five problems this build had to solve

1. **`META-INF/versions/**` breaks `dx`** (`unknown tag byte`). Every jar has
   those multi-release directories stripped.
2. **`aapt2` styleable conflicts.** Passing library resources positionally makes
   Material and AppCompat collide on `styleable/SearchView`. They must be passed
   as ordered `-R` overlays, least specific first.
3. **The full transitive resource closure is required** — drawerlayout, cardview
   and friends, or Material's styles fail to resolve.
4. **`.kotlin_module` files must survive.** Stripping all of `META-INF` from the
   `-ktx` artifacts silently removes the module metadata Kotlin needs to resolve
   top-level extensions, and `viewModels()` / `viewModelScope` /
   `repeatOnLifecycle` stop resolving with a confusing "unresolved reference".
5. **Duplicate classes at dex time.** Newer `activity`/`lifecycle` artifacts
   bundle classes the older `-ktx` artifacts also carry. The `-ktx` jars stay
   intact on the *compile* classpath (see #4); the overlap is removed only in a
   dex-time staging copy.

`minSdk` is **26** because `dx` refuses `invokedynamic` below that — which
matches the Grand Plan's stated policy anyway.

## Build

```bash
tools/build_apk.sh release     # -> build/outputs/superflow-release.apk
tools/build_apk.sh debug       # -> build/outputs/superflow-debug.apk
```

Pipeline: compile library resources (cached) → `aapt2 link` with ordered
overlays → generate Kotlin `R` objects from `R.txt` → `kotlinc` → `dx --multi-dex`
→ package → sign (v1+v2+v3).

Signing keys are generated on first run into `build/` and are git-ignored.
Override with `SUPERFLOW_KEYSTORE`, `SUPERFLOW_KEYSTORE_PASS`, `SUPERFLOW_KEY_ALIAS`.

### Generated R classes

`kotlinc` cannot consume aapt2's Java `R.java`, so `tools/gen_res.py` turns
`R.txt` into Kotlin objects — including `styleable` int arrays, and mirrors of
the same ids under `com.google.android.material` and `androidx.appcompat`, which
is what those libraries' own code links against.

## Tests

```bash
tools/run_tests.sh
```

143 assertions across four suites:

- **CoreTest** (62) — injected clock, DST gaps and overlaps, leap days, locale
  week starts, every recurrence form, and the opportunity engine: planned skips
  and pauses never creating misses, today never counting as a miss, unscheduled
  days staying transparent, runs/recoveries, never-miss-twice, and pro-rated
  flexible quotas.
- **LogicTest** (21) — habit ladder fallbacks, contract generation, enum parsing.
- **ParseTest** (27) — recurrence parsing and round-tripping, JSON extracted from
  fenced, prose-wrapped, nested and escaped model output.
- **AiTest** (33) — natural-language habit parsing, prompt-injection detection,
  Blueprint extraction with citations, conflicts and coverage.

`tools/test/JsonShim.kt` supplies a real `org.json` for the desktop JVM (the
stub in `android.jar` throws). It is never compiled into the APK.

## Output

| | |
|---|---|
| Package | `com.superflow` 2.0.0 (code 2) |
| minSdk / targetSdk | 26 / 34 |
| Size | ~7.8 MB, 2 dex |
| Signatures | v2 + v3 verified |
| App classes | 343 |
| Capabilities | 49 |

## AAB

Producing an Android App Bundle needs `bundletool`, which was not reachable.
The release APK carries the complete feature set; AAB packaging is the only
distribution artifact still outstanding.
