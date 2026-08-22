# SuperFlow Build & CI Playbook

Everything learned while making the repository build cleanly and get the
**PR #12 CI pipeline** (static checks → build → emulator) fully green. Use
this as the reference for *any future build, fix or release*.

It is split into four parts:

1. [Technology stack](#1-technology-stack) — what the project is built on.
2. [The CI pipeline](#2-the-ci-pipeline) — what GitHub Actions runs and why.
3. [Procedures & step-by-step guides](#3-procedures--step-by-step-guides) —
   how to build, verify, run on a device, and ship.
4. [Tricks, hacks & traps we hit](#4-tricks-hacks--traps-we-hit) — the
   non-obvious things that cost the most time, so you do not repeat them.

---

## 1. Technology stack

| Concern | Choice | Version | Notes |
|---|---|---|---|
| Language | Kotlin | 2.2.0 | Backtick method names are legal Kotlin but break dexing below API 28 (see [trap](#t4-r8-rejects-spaces-in-identifier-names-on-minsdk--28)) |
| Build system | Gradle (wrapper) | 8.11.1 | Always use `./gradlew`, never a system Gradle |
| Android Gradle Plugin | AGP | 8.9.1 | Requires JDK 17 |
| compileSdk / targetSdk / minSdk | — | 36 / 34 / 26 | Emulator matrix runs API **26** and **34** |
| UI toolkit | View + Compose | Compose 1.7.6, Material3 1.3.1, Material 1.13.0, AppCompat 1.7.1 | Both coexist while the Compose migration lands |
| Data | `androidx.sqlite` (+ framework) | 2.1.0 | Hand-written DAOs, no Room annotation processor (offline constraint) |
| Background | WorkManager | 2.7.0 | Initialized by the `androidx.startup` provider merged from the AAR — never init manually |
| Async | Kotlin Coroutines | 1.8.1 | A single serialized `AppBackground` lane |
| Build flags | non-transitive R classes | — | `android.nonTransitiveRClass=true` (AGP 8 default) |

Key build files:

- `gradle/libs.versions.toml` — every version lives here (version catalog).
- `gradle.properties` — JVM args, `useAndroidX`, R-class mode, parallelism.
- `app/build.gradle.kts` — the app module, lint config, test instrumentation
  runner, and the GitHub Actions test-annotation block.
- `.github/workflows/ci.yml` — the CI pipeline (see below).

---

## 2. The CI pipeline

`.github/workflows/ci.yml` runs on every PR to `main`, on pushes to `main`,
and on `workflow_dispatch`. It has three jobs:

| Job | What it runs | Catches |
|---|---|---|
| **Static checks** | `tools/check_res.py`, `check_policy.py`, `check_compose.py`, `check_generated.py`, `check_widget.py` | Cross-file invariants that neither `aapt2` nor the unit tests can see (resource cross-refs, data-export policy, Compose usage, generated-file drift, widget layouts) |
| **Build, unit tests, lint** | JDK 17 → `testDebugUnitTest` → `lintDebug` → `assembleDebug` → **APK integrity** → uploads | Compile errors, JVM test failures, lint issues, and a silently broken APK |
| **Emulator verification** | On API **26** and **34** via `reactivecircus/android-emulator-runner` → `tools/ci_emulator_verify.sh` | Install failure, cold-launch crash/ANR, and `connectedDebugAndroidTest` |

The emulator job `needs: build`, so a broken build fails fast without paying
for emulator boots. `tools/ci_emulator_verify.sh` is the `--device` half of
`tools/verify.sh` factored into one entry point so it can also run locally.

### APK integrity (the job that actually matters)

A build can *link* yet ship an APK that crashes on launch. The integrity step
in the build job asserts all of these, as hard failures:

```bash
APK=app/build/outputs/apk/debug/app-debug.apk
AAPT2=<build-tools>/aapt2          # highest build-tools version
APKSIGNER=<build-tools>/apksigner

"$AAPT2" dump badging "$APK" | grep -E '^package:|application-label:'
MANIFEST="$("$AAPT2" dump xmltree --file AndroidManifest.xml "$APK")"
# 1. No unresolved ${applicationId} placeholder in the merged manifest.
echo "$MANIFEST" | grep -q 'applicationId' && exit 1
# 2. The androidx.startup provider must be merged in (from work-runtime AAR).
echo "$MANIFEST" | grep -q 'E: provider' || exit 1
# 3. Debug signature present.
"$APKSIGNER" verify --print-certs "$APK"
```

Why: an earlier Gradle-less pipeline left `${applicationId}` unresolved in
the startup provider authority, which crashed at launch. These assertions
make that class of bug fail in CI instead of on a device.

---

## 3. Procedures & step-by-step guides

### 3.1 First-time environment

Requires JDK 17, an Android SDK (platform 36), and `ANDROID_HOME` set. The
Gradle wrapper downloads the rest.

### 3.2 Build & run the whole verification locally

```bash
./gradlew clean
./tools/verify.sh            # build + JVM tests + lint + APK hash
./tools/verify.sh --device   # + install, cold launch, logcat crash check,
                             #   existing-data relaunch, connectedDebugAndroidTest
```

`--device` needs exactly one connected emulator/device (e.g. `adb devices`).

### 3.3 Run just the JVM unit tests

```bash
./gradlew testDebugUnitTest
# HTML report: app/build/reports/tests/testDebugUnitTest/
# JUnit XML:   app/build/test-results/testDebugUnitTest/
```

### 3.4 Run lint

```bash
./gradlew lintDebug
```

Lint is intentionally bounded for CI (`abortOnError=false`, a curated
`checkOnly` set under `GITHUB_ACTIONS`, `ignoreTestSources=true`,
`checkGeneratedSources=false`, heap raised to `-Xmx4g`) because the full
`lintAnalyzeDebugUnitTest` pass used to hang for 30+ minutes (see
[trap](#t3-lint-hangs-on-lintanalyzedebugunittest)).

### 3.5 Run the instrumented tests on an emulator

```bash
./tools/ci_emulator_verify.sh   # assumes one connected device/emulator
```

This assembles the APK, installs it, runs the cold-launch + existing-data
smoke test, then runs `connectedDebugAndroidTest`.

### 3.6 Update a dependency

1. Edit `gradle/libs.versions.toml` (add the version + the catalog entry).
2. Reference it in `app/build.gradle.kts` as `libs.xxx`.
3. If a new AndroidX/Google library is added, extend the
   `IMPORT_ROOT_TO_LIB` map in `tools/check_res.py` so the resource checker
   knows which import roots are covered by declared dependencies.
4. Run `./tools/verify.sh` and the emulator script.

### 3.7 Ship a release branch / PR

1. Branch off `main`.
2. Make changes, run `./tools/verify.sh` locally.
3. Push; the CI runs the three jobs.
4. PR is mergeable only when **Static checks**, **Build**, **API 26** and
   **API 34** all pass (branch protection on `main`).

---

## 4. Tricks, hacks & traps we hit

Ordered roughly by how much time each cost.

### T1. Instrumented tests: use `ActivityLifecycleCallbacks`, not `ActivityScenario`

**Symptom:** `MainActivityLaunchTest` failed on both API 26 and 34 with
`java.lang.NullPointerException: Cannot run onActivity since Activity has
been destroyed already` and `Activity never becomes requested state
"[DESTROYED]"`.

**Why:** `MainActivity` calls `finish()` synchronously in `onCreate` when
onboarding is pending, so the activity is already destroyed by the time
`ActivityScenario.onActivity` runs. Conversely, after navigating to the
Compose Insights tab the teardown is slower than `ActivityScenario.close()`'s
default wait, so `close()` times out.

**Fix:** drive the activity lifecycle directly with
`Application.ActivityLifecycleCallbacks`, `CountDownLatch`es and generous
timeouts:

```kotlin
app.registerActivityLifecycleCallbacks(hook)
app.startActivity(Intent(app, MainActivity::class.java)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
assertTrue(hook.mainResumed.await(15, TimeUnit.SECONDS))
// assert on the main thread with runOnMainSync { ... }
// cleanup: finish() on the main thread, then await onActivityDestroyed.
app.unregisterActivityLifecycleCallbacks(hook)
```

Rules that make these stable:

- **Never** call `onActivity` on an activity you expect to self-destroy.
- Do **not** `unregisterActivityLifecycleCallbacks` before you await
  `onActivityDestroyed` — the latch will never fire.
- Add `FLAG_ACTIVITY_NEW_TASK` when launching from the application context.
- Run assertions and `finish()` inside `runOnMainSync`.

### T2. R8 rejects spaces in identifier names on minSdk < 28

**Symptom:** `dexBuilderDebugAndroidTest` failed before any test ran:

```
Space characters in SimpleName 'disabled reminders schedule nothing' are not
allowed prior to DEX version 040
```

**Why:** Kotlin backtick method names (`` fun `my test`() ``) put spaces in
the JVM `SimpleName`, which D8 refuses to dex for minSdk < 28 (DEX version
< 040). The androidTest APK is dexed at `minSdk`, so it failed on **both**
API 26 and API 34.

**Fix:** keep instrumented-test method names space-free camelCase:

```kotlin
// No:  fun `disabled reminders schedule nothing`()
// Yes: fun disabledRemindersScheduleNothing()
```

### T3. Lint hangs on `lintAnalyzeDebugUnitTest`

**Symptom:** the build job's Lint step ran 30–45+ minutes and was cancelled
(`Error: The operation was canceled.` at `:app:lintAnalyzeDebugUnitTest`),
even after `timeout-minutes: 30`.

**Why:** the full lint pass over Compose + View sources was exhausting the
default heap and the analyzer never finished.

**Fix (three parts):**

- `gradle.properties`: raise the JVM heap → `-Xmx4g -XX:MaxMetaspaceSize=1g`.
- `app/build.gradle.kts` `lint { }`: `abortOnError=false`,
  `ignoreTestSources=true`, `checkGeneratedSources=false`, and under
  `GITHUB_ACTIONS` a curated `checkOnly += setOf("NewApi","InlinedApi",
  "ObsoleteSdkInt","MissingPermission","MissingSuperCall","Recycle")`.
- Keep `timeout-minutes: 30` on the build job as a backstop.

Result: lint went from a 30+ minute hang to ~1m47s.

### T4. A bash `ERR` trap fires inside `set +e` and masks the real failure

**Symptom:** emulator job reported only
`stage=instrumented tests exit=1` — no test names, no compiler errors.

**Why:** `set -euo pipefail` + a `trap on_err ERR` was set. Inside
`set +e`, the failing element of a pipeline still triggers the `ERR` trap,
so `on_err` aborted **before** the diagnostic-annotation code ran.

**Fix:** clear the trap around the guarded command and reinstall it after:

```bash
set +e
trap - ERR
./gradlew --no-daemon connectedDebugAndroidTest 2>&1 | tee "$AT_LOG"
GRADLE_RC=${PIPESTATUS[0]}
trap on_err ERR
set -e
```

### T5. `adb logcat -c` fails on API 26

**Symptom:** `failed to clear the 'main' log` → the smoke-test script exited.

**Fix:** treat a failed `logcat -c` as non-fatal:

```bash
if ! adb logcat -c >/dev/null 2>&1 && ! adb logcat -b all -c >/dev/null 2>&1; then
  since="-T $(adb shell date '+%m-%d %H:%M:%S.000' | tr -d '\r')"
fi
```

The crash scan still only sees logcat produced from this launch onwards.

### T6. `jsonOf` silently dropped every `List` → tests saw empty collections

**Symptom:** `GrowthTest` round-trip assertions failed: `expected:<2> but
was:<0>`, `expected:<[good sleep, high energy]> but was:<[]>`.

**Why:** `jsonOf` stored Kotlin `List`/`Array`/`Map` values directly, so
`optJSONArray` returned `null` and the collections round-tripped empty.

**Fix:** make `jsonOf` recursively wrap values (`wrapJson`) so
`Collection`/`Array`/`Map` become `JSONArray`/`JSONObject`, while existing
`JSONObject`/`JSONArray` pass through. `tools/util/Json.kt`.

### T7. Float equality must use the delta `assertEquals`

**Symptom:** `FuzzyTest.similarity` → `AssertionError: Use
assertEquals(expected, actual, delta)`.

**Fix:** use the three-argument `assertEquals(expected, actual, delta)` for
`Double`/`Float` in both unit and instrumented tests.

### T8. Material 1.13.0 has no `Widget.Material3.Button.Filled`

**Symptom:** `aapt2` resource linking failed on button style references.

**Fix:** the valid Material3 button styles are `Widget.Material3.Button`
and `Widget.Material3.Button.OutlinedButton` — there is no `*.Filled` or
`*.Outlined` variant in 1.13.0. `tools/check_res.py` now exact-checks
Material style references against `KNOWN_MATERIAL_STYLES`.

### T9. Non-transitive R classes force the correct library namespace

**Symptom:** `Unresolved reference 'colorPrimary'`,
`materialButtonFilledStyle`, `borderlessButtonStyle`.

**Fix:** with non-transitive R, an attribute must be referenced through the
library that defines it:

- `androidx.appcompat.R.attr.colorPrimary` / `borderlessButtonStyle` /
  `colorError` — AppCompat.
- `com.google.android.material.R.attr.*` — Material.
- `material_dynamic_tertiary40` exists only in Material `values-v31` — do not
  use it on API 26–30; use `themeColor(androidx.appcompat.R.attr.colorError)`.

`tools/check_res.py` validates these against `KNOWN_LIBRARY_R_REFS`.

### T10. `androidx.startup` provider must be merged, not declared

The app manifest does **not** declare the
`androidx.startup.InitializationProvider`; the manifest merger contributes it
from the `work-runtime` AAR with the correctly-resolved authority. Declaring
it manually (or leaving `${applicationId}` unresolved) is the original launch
crash. The integrity step asserts a `<provider>` exists.

### T11. WorkManager 2.7.0: no `ExistingPeriodicWorkPolicy.UPDATE`, no `force`

`ExistingPeriodicWorkPolicy.UPDATE` does not exist in WorkManager 2.7.0 (use
`REPLACE`), and `TodayWidget.refresh` has no `force` parameter — callers
(and the widget test) must not pass one.

### T12. Missing SwipeRefreshLayout dependency

**Symptom:** `Unresolved reference 'SwipeRefreshLayout'` at compile.

**Fix:** add `swiperefreshlayout = "1.0.0"` to the version catalog and an
`implementation(libs.androidx.swiperefreshlayout)` line. `tools/check_res.py`
checks import roots against declared dependencies.

### T13. Coroutine calls need the `block` parameter name

**Symptom:** `Argument type mismatch: actual type is
'SuspendFunction1<CoroutineScope, Unit>'` on `scope.launch { ... }`.

**Fix:** pass the lambda positionally is ambiguous once a context is set —
use `scope.launch(block = block)` (same for `AppBackground.await`).

### T14. The GitHub Actions token is short-lived

The Arena/GitHub token periodically expires mid-run (`HTTP 401: Bad
credentials`). After reconnecting GitHub, always re-check the *latest* run
(`gh run list --branch ...`) rather than trusting a stale id, because the
run you were watching may have been superseded by a newer push.

---

## Quick reference: common commands

```bash
./gradlew testDebugUnitTest        # JVM unit tests
./gradlew lintDebug                # lint
./gradlew assembleDebug            # build APK
./gradlew connectedDebugAndroidTest# instrumented tests (device/emulator)
./tools/verify.sh [--device]       # whole pipeline, optional on-device
./tools/ci_emulator_verify.sh      # CI emulator entry point (single device)
python3 tools/check_res.py         # one static checker, or all five via CI
```

Reports land under `app/build/reports/`; JUnit XML under
`app/build/test-results/` and `app/build/outputs/androidTest-results/`.
