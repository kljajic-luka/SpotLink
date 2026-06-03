import Foundation
import UserNotifications
#if canImport(UIKit)
import UIKit
#endif

enum PushNotificationStorageKey {
    static let lastDeviceToken = "push.lastDeviceToken"
}

@MainActor
public protocol PushDeviceTokenLifecycle: AnyObject {
    func uploadKnownDeviceTokenIfAuthenticated() async
    func unregisterKnownDeviceTokenBeforeLogout() async
}

// MARK: - Push Notification Manager

/// Apstrakcija za APNs permisije i device token registraciju.
@MainActor
public final class PushNotificationManager: NSObject, ObservableObject, PushDeviceTokenLifecycle {

    public static let shared = PushNotificationManager()

    @Published public private(set) var permissionStatus: UNAuthorizationStatus = .notDetermined
    @Published public private(set) var deviceToken: String?

    private let preferences: PreferenceStorage
    private var notificationService: DeviceTokenLifecycleServicing?

    public init(
        notificationService: DeviceTokenLifecycleServicing? = nil,
        preferences: PreferenceStorage = .shared
    ) {
        self.notificationService = notificationService
        self.preferences = preferences
        self.deviceToken = preferences.string(forKey: PushNotificationStorageKey.lastDeviceToken)
        super.init()
    }

    public func configure(notificationService: DeviceTokenLifecycleServicing) {
        self.notificationService = notificationService
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
        if canRegisterForRemoteNotifications {
            await registerForRemoteNotifications()
        }
    }

    // MARK: - Device Token

    /// Poziva se iz AppDelegate nakon dobijanja APNs tokena.
    public func didRegisterDeviceToken(_ tokenData: Data) async {
        let tokenString = tokenData.map { String(format: "%02.2hhx", $0) }.joined()
        persistDeviceToken(tokenString)
        await uploadDeviceToken(tokenString)
    }

    public func didFailToRegisterForRemoteNotifications(_ error: Error) {
        SpotLinkLogger.warn("APNs registration failed: \(error.localizedDescription)")
    }

    public func uploadKnownDeviceTokenIfAuthenticated() async {
        guard let token = knownDeviceToken else { return }
        deviceToken = token
        await uploadDeviceToken(token)
    }

    public func unregisterKnownDeviceTokenBeforeLogout() async {
        guard let token = knownDeviceToken, let notificationService else { return }
        do {
            try await notificationService.unregisterDeviceToken(token)
            clearDeviceToken()
            SpotLinkLogger.info("Device token odjavljen sa backenda")
        } catch {
            SpotLinkLogger.warn("Device token unregister failed: \(error.localizedDescription)")
        }
    }

    // MARK: - Private

    private var knownDeviceToken: String? {
        deviceToken ?? preferences.string(forKey: PushNotificationStorageKey.lastDeviceToken)
    }

    private var canRegisterForRemoteNotifications: Bool {
        switch permissionStatus {
        case .authorized, .provisional, .ephemeral:
            return true
        case .notDetermined, .denied:
            return false
        @unknown default:
            return false
        }
    }

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

    private func persistDeviceToken(_ token: String) {
        deviceToken = token
        preferences.set(token, forKey: PushNotificationStorageKey.lastDeviceToken)
    }

    private func clearDeviceToken() {
        deviceToken = nil
        preferences.remove(forKey: PushNotificationStorageKey.lastDeviceToken)
    }

    // Vraca samo Sendable UNAuthorizationStatus, bez prenosa non-Sendable
    // UNNotificationSettings preko granice izolacije aktora.
    nonisolated private func fetchAuthorizationStatus() async -> UNAuthorizationStatus {
        await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
    }
}
