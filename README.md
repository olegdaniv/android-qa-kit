# 🛠️ android-qa-kit

[![CI](https://github.com/olegdaniv/android-qa-kit/actions/workflows/ci.yml/badge.svg)](https://github.com/olegdaniv/android-qa-kit/actions)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)](https://android-arsenal.com/api?level=24)

In-app QA & debug toolkit for Android — logger, UI inspector, network monitor, shake to open.

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

## 🚀 Integration

```kotlin
// build.gradle.kts

dependencies {
    // Compose app
    debugImplementation("io.github.olegdaniv:qa-ui-compose:0.1.0")

    // View app
    debugImplementation("io.github.olegdaniv:qa-ui-view:0.1.0")

    // Release — zero overhead stubs
    releaseImplementation("io.github.olegdaniv:qa-no-op:0.1.0")
}
```

## 📦 Modules

```
android-qa-kit/
├── qa-core/          # Pure Kotlin logic — no UI deps
├── qa-ui-compose/    # Compose QA screens & inspector
├── qa-ui-view/       # View-based QA screens & inspector
├── qa-no-op/         # Release stubs (zero overhead)
└── sample/           # Demo app
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

## 📄 License

```
Copyright 2025 olegdaniv

Licensed under the Apache License, Version 2.0
```
