import ReviewFlowCore
import StoreKit
import UIKit

/// Installs StoreKit's modern Swift API behind ReviewFlow's small reverse-interop contract.
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
