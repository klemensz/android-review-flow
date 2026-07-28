# ReviewFlow

[![Maven Central](https://img.shields.io/maven-central/v/com.zleptnig/reviewflow-core?label=reviewflow-core)](https://central.sonatype.com/artifact/com.zleptnig/reviewflow-core)
[![Maven Central](https://img.shields.io/maven-central/v/com.zleptnig/reviewflow-compose?label=reviewflow-compose)](https://central.sonatype.com/artifact/com.zleptnig/reviewflow-compose)

Coroutine-first In-App Review orchestration for Android with optional Jetpack Compose integration.

> Show the Play Store review dialog at the right moment — safely, deterministically, and without callback hell.

A thin orchestration layer on top of Play Core for rules, concurrency safety, and observability.

---

## Why?

The official [Play Core In-App Review API](https://developer.android.com/guide/playcore/in-app-review) is:

- callback / Task based
- hard to test
- easy to misuse
- non-deterministic
- missing timing & cooldown logic

**ReviewFlow** provides a structured, Flow-based orchestration layer that:

- prevents spamming the review dialog
- handles cooldown & app-usage rules
- guarantees single-flight execution
- integrates cleanly with coroutines
- works with and without Compose

---

## Features

- Coroutine-first API
- StateFlow + SharedFlow events
- Cooldown & usage rules
- Once-per-version support
- Idempotent triggering
- Testable (fake client + fake clock)
- Compose integration module
- No analytics / no tracking
- No Play Store hacks

---

## Installation

Add Maven Central:

```kotlin
repositories {
    google()
    mavenCentral()
}
```

Core:

```kotlin
implementation("com.zleptnig:reviewflow-core:0.1.0")
```

Compose integration:

```kotlin
implementation("com.zleptnig:reviewflow-compose:0.1.0")
```

`reviewflow-compose` depends on `reviewflow-core`, so you only need to add one of them based on your use case.

---

## Requirements

- Android `minSdk 23` (library requirement)
- `review-compose` currently targets a modern Compose stack and requires app projects to build with `compileSdk 36`
- A foreground `Activity` is required when calling `tryShow(activity)`
- For local source builds, use JDK 17 and the provided Gradle wrapper

---

## Quick Start

### 1. Create orchestrator

```kotlin
val orchestrator = ReviewOrchestrator.create(context)

// Call once per cold start:
lifecycleScope.launch {
    orchestrator.onAppStart()
}
```

---

### 2. Register success moments

After meaningful user actions:

```kotlin
lifecycleScope.launch {
    orchestrator.onSuccessMoment()
}
```

Examples:

- user completed a task
- user exported data
- trip saved
- purchase finished

---

### 3. Try showing review

```kotlin
lifecycleScope.launch {
    val launched = orchestrator.tryShow(activity)
    if (!launched) {
        // Rule checks failed, another call is in-flight, or request failed.
    }
}
```

The dialog may or may not appear — this is controlled by Google Play.
`tryShow(...) == true` means the review flow completed successfully, not that a visible dialog was guaranteed.

---

## Compose Usage

### Simple case (direct button action)

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val orchestrator = rememberReviewOrchestrator()
            val scope = rememberCoroutineScope()

            Button(
                onClick = {
                    scope.launch {
                        orchestrator.onSuccessMoment()
                        orchestrator.tryShow(this@MainActivity)
                    }
                },
            ) {
                Text("Rate app")
            }
        }
    }
}
```

### Advanced case (event-/ViewModel-driven trigger)

```kotlin
val orchestrator = rememberReviewOrchestrator()
var trigger by remember { mutableStateOf(false) }

// Example: set this from a one-shot ViewModel event.
Button(onClick = { trigger = true }) { Text("Rate app") }

ReviewEffect(
    orchestrator = orchestrator,
    trigger = trigger,
    onConsumed = { trigger = false }
)
```

`ReviewEffect` targets a different use case.

When to use which:

- Direct button action: user explicitly taps "Rate app" and you want the shortest flow.
- `ReviewEffect`: trigger comes from state/events (for example, a one-shot ViewModel event).
- The `sample-app` demonstrates both patterns side by side (`simple` and `effect` buttons).

---

## Runtime Behavior (Important)

- Google Play decides whether a review dialog is shown.
- A request can complete as a no-op (`ReviewInfo ... isNoOp=true`) even though the flow itself succeeds.
- `ReviewEvent.Shown` means `launchReviewFlow(...)` completed successfully, not necessarily that a dialog was visible.
- Prefer observing `state` and `events` for diagnostics instead of asserting visible UI behavior.

---

## Rules

Default rules:

- minimum app starts: 3
- minimum success moments: 1
- cooldown: 30 days
- once per app version

Custom:

```kotlin
ReviewOrchestrator.create(
    context,
    rules = ReviewRules(
        minAppStarts = 5,
        minSuccessMoments = 3,
        cooldown = 14.days
    )
)
```

---

## Observing State and Events

Use `state` for current status and `events` for one-off diagnostics.

```kotlin
orchestrator.state.collect { state ->
    when (state) {
        is ReviewState.Ready -> {}
        is ReviewState.Showing -> {}
        is ReviewState.Done -> {}
        is ReviewState.Error -> {}
    }
}
```

Events:

```kotlin
orchestrator.events.collect { event ->
    // analytics or debugging
}
```

---

## Testing

Use fakes:

- fake `ReviewClient`
- fake `Clock`

Do NOT test actual dialog appearance — Google controls that.

Test state & event behavior instead.

---

## Modules

| Module         | Description                                                               |
|----------------|---------------------------------------------------------------------------|
| review-core    | orchestration logic, rules, persistence, and Play Core integration        |
| review-compose | Compose helpers such as `rememberReviewOrchestrator()` and `ReviewEffect` |

---

## Philosophy

This library intentionally:

- does not guarantee dialog appearance
- does not bypass Play policies
- does not track users
- does not include analytics SDKs

It only guarantees correct orchestration.

---

## Publishing (Maintainers)

Quick local check:

```bash
./gradlew :review-core:publishAllPublicationsToProjectLocalRepository :review-compose:publishAllPublicationsToProjectLocalRepository
```

Release both modules to Maven Central Portal:

```bash
./gradlew releaseToMavenCentralPortal
```

Maintainers must follow the complete preparation, validation, and tagging procedure in [RELEASING.md](RELEASING.md).

---

## License

Apache 2.0
