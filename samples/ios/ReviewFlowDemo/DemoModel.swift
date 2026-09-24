import Foundation
import ReviewFlowDemoKit
import StoreKit
import UIKit

@MainActor
final class DemoModel: ObservableObject {
    enum Mode: String, CaseIterable, Identifiable {
        case completed = "Simulated completion"
        case unavailable = "Simulated unavailable"
        case storeKit = "StoreKit"
        var id: String { rawValue }
    }

    struct Entry: Identifiable {
        let id = UUID()
        let time = Date()
        let message: String
    }

    @Published private(set) var mode: Mode = .completed
    @Published private(set) var state = "Idle"
    @Published private(set) var entries: [Entry] = []
    private var session: DemoSession?
    private var recordedLaunch = false

    var version: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown"
    }

    func start() {
        guard session == nil else { return }
        session = DemoSession(
            request: DemoReviewRequest(mode: mode),
            onState: { [weak self] value in
                MainActor.assumeIsolated { self?.state = value }
            },
            onLog: { [weak self] value in
                MainActor.assumeIsolated { self?.log(value) }
            }
        )
        if !recordedLaunch {
            recordedLaunch = true
            session?.recordAppStart()
        }
    }

    func stop() {
        session?.close()
        session = nil
    }

    func setMode(_ newMode: Mode) {
        guard newMode != mode else { return }
        let wasRunning = session != nil
        stop()
        mode = newMode
        state = "Idle"
        log("Mode: \(mode.rawValue). State reset to Idle; pending actions cancelled. Persisted eligibility unchanged.")
        // Selecting a mode while stopped must not start collectors or record an app start.
        if wasRunning { start() }
    }

    func appStart() { session?.recordAppStart() }
    func success() { session?.recordSuccess() }
    func request() { session?.requestReview() }
    func clearLog() { entries.removeAll() }

    func reset() {
        stop()
        // Clear only this standalone demo's preference domain, never library key names.
        if let identifier = Bundle.main.bundleIdentifier {
            UserDefaults.standard.removePersistentDomain(forName: identifier)
        }
        recordedLaunch = false
        entries.removeAll()
        log("Demo data reset. Recording the first app start.")
        start()
    }

    private func log(_ message: String) {
        entries.insert(Entry(message: message), at: 0)
        if entries.count > 100 { entries.removeLast(entries.count - 100) }
    }
}

/// ReviewFlow dispatches this synchronous reverse-interop callback to the main dispatcher.
@MainActor
private final class DemoReviewRequest: @preconcurrency IosReviewRequest {
    private let mode: DemoModel.Mode
    init(mode: DemoModel.Mode) { self.mode = mode }

    func requestReview() -> Bool {
        switch mode {
        case .completed:
            return true
        case .unavailable:
            return false
        case .storeKit:
            guard let scene = UIApplication.shared.connectedScenes
                .compactMap({ $0 as? UIWindowScene })
                .first(where: { $0.activationState == .foregroundActive }) else {
                return false
            }
            AppStore.requestReview(in: scene)
            return true
        }
    }
}
