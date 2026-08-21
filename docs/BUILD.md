# Building SuperFlow

SuperFlow is a **full AndroidX / Material 3 application** built with the
standard Android Gradle Plugin. This is the only supported build path.

> **History:** an earlier revision of this repository shipped a Gradle-less
> pipeline (`tools/build_apk.sh` + `tools/gen_res.py` + a vendored library
> set) because the build host could not reach the package repositories.
> That pipeline produced the original launch crash: it left
> `${applicationId}` unresolved in the AndroidX Startup provider authority,
> generated only partial library `R` classes (which masked wrong-namespace
> source references such as `com.google.android.material.R.attr
> .borderlessButtonStyle`, an AppCompat attribute), and merged AAR
> manifests inconsistently. The pipeline has been **removed**; Gradle now
> performs dependency resolution, resource linking, `R` generation,
> manifest merging (including the `androidx.startup` provider contributed
> by the `work-runtime` AAR), Kotlin compilation, D8 dexing and debug
> signing.

## Requirements

| Component | Version |
|---|---|
| JDK | 17 (AGP 8.9 requirement) |
| Gradle | 8.11.1 (wrapper committed) |
| Android Gradle Plugin | 8.9.1 |
| Kotlin | 2.2.0 |
| Android SDK | platform 36 (compileSdk), targetSdk 34, minSdk 26 |

## Build

```bash
./gradlew clean
./gradlew testDebugUnitTest     # JVM unit tests (domain + logic)
./gradlew lintDebug
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Full verification (build + tests + APK integrity + optional on-device
smoke test and instrumented tests):

```bash
./tools/verify.sh            # build, test, lint, APK hash
./tools/verify.sh --device   # + install, force-stop, cold launch, logcat
                             #   crash check, existing-data relaunch,
                             #   connectedDebugAndroidTest
```

## Continuous integration

`.github/workflows/ci.yml` runs on every pull request to `main`, on pushes to
`main`, and on demand (`workflow_dispatch`). It mirrors `tools/verify.sh` in
three jobs:

| Job | What it runs |
|---|---|
| **Static checks** | `tools/check_res.py`, `check_policy.py`, `check_compose.py`, `check_generated.py`, `check_widget.py` — the cross-file invariants neither `aapt2` nor the unit tests can see |
| **Build, unit tests, lint** | JDK 17 + `testDebugUnitTest`, `lintDebug`, `assembleDebug`, then APK integrity: no unresolved `${applicationId}`, the `androidx.startup` provider is present in the merged manifest, and the debug signature verifies. Uploads the APK and all reports |
| **Emulator verification** | On API **26** (minSdk) and **34** (targetSdk), via `reactivecircus/android-emulator-runner`: runs `tools/ci_emulator_verify.sh` — install, cold launch, logcat crash/ANR check, existing-data relaunch, then `connectedDebugAndroidTest` |

The emulator job depends on the build job, so a broken build fails fast
without paying for emulator boots. `tools/ci_emulator_verify.sh` is the
`--device` half of `verify.sh` factored into a single entry point, so it can
be run locally against a connected device with no arguments.

> The APK integrity assertions are deliberately hard failures: an APK that
> links but leaves `${applicationId}` unresolved in the startup provider
> authority is exactly the artifact that produced the original launch crash.

> **Status:** the workflow is currently staged at `ci/ci.yml` and is **not
> active** — GitHub only runs workflows under `.github/workflows/`. See
> [`ci/README.md`](../ci/README.md) for the one-command move that activates it.

## Dependencies

Declared in `gradle/libs.versions.toml`. The runtime set matches the exact
library versions the source was developed and field-tested against:

| Area | Libraries |
|---|---|
| UI | Material **1.13.0**, AppCompat 1.7.1, RecyclerView 1.4.0, ViewPager2 1.1.0, Activity 1.11.0 |
| Compose | Compose **1.7.6** (runtime/ui/foundation/animation), Material3 **1.3.1**, Activity-Compose **1.9.3** — enabled via the Kotlin 2.2.0 Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`) and `buildFeatures.compose = true` |
| Architecture | Fragment 1.6.1, Lifecycle/ViewModel 2.6.2 (+ `-ktx`), Core-ktx 1.17.0 |
| Async | Kotlin Coroutines 1.8.1 |
| Data | `androidx.sqlite` 2.1.0 + framework |
| Background | WorkManager 2.7.0 (+ `-ktx` for `CoroutineWorker`) — initialized by the `androidx.startup` provider merged from the AAR; never initialize it manually |

## Manifest notes

* The app manifest does **not** declare a `package` attribute (AGP 8 takes
  the namespace from `app/build.gradle.kts`) and does **not** declare the
  `androidx.startup.InitializationProvider` — the manifest merger
  contributes it with the correctly resolved authority.
* `BootReceiver` stays exported with a `BOOT_COMPLETED` /
  `MY_PACKAGE_REPLACED` filter (required for the reschedule-on-reboot path).
* `ReminderReceiver` and the widget receiver are not exported.

## Resource namespaces

With non-transitive R classes (the AGP 8 default, also made explicit in
`gradle.properties`), library attributes must be referenced through the
library that defines them:

* `androidx.appcompat.R.attr.borderlessButtonStyle` — AppCompat attribute
  (used for text-style `MaterialButton` constructors).
* `com.google.android.material.R.attr.*` — Material color and button-style
  attributes (`colorPrimary`, `colorSurface`, `colorSurfaceVariant`,
  `colorOnSurface`, `colorOnSurfaceVariant`, `colorOnPrimary`,
  `colorOutline`, `colorPrimaryContainer`, `colorSecondaryContainer`,
  `materialButtonOutlinedStyle`).
* `com.superflow.R.*` — application resources only.

## Testing

* `app/src/test` — JVM unit tests: recurrence rules, opportunity model
  (adherence, runs, recoveries, never-miss-twice), time/DST handling,
  domain models, JSON extraction, the Local Coordinator router, quiet-hours
  predicate and row serialization.
* `app/src/androidTest` — instrumented tests: schema creation and v1→v3
  migration with data safety, WAL mode, clean-install / onboarding /
  onboarded launch paths, reminder scheduling (enabled/disabled, quiet
  hours, budget, idempotence), widget refresh with zero installed widgets,
  check-in/undo round trips and tab navigation.
