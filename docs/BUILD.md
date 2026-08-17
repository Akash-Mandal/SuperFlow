# Building SuperFlow

SuperFlow builds into a signed Android APK **without Gradle and without Maven
access**. This was a hard constraint of the environment the app was first built
in: `dl.google.com`, `maven.google.com`, `repo1.maven.org`, `services.gradle.org`
and `plugins.gradle.org` were all unreachable, so the usual
`Gradle + AndroidX + Compose + Room + Hilt` stack could not be resolved.

The app is therefore written against the **Android framework only** — no
third-party runtime dependencies at all — and is compiled by a small script
that drives the Android build tools directly.

## Consequences of the no-dependency design

| Planned | Shipped instead | Why |
|---|---|---|
| Jetpack Compose + Material 3 | Android Views + a hand-written design system (`ui/Design.kt`) | Compose artifacts come from Google Maven |
| Room | `SQLiteOpenHelper` + a typed repository (`data/Db.kt`, `data/Repo.kt`) | Room is a Maven artifact; the repository boundary is preserved |
| DataStore | `SharedPreferences`, with secrets in a separate excluded file | same |
| Hilt | Explicit singletons with `get(context)` accessors | same |
| WorkManager | `AlarmManager` + `BroadcastReceiver` | same |
| Retrofit / Ktor | `HttpURLConnection` (`ai/MainBrain.kt`) | same |
| Kotlinx Serialization | `org.json` (ships in the framework) + `domain/Serial.kt` | same |

The architectural boundaries from the Grand Plan are kept as **packages**
rather than Gradle modules, which the plan explicitly allows.

## Toolchain

`tools/build_apk.sh` expects these components under `$SUPERFLOW_TOOLCHAIN`
(default `~/toolchain`):

```
toolchain/
├── jdk/                        JDK 25 runtime (PyPI: jdk4py)
├── kotlinc/                    Kotlin 2.4.x compiler (npm: kotlin-compiler)
├── bin/aapt2                   Android Asset Packaging Tool 2 (npm: aaptjs3)
├── lib/dx.jar                  AOSP dexer (LineageOS/android_prebuilts_build-tools)
├── lib/apksigner.jar           APK signer
├── lib/kotlin-stdlib-clean.jar Kotlin stdlib with META-INF/versions stripped
└── platforms/android-34.jar    API 34 android.jar (Sable/android-platforms)
```

Two details matter:

- **`kotlin-stdlib-clean.jar`** — the stock `kotlin-stdlib.jar` contains
  `META-INF/versions/9/module-info.class`, which the AOSP `dx` dexer rejects
  with `unknown tag byte`. The clean jar is the same stdlib with `META-INF`
  removed.
- **`minSdk 26`** — `dx` refuses to translate `invokedynamic` (used throughout
  the Kotlin stdlib) below API 26. This matches the Grand Plan's stated
  `minSdk 26` policy anyway.

## Build

```bash
tools/build_apk.sh release     # -> build/outputs/superflow-release.apk
tools/build_apk.sh debug       # -> build/outputs/superflow-debug.apk
```

Pipeline:

1. `aapt2 compile` — compile resources
2. `aapt2 link` — link resources, emit `R.txt` and the base APK
3. `gen_res.py` — translate `R.txt` into a Kotlin `R` object (kotlinc cannot
   consume aapt2's generated `R.java` here)
4. `kotlinc` — compile all Kotlin sources against `android.jar`
5. `dx` — dex the classes together with the Kotlin stdlib
6. `apksigner` — sign with v1 + v2 + v3 schemes

Signing keys are generated on first run into `build/` and are **git-ignored**.
Override with `SUPERFLOW_KEYSTORE`, `SUPERFLOW_KEYSTORE_PASS` and
`SUPERFLOW_KEY_ALIAS`.

## Tests

```bash
tools/run_tests.sh
```

Three suites, 103 assertions, covering the framework-independent logic:

- **LogicTest** (45) — dates, day-of-week maths, scheduling masks, the habit
  ladder and its fallbacks, contract generation, time validation
- **ParseTest** (25) — day-spec parsing and label round-tripping, JSON
  extraction from fenced/prose-wrapped/nested/escaped model output
- **AiTest** (33) — natural-language habit parsing (12/24-hour times, anchors,
  places, day lists), prompt-injection detection, Blueprint requirement
  extraction, source citation, conflict detection and coverage reporting

Because `android.jar` is a stub whose methods throw, `tools/test/JsonShim.kt`
provides a real `org.json` for the desktop JVM only. It is never compiled into
the APK.

## Output

| | |
|---|---|
| Package | `com.superflow` |
| Version | 1.0.0 (code 1) |
| minSdk / targetSdk | 26 / 34 |
| Size | ~1.1 MB |
| Signatures | v2 + v3 verified |
| Runtime dependencies | none |

## AAB

Producing an Android App Bundle requires `bundletool` and Google's build
tooling, which were not reachable from the build environment. The release APK
contains the complete feature set described in the plans; the AAB packaging
step is the only distribution artifact still outstanding.
