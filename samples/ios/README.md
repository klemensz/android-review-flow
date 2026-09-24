# ReviewFlow SwiftUI demo

A native iOS 16+ app that exercises the real ReviewFlow iOS factory, UserDefaults persistence,
Flow states/events, and a Swift StoreKit presenter. The Android sample remains independent.

## Run in Xcode

1. Install Xcode with an iOS Simulator runtime and JDK 17 or 21.
2. Open `ReviewFlowDemo.xcodeproj` and select the shared `ReviewFlowDemo` scheme.
3. Select an iPhone simulator and Run. For a physical iPhone, select your development team in
   Signing & Capabilities first; change the demo bundle identifier if necessary.

The build respects an existing `JAVA_HOME`; otherwise it asks macOS for a JDK version 17 or
newer (`java_home -F -v '17+'`) and fails if lookup fails. This is not an exact JDK-17 pin.

The first build needs network access for Gradle dependencies. The Xcode build phase builds the
matching Kotlin framework automatically. No XcodeGen or CocoaPods installation is required.
The default dependency is the local `reviewflow-core` project, not a Maven Central release.
The small `:ios-demo-bridge` module exports Core and owns cancellable Flow collectors; it is
sample infrastructure, has no publishing plugin, and does not change the library's public API.

Command-line simulator build (no device signing required):

```sh
xcodebuild -project samples/ios/ReviewFlowDemo.xcodeproj \
  -scheme ReviewFlowDemo -configuration Debug \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath samples/ios/build CODE_SIGNING_ALLOWED=NO build
```

## Suggested manual checks

The app records one app start automatically per process launch, not per foreground transition.
It uses the production defaults: three starts, one success moment, 30 days cooldown, once per
app version. State and actual library events are displayed; the latest 100 log entries are
held in memory. Counters and eligibility persist across relaunches.

1. Reset demo data. Request immediately: expect `Skipped` with an eligibility reason.
2. Record two more starts and one success. In **Simulated unavailable**, request: expect
   `RequestUnavailable` and state `Unavailable`. Repeating should still reach the presenter,
   because unavailability does not consume cooldown or the version allowance.
3. Switch to **Simulated completion** and request: expect `RequestCompleted` and `Done`.
   Request again: expect once-per-version rejection. Relaunch and verify it remains rejected.
4. Reset, satisfy the rules again, select **StoreKit**, and request on an iPhone. Completion
   means StoreKit was called with an active scene, not that a dialog was visible.
5. Rotate, use large Dynamic Type, scroll through the event log, and background/foreground
   the app. The UI must remain readable and foregrounding must not add app starts.
6. To check cooldown independently of once-per-version, complete a request, increase the
   Xcode target's Marketing Version, and install over the existing app without deleting data.
   The request should now be rejected by cooldown instead of the version rule.

Changing presenter mode cancels the old session and pending actions, resets the displayed state
to `Idle`, and logs the transition. The session stays alive during ordinary background/foreground
transitions; root-view `onDisappear` is only a view-removal cleanup hook, not a background hook.
Mode changes and reset explicitly close the old session as well.

All presenter modes intentionally share the same persisted eligibility. A simulated completion
therefore affects later StoreKit requests until reset. Reset clears only this demo app's
UserDefaults domain, then records its first start. It does not depend on internal library keys.
The demo has no custom cooldown controls and no analytics or network layer.

`StoreKitReviewRequest.swift` next to this README remains a standalone integration example for
consumers exporting a framework named `ReviewFlowCore`. The demo imports `ReviewFlowDemoKit`
and includes the equivalent presenter in `ReviewFlowDemo/DemoModel.swift`.
