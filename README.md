# ReviewFlow

Coroutine-first In-App Review orchestration for Android  
with optional Jetpack Compose integration.

> Show the Play Store review dialog at the right moment — safely, deterministically, and without callback hell.

---

## Why?

The official Play Core In-App Review API is:

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

## Installation (Coming Soon)

Maven Central coordinates will be published soon.
Until then, use the modules via local source/composite build.

<!--
Add Maven Central and the dependency coordinates:

```kotlin
repositories {
    google()
    mavenCentral()
}
```

Core only:

```kotlin
implementation("com.zleptnig:reviewflow-core:0.1.0")
```

Compose integration:

```kotlin
implementation("com.zleptnig:reviewflow-compose:0.1.0")
```

For local development, you can still include modules manually or via composite build.
-->

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
val reviewOrchestrator = ReviewOrchestrator.create(context)

// Call once per cold start:
lifecycleScope.launch {
    reviewOrchestrator.onAppStart()
}
```

---

### 2. Register success moments

After meaningful user actions:

```kotlin
lifecycleScope.launch {
    reviewOrchestrator.onSuccessMoment()
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
    val launched = reviewOrchestrator.tryShow(activity)
    if (!launched) {
        // Rule checks failed, another call is in-flight, or request failed.
    }
}
```

The dialog may or may not appear — this is controlled by Google Play.
`tryShow(...) == true` means the review flow completed successfully, not that a visible dialog was guaranteed.

---

## Compose Usage

```kotlin
val orchestrator = rememberReviewOrchestrator()

var trigger by remember { mutableStateOf(false) }

Button(onClick = { trigger = true }) {
    Text("Rate app")
}

ReviewEffect(
    orchestrator = orchestrator,
    trigger = trigger,
    onConsumed = { trigger = false }
)
```

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

## Observing state

```kotlin
reviewOrchestrator.state.collect { state ->
    when (state) {
        is ReviewState.Ready -> { }
        is ReviewState.Showing -> { }
        is ReviewState.Done -> { }
        is ReviewState.Error -> { }
    }
}
```

Events:

```kotlin
reviewOrchestrator.events.collect { event ->
    // analytics or debugging
}
```

---

## Testing

Use fakes:
- FakeReviewClient
- FakeClock

Do NOT test actual dialog appearance — Google controls that.

Test state & event behavior instead.

---

## Modules

| Module | Description |
| --- | --- |
| review-core | orchestration logic |
| review-compose | Compose integration |

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

Release instructions are documented in [`RELEASING.md`](RELEASING.md).
For a quick local check:

```bash
./gradlew :review-core:publishReleasePublicationToMavenLocal :review-compose:publishReleasePublicationToMavenLocal

---

## License

Apache 2.0
