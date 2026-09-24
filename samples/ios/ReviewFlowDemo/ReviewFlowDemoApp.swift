import SwiftUI

@main
struct ReviewFlowDemoApp: App {
    @StateObject private var model = DemoModel()

    var body: some Scene {
        WindowGroup {
            DemoView(model: model)
                .onAppear { model.start() }
                // Root-view disappearance is not an iOS background notification.
                // Keep the session across foreground transitions. stop() also runs on
                // mode changes and reset; process termination needs no teardown callback.
                .onDisappear { model.stop() }
        }
    }
}
