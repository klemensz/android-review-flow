# Contributing

Thanks for your interest in contributing to ReviewFlow!

This project aims to provide a stable, coroutine-first Kotlin Multiplatform In-App Review library.
Please read this document before opening a pull request.

---

## Development setup

Toolchain requirements:

- JDK 17
- Gradle Wrapper `9.0.0` (`./gradlew`)
- Android Gradle Plugin `8.13.2`
- Kotlin `2.3.10`
- Android SDK Platform 36 installed
- Xcode with an iOS Simulator SDK for Apple-target tests

Build the project:

./gradlew build

Run tests:

./gradlew :reviewflow-core:allTests :review-compose:test :sample-app:test

On Apple Silicon, the `iosX64Test` task is expected to be skipped. Run that target on an Intel
macOS CI worker when x64 simulator execution is release-gating.

Lint / verification:

./gradlew check

---

## Before opening a PR

Please make sure:

- The project builds successfully
- Tests pass
- Public API is not broken
- New behavior is documented
- You followed rules in AGENTS.md

---

## Code guidelines

### API stability
This is a library. Avoid breaking changes.

Prefer:
- adding new APIs
- deprecating old APIs

Avoid:
- renaming public classes
- changing function signatures

---

### Coroutines & Flow
The library is concurrency-sensitive.

Do NOT introduce:
- GlobalScope
- runBlocking in production code
- blocking IO on main thread

Preserve:
- StateFlow for state
- SharedFlow for events
- single-flight guarantees

---

### Compose module
`review-compose` must stay a thin integration layer.
Business logic belongs in the `review-core` directory, exposed to Gradle as `:reviewflow-core`.

### Multiplatform core

- Platform-neutral rules and state machines belong in `commonMain`.
- Android framework and Play Core types belong in `androidMain`.
- Apple framework and UserDefaults types belong in `iosMain`.
- Do not expose platform framework types from shared API models.

---

## In-App Review constraints

- Dialog visibility is controlled by Google Play or StoreKit and is non-deterministic.
- No-op outcomes are valid (for example `ReviewInfo` with `isNoOp=true`).
- Treat Android `ReviewEvent.Shown` and common `ReviewFlowEvent.RequestCompleted` as "request completed", not "dialog was visible".
- Tests must validate state/events/rules, not UI visibility of the Play dialog.

---

## Tests
Logic changes must include tests.

Use fakes appropriate to the affected layer:

- Common orchestration: fake `ReviewPresenter`, in-memory `ReviewStateStore`, fake
  `AppVersionProvider`, and fake `Clock`.
- Android compatibility: fake `ReviewClient`.

Do not rely on real Play Store or StoreKit behavior.

---

## Pull request style

Good PRs are:
- small
- focused
- documented

Bad PRs:
- formatting-only changes
- dependency updates without reason
- large refactors

---

## Questions?
Open an issue before implementing large changes.
