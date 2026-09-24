# ReviewFlow code review

Follow the repository's AGENTS.md and CONTRIBUTING.md. Prioritize actionable bugs,
regressions, and missing behavioral tests; avoid speculative refactors and style-only comments.

Review changes especially for:

- Single-flight behavior: concurrent calls to `ReviewOrchestrator.tryShow()` or
  `ReviewFlow.tryRequest()` must preserve each API's single-flight guarantee.
  Check mutex ownership and cleanup on cancellation and failure.
- Structured concurrency: preserve cooperative cancellation; do not swallow
  `CancellationException`, introduce `GlobalScope`, or block the main thread.
- Flow semantics: state belongs in StateFlow and events in SharedFlow. Flag unintended
  changes to replay, buffering, event timing, and terminal-state transitions.
- Compose lifecycle: `ReviewEffect` must launch at most once per trigger edge and remain
  safe under recomposition. Resolve Activity through ContextWrapper traversal.
- Compatibility: preserve public source/binary APIs and existing DataStore/UserDefaults
  keys. Check cooldown and once-per-version semantics, including persisted state.
- Module boundaries: review-core must remain Compose-free. Shared APIs must not expose
  Android or Apple framework types. Keep orchestration out of review-compose and samples.
- Tests: orchestration changes need deterministic coverage using fakes for time,
  presenters/clients, versions, and persistence. Cover concurrent calls, cancellation,
  failures, and rule boundaries when affected. Sample tests are integration smoke tests.
- Platform semantics: request completion does not prove that a Play or StoreKit dialog
  appeared. Do not suggest tests that depend on real store dialog visibility.

Explain the concrete failure scenario and point to the affected code. Treat AI review
as additional feedback; passing CI and human review remain necessary.
