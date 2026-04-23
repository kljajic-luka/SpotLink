import Foundation
import UserNotifications
#if canImport(UIKit)
import UIKit
#endif

// MARK: - Push Notification Manager

/// Apstrakcija za APNs permisije i device token registraciju.
@MainActor
public final class PushNotificationManager: NSObject, ObservableObject {

    public static let shared = PushNotificationManager()

    @Published public private(set) var permissionStatus: UNAuthorizationStatus = .notDetermined
    @Published public private(set) var deviceToken: String?

    private let notificationService: NotificationService?

    public init(notificationService: NotificationService? = nil) {
        self.notificationService = notificationService
        super.init()
    }

    // MARK: - Permission

    public func requestPermission() async {
        let center = UNUserNotificationCenter.current()
        do {
            let granted = try await center.requestAuthorization(options: [.alert, .badge, .sound])
            if granted {
                await registerForRemoteNotifications()
            }
            permissionStatus = await fetchAuthorizationStatus()
        } catch {
            SpotLinkLogger.warn("APNs permission request failed: \(error.localizedDescription)")
        }
    }

    public func checkPermissionStatus() async {
        permissionStatus = await fetchAuthorizationStatus()
    }

    // MARK: - Device Token

    /// Poziva se iz AppDelegate nakon dobijanja APNs tokena.
    public func didRegisterDeviceToken(_ tokenData: Data) async {
        let tokenString = tokenData.map { String(format: "%02.2hhx", $0) }.joined()
        deviceToken = tokenString
        await uploadDeviceToken(tokenString)
    }

    public func didFailToRegisterForRemoteNotifications(_ error: Error) {
        SpotLinkLogger.warn("APNs registration failed: \(error.localizedDescription)")
    }

    // MARK: - Private

    private func registerForRemoteNotifications() async {
        #if os(iOS)
        await MainActor.run {
            UIApplication.shared.registerForRemoteNotifications()
        }
        #endif
    }

    private func uploadDeviceToken(_ token: String) async {
        do {
            try await notificationService?.registerDeviceToken(token)
            SpotLinkLogger.info("Device token registrovan na backendu")
        } catch {
            SpotLinkLogger.warn("Device token upload failed: \(error.localizedDescription)")
        }
    }

    // Vraca samo Sendable UNAuthorizationStatus, bez prenosa non-Sendable
    // UNNotificationSettings preko granice izolacije aktora.
    nonisolated private func fetchAuthorizationStatus() async -> UNAuthorizationStatus {
        await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
    }
}

