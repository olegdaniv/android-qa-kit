# 🛠️ android-qa-kit

[![CI](https://github.com/olegdaniv/android-qa-kit/actions/workflows/ci.yml/badge.svg)](https://github.com/olegdaniv/android-qa-kit/actions)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)](https://android-arsenal.com/api?level=24)

In-app QA & debug toolkit for Android — logger, UI inspector, network monitor, shake to open.

> Inspired by [log_garden](https://github.com/booooohdan/log_garden) Flutter project.

## ✨ Features

| Feature | Module |
|---|---|
| 📝 Logger (Timber wrapper) | `qa-core` |
| 📱 QA Helper Screen (version, device, memory) | `qa-ui-compose` / `qa-ui-view` |
| 🔍 UI Inspector (tap → sizes, colors, fonts) | `qa-ui-compose` / `qa-ui-view` |
| 🌐 Network monitor (OkHttp interceptor) | `qa-core` |
| 💥 Exception Snackbar (auto error display) | `qa-ui-compose` / `qa-ui-view` |
| 📳 Shake to open | `qa-core` |

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
            shakeToOpen = true
            sentryDsn = BuildConfig.SENTRY_DSN
        }
    }
}
```

## 📄 License

```
Copyright 2025 olegdaniv

Licensed under the Apache License, Version 2.0
```
