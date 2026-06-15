# 🛠️ android-qa-kit

[![CI](https://github.com/olegdaniv/android-qa-kit/actions/workflows/ci.yml/badge.svg)](https://github.com/olegdaniv/android-qa-kit/actions)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)](https://android-arsenal.com/api?level=24)

In-app QA & debug toolkit for Android — logger, lifecycle, network monitor, crash/ANR & performance metrics, shake to open.

> Inspired by [log_garden](https://github.com/booooohdan/log_garden) Flutter project.

## ✨ Features

| Feature | Module | Status |
|---|---|---|
| 📝 Logger (Timber wrapper) + Logs viewer | `qa-core` / `qa-ui-compose` | ✅ |
| ♻️ Lifecycle logging (Activity + Fragment) | `qa-core` | ✅ |
| 🌐 Network monitor (OkHttp interceptor, Chucker) | `qa-core` | ✅ |
| 📱 Device info / app version / memory | `qa-core` / `qa-ui-compose` | ✅ |
| 💥 Error collection — handled + crashes (persisted across restarts) | `qa-core` | ✅ |
| 🐢 ANR detection (live watchdog + `ApplicationExitInfo`) | `qa-core` | ✅ |
| 📊 Performance metrics (jank, memory, startup) with Android vitals ratings | `qa-core` / `qa-ui-compose` | ✅ |
| 📳 Shake to open | `qa-core` | ✅ |
| 🔍 UI Inspector (tap → sizes, colors, fonts) | `qa-ui-compose` / `qa-ui-view` | 🚧 planned |

## 📦 Modules

```
android-qa-kit/
├── qa-core/          # Always-on logic — logger, errors, ANR, perf, device, network (no Compose)
├── qa-ui-compose/    # QA panel UI (Compose) — debug only
├── qa-ui-view/       # QA panel UI (View/XML, no Compose) — debug only
├── qa-no-op/         # Release stubs — 🚧 planned (stub)
└── sample/           # Demo app (Compose)
```

- **`qa-core`** has no UI and pulls in no Compose — safe in any app. It collects everything; you read it through a panel.
- **`qa-ui-compose`** and **`qa-ui-view`** each provide the QA panel as a **self-contained `Activity`** and register the panel opener (debug builds only). Pick the one matching your UI toolkit — **do not include both** (they would both register an opener).

## 🚀 Integration

> ⚠️ Not published to Maven yet. Consume the modules locally for now (see below); the
> `io.github.olegdaniv:*` coordinates will be added once the first release is published.

Include the modules in your build (e.g. as a Git submodule or [composite build](https://docs.gradle.org/current/userguide/composite_builds.html)),
then add them in `settings.gradle.kts`:

```kotlin
include(":qa-core", ":qa-ui-compose", ":qa-ui-view")
```

## 🔧 Quick Start

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        QaKit.init(this) {
            shakeToOpen           = true   // open QA panel on shake
            lifecycleLogging      = true   // log Activity + Fragment lifecycle
            persistErrors         = true   // crashes survive process restart
            anrDetection          = true   // live ANR watchdog
            collectExitInfo       = true   // real ANR / native crash (API 30+)
            performanceMonitoring = true   // jank / memory / startup metrics
            networkConfig         = NetworkInterceptorConfig(
                headersToRedact = setOf("Authorization", "X-Api-Key"),
            )
        }
    }
}
```

Attach the network interceptor to your OkHttp client:

```kotlin
val client = OkHttpClient.Builder()
    .apply { QaKit.networkInterceptor?.let { addInterceptor(it) } }
    .build()
```

Open the panel from code (no-op in release):

```kotlin
QaKit.openPanel(context)
```

## 🧩 Usage by project type

### Pure Compose app

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(project(":qa-core"))            // always available (logger, errors, perf, …)
    debugImplementation(project(":qa-ui-compose"))  // QA panel UI — debug builds only
}
```

1. Call `QaKit.init(this) { … }` in your `Application.onCreate()` (see [Quick Start](#-quick-start)).
2. Open the panel by **shaking the device** (when `shakeToOpen = true`) or from a button:

```kotlin
Button(onClick = { QaKit.openPanel(context) }) { Text("QA Panel") }
```

`QaKit.isPanelAvailable` is `false` in release builds (no UI module) — `openPanel` is a safe no-op there.

### Legacy / View (XML) app

Use `qa-ui-view` — a pure View/XML panel (no Compose dependency at all). It is a standalone
`Activity`; **you do not write any Compose**:

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(project(":qa-core"))
    debugImplementation(project(":qa-ui-view"))  // self-contained View panel Activity
}
```

```kotlin
// Application.onCreate()
QaKit.init(this) { shakeToOpen = true /* … */ }

// Open from a menu item / button
findViewById<Button>(R.id.qa_button).setOnClickListener {
    QaKit.openPanel(this)
}
```

The panel hosts the same tabs as the Compose one (Logs / Errors / Device / Network / Perf) and
updates live. It is built with `AppCompatActivity` + Material `TabLayout` (transitively from
`com.google.android.material`).

### Core only (no panel)

If you just want **collection** without the built-in viewer, depend on `qa-core` alone. Logs flow
to Logcat (via Timber) and HTTP traffic is browsable in Chucker's own notification/Activity; errors,
ANR and perf data are available programmatically (`GlobalErrorHandler.entries`,
`PerformanceMonitor.snapshot()`).

### Logging & manual error capture

```kotlin
QaLogger.d("Auth", "user signed in")
QaLogger.e("Sync", "sync failed", throwable)

try {
    riskyCall()
} catch (e: Exception) {
    GlobalErrorHandler.record(e)   // shows up in the Errors tab
}
```

## 📄 License

```
Copyright 2025 olegdaniv

Licensed under the Apache License, Version 2.0
```
