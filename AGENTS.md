# AGENTS.md — Project Rules for AI-Assisted Contributions

This repository contains an Android In-App Review orchestration library with a coroutine/Flow-first API.
Changes must preserve structured concurrency, Flow semantics, and public API stability.

## Project structure

- `review-core`:
    - Android-only but UI-toolkit-agnostic (no Compose dependency).
    - Contains orchestration logic, persistence, and Play Core integration.
- `review-compose`:
    - Jetpack Compose convenience layer only.
    - Must not contain business logic that belongs in `review-core`.
- `sample-app`:
    - Demonstrates library integration and diagnostics.
    - Must not contain behavior required by the library modules.
    - Its unit tests are release-gating integration smoke tests.

## Hard rules (must follow)

1. **No `GlobalScope`** or unstructured concurrency.
2. **No blocking calls** on main thread (`runBlocking`, `Thread.sleep`, blocking I/O).
3. **Public API stability**:
    - Do not break source/binary compatibility without a major version bump.
    - Avoid renaming or removing public types/functions.
    - Prefer adding new APIs over changing existing ones.
4. **Flow semantics must remain correct**:
    - `StateFlow` is for state, `SharedFlow` for events.
    - No “event as state” anti-pattern.
    - Avoid changing replay/buffer behavior unless explicitly intended and documented.
5. **Single-flight guarantees**:
    - `tryShow()` must remain idempotent under concurrent calls.
    - Avoid race conditions (keep mutex/atomic protection).
6. **Module boundaries**:
    - `review-core` must not depend on Compose.
    - `review-compose` can depend on `review-core`, not the other way around.

## Coding standards

- Kotlin: prefer small, composable functions; keep side effects explicit.
- Favor `sealed interface` models for states/events.
- Prefer `Result`/domain errors or explicit sealed reasons over throwing for control flow.
- Keep Android API usage behind interfaces when reasonable (testability).

## Concurrency & Coroutines guidelines

- Use structured concurrency (`coroutineScope`, `supervisorScope`) where needed.
- Cancellation must be cooperative.
- Prefer `Mutex` for single-flight over ad-hoc boolean flags.
- Use `Dispatchers.IO` only for true blocking I/O (DataStore is already non-blocking).

## Persistence rules

- DataStore keys are internal implementation details:
    - Do not rename keys casually (migration would be required).
    - If you must change keys, add migration or keep backward compatibility.

## Compose integration rules

- `ReviewEffect` must be recomposition-safe:
    - Launch at most once per trigger edge.
    - No infinite loops; no repeated launching due to state updates.
- Never cast `LocalContext.current` directly to `Activity` without safe lookup.
    - Use `ContextWrapper` traversal.

## Testing requirements

- Any change in orchestration logic must include tests.
- Prefer deterministic tests:
    - Use fake `Clock`.
    - Use fake `ReviewClient`.
    - Avoid relying on real Play Core behavior.
- Focus tests on state/events and rule evaluation; the Play dialog is server-controlled and non-deterministic.

## Documentation requirements

- Public API changes require README updates and minimal KDoc.
- Document behavioral guarantees:
    - when events emit
    - cooldown semantics
    - once-per-version semantics

## Allowed improvements (good PR topics)

- Add preloading with TTL (cache ReviewInfo safely)
- Add retry/backoff policy (opt-in)
- Add richer skip reasons and diagnostics
- Expand deterministic unit test coverage
- Add a CI workflow

## Disallowed changes

- Adding analytics SDK dependencies
- Telemetry/phone-home behavior
- Runtime behavior that requires Play Store credentials or network calls beyond Play Core
