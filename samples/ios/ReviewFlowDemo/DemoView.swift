import SwiftUI

struct DemoView: View {
    @ObservedObject var model: DemoModel
    @State private var confirmReset = false

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Label("ReviewFlow", systemImage: "star.bubble.fill")
                        .font(.largeTitle.bold())
                        .foregroundStyle(.indigo)
                    Text("A native iOS playground for review eligibility and StoreKit requests.")
                        .foregroundStyle(.secondary)
                    LabeledContent("State", value: model.state)
                    LabeledContent("App version", value: model.version)
                }

                Section("Presenter") {
                    Picker("Mode", selection: Binding(get: { model.mode }, set: { model.setMode($0) })) {
                        ForEach(DemoModel.Mode.allCases) { mode in
                            Text(mode.rawValue).tag(mode)
                        }
                    }
                    Text("All modes use the same persisted rules. Simulated completion also consumes cooldown and the version allowance.")
                        .font(.footnote).foregroundStyle(.secondary)
                }

                Section {
                    Button(action: model.appStart) { Label("Record app start", systemImage: "plus.circle") }
                    Button(action: model.success) { Label("Record success moment", systemImage: "checkmark.circle") }
                    Button(action: model.request) { Label("Request review", systemImage: "star") }
                } header: {
                    Text("Test actions")
                } footer: {
                    Text("Defaults: 3 app starts, 1 success moment, 30-day cooldown, once per version. One app start is recorded automatically per launch. Completion does not guarantee a visible review dialog.")
                }

                Section {
                    if model.entries.isEmpty {
                        Text("Actions and library events appear here.").foregroundStyle(.secondary)
                    }
                    ForEach(model.entries) { entry in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(entry.message).font(.system(.callout, design: .monospaced))
                                .textSelection(.enabled)
                            Text(entry.time, style: .time).font(.caption).foregroundStyle(.secondary)
                        }
                        .accessibilityElement(children: .combine)
                    }
                    Button("Clear log", action: model.clearLog)
                } header: {
                    Text("Event log · latest first")
                } footer: {
                    Text("The latest 100 entries are held in memory. Eligibility data survives app restarts.")
                }

                Section {
                    Button("Reset demo data", role: .destructive) { confirmReset = true }
                }
            }
            .navigationTitle("Review playground")
            .navigationBarTitleDisplayMode(.inline)
            .confirmationDialog("Reset counters, cooldown and version allowance?", isPresented: $confirmReset, titleVisibility: .visible) {
                Button("Reset demo data", role: .destructive, action: model.reset)
            } message: {
                Text("This clears only this demo app's preferences and starts a fresh session.")
            }
        }
    }
}

#Preview {
    DemoView(model: DemoModel())
}
