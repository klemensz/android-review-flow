# ReviewFlow

[![Maven Central](https://img.shields.io/maven-central/v/com.zleptnig/reviewflow-core?label=reviewflow-core)](https://central.sonatype.com/artifact/com.zleptnig/reviewflow-core)
[![Maven Central](https://img.shields.io/maven-central/v/com.zleptnig/reviewflow-compose?label=reviewflow-compose)](https://central.sonatype.com/artifact/com.zleptnig/reviewflow-compose)

Coroutine-first In-App Review orchestration for Android and iOS, with optional Jetpack Compose integration.

> Submit the platform review request at the right moment — safely, deterministically, and without duplicating eligibility rules.

ReviewFlow adds testable rules, persistence, concurrency safety, and observability on top of Play Core and StoreKit.

---

## Features

- Kotlin Multiplatform rules and state machine
- Android and iOS persistence
- Coroutine-first API
- StateFlow for state and non-replaying SharedFlow events
- Cooldown and usage rules
- Once-per-version support
- Immediate single-flight rejection for concurrent requests
- Android source/binary compatibility with `0.1.x`
- Optional Android Compose integration
- No analytics, tracking, or phone-home behavior

---

## Installation

Add Maven Central:

```kotlin
repositories {
    google()
    mavenCentral()
}
```

Classic Android:

```kotlin
implementation("com.zleptnig:reviewflow-core:0.2.0")
```

Jetpack Compose:

```kotlin
implementation("com.zleptnig:reviewflow-compose:0.2.0")
```

Kotlin Multiplatform:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.zleptnig:reviewflow-core:0.2.0")
        }
    }
}
```

The KMP root publication resolves the matching Android or iOS artifact automatically.
`reviewflow-compose` already depends on `reviewflow-core`, so Android Compose projects normally
only need the Compose dependency.

---

## Requirements

- Android `minSdk 23`
- iOS 16 or newer for the documented `AppStore.requestReview(in:)` bridge
- `review-compose` requires app projects to compile with `compileSdk 36`
- A foreground `Activity` is required when calling the Android compatibility API
  `tryShow(activity)`
- Local source builds use JDK 17 and the provided Gradle wrapper

---

## Android Quick Start

The Android API from `0.1.x` remains unchanged. A classic Android app does not need to enable KMP.

### 1. Create the orchestrator

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

Examples include completing a task, exporting data, saving a trip, or finishing a purchase.

### 3. Try the review request

```kotlin
lifecycleScope.launch {
    val completed = orchestrator.tryShow(activity)
}
```

`true` means the Play Core flow completed. Google Play may still suppress the dialog.
`false` means a rule rejected the request, another request was already in flight, or the platform
request failed.

---

## Multiplatform API

`ReviewFlow` is the platform-neutral entry point:

```kotlin
class ReviewFlow(
    presenter: ReviewPresenter,
    store: ReviewStateStore,
    versionProvider: AppVersionProvider,
    rules: ReviewRules = ReviewRules(),
    clock: Clock = SystemClock,
)
```

It exposes:

```kotlin
val state: StateFlow<ReviewFlowState>
val events: SharedFlow<ReviewFlowEvent>

suspend fun onAppStart()
suspend fun onSuccessMoment()
suspend fun tryRequest(): Boolean
```

`ReviewFlowEvent.RequestCompleted` means the platform request API completed; it never claims that a dialog was visible.

### Android factory

Provide the foreground activity when using the new common API:

```kotlin
val reviewFlow = AndroidReviewFlow.create(
    context = applicationContext,
    activityProvider = currentActivityHolder::resumedActivity,
)
```

`AndroidReviewFlow` retains the provider for the lifetime of the flow. An application-scoped flow
must therefore use a lifecycle-aware holder backed by a `WeakReference`; do not capture an
`Activity` directly with `{ this }`.

If the provider returns `null`, `tryRequest()` returns `false`, emits `RequestUnavailable`, and
consumes neither the cooldown nor the once-per-version request.

### iOS StoreKit bridge

StoreKit's modern request API is Swift-only. Export ReviewFlow from your consuming KMP framework, then install the small Swift bridge:

```swift
import StoreKit
import UIKit
import YourSharedFramework

@available(iOS 16.0, *)
final class StoreKitReviewRequest: @preconcurrency IosReviewRequest {
    @MainActor
    func requestReview() -> Bool {
        guard let scene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .first(where: { $0.activationState == .foregroundActive })
        else {
            return false
        }

        AppStore.requestReview(in: scene)
        return true
    }
}

@available(iOS 16.0, *)
func makeReviewFlow() -> ReviewFlow {
    IosReviewFlow.shared.create(request: StoreKitReviewRequest())
}
```

The complete type-checked bridge is available in [`samples/ios/StoreKitReviewRequest.swift`](samples/ios/StoreKitReviewRequest.swift). The Kotlin adapter invokes it on the main dispatcher. ReviewFlow does not fall back to deprecated `SKStoreReviewController`.

The iOS factory uses `NSUserDefaults` for counters and cooldown state. The once-per-version value comes from `CFBundleShortVersionString`, falling back to `CFBundleVersion`.

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

Button(onClick = { trigger = true }) {
    Text("Rate app")
}

ReviewEffect(
    orchestrator = orchestrator,
    trigger = trigger,
    onConsumed = { trigger = false },
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
val orchestrator = ReviewOrchestrator.create(
    context = context,
    rules = ReviewRules(
        minAppStarts = 5,
        minSuccessMoments = 3,
        cooldown = 14.days,
    ),
)
```

Behavior:

- Rule order is once-per-version, app starts, success moments, then cooldown.
- The cooldown begins before the platform request starts, so rapid retries still count.
- A request exactly at the cooldown boundary is allowed.
- A `null` app version bypasses once-per-version blocking.
- Concurrent requests are rejected immediately with `SkipReason.InFlight`; they are not queued.
- Cancellation is rethrown and resets state to `Idle`.
- Platform failures do not mark a version as completed but still consume the cooldown.
- Unavailable presentation contexts consume neither the cooldown nor the once-per-version request.
- On Android, Play Core may complete as a no-op even though the request itself succeeded.
- `SharedFlow` events have replay `0` and extra buffer capacity `16`.

---

## Android `0.1.x` Compatibility

Existing Android code can upgrade by changing only the version:

```kotlin
implementation("com.zleptnig:reviewflow-core:0.2.0")
```

The following remain available:

- `ReviewOrchestrator.create(context)`
- `onAppStart()` and `onSuccessMoment()`
- `tryShow(activity)`
- custom `ReviewClient` implementations
- `ReviewState.Ready(ReviewInfo)`
- all existing `ReviewState`, `ReviewEvent`, and `SkipReason` types

The legacy `ReviewEvent.Shown` name is retained for compatibility. It means the Play Core flow completed, not that a dialog was shown. New multiplatform integrations should observe `ReviewFlowEvent.RequestCompleted`.

Android continues to use the existing DataStore name and keys, so counters, cooldowns, and once-per-version state survive the upgrade.

---

## Observing State and Events

Use `state` for the current status and `events` for one-off diagnostics:

```kotlin
lifecycleScope.launch {
    orchestrator.state.collect { state ->
        when (state) {
            is ReviewState.Ready -> {}
            is ReviewState.Showing -> {}
            is ReviewState.Done -> {}
            is ReviewState.Error -> {}
            else -> {}
        }
    }
}

lifecycleScope.launch {
    orchestrator.events.collect { event ->
        // Diagnostics or application-owned analytics.
    }
}
```

The multiplatform `ReviewFlow` exposes `StateFlow<ReviewFlowState>` and
`SharedFlow<ReviewFlowEvent>` with the same state-versus-event distinction.

---

## Testing

Common orchestration can be tested with:

- fake `ReviewPresenter`
- in-memory `ReviewStateStore`
- fake `AppVersionProvider`
- fake `Clock`

Android compatibility tests can continue using a fake `ReviewClient`. Do not test dialog appearance; Google Play and StoreKit control whether a dialog is visible.

---

## Modules

| Module                             | Description                                                                |
|------------------------------------|----------------------------------------------------------------------------|
| `review-core` (`:reviewflow-core`) | KMP orchestration, rules, persistence, Android Play Core, and iOS adapters |
| `review-compose`                   | Android Compose helpers around the compatibility orchestrator              |
| `sample-app`                       | Android integration and release-gating smoke tests                         |

---

## Publishing (Maintainers)

Run the full release verification:

```bash
./gradlew verifyMavenCentralRelease
```

Build all platform publications locally:

```bash
./gradlew :reviewflow-core:publishAllPublicationsToProjectLocalRepository \
    :review-compose:publishAllPublicationsToProjectLocalRepository
```

Release both modules:

```bash
./gradlew releaseToMavenCentralPortal
```

Follow the complete procedure in [RELEASING.md](RELEASING.md).

---

## License

Apache 2.0
