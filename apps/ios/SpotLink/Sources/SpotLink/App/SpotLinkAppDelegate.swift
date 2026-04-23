#if canImport(UIKit)
import UIKit

public final class SpotLinkAppDelegate: NSObject, UIApplicationDelegate {
    public override init() {
        super.init()
    }

    public func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        Task {
            await PushNotificationManager.shared.didRegisterDeviceToken(deviceToken)
        }
    }

    public func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        PushNotificationManager.shared.didFailToRegisterForRemoteNotifications(error)
    }
}
#endif