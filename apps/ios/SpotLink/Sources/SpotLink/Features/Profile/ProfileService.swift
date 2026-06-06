import Foundation

// MARK: - Profile Service

public final class ProfileService: Sendable {
    private let apiClient: APIClientProtocol

    public init(apiClient: APIClientProtocol) {
        self.apiClient = apiClient
    }

    public func getCurrentProfile() async throws -> UserProfileDetails {
        try await apiClient.get("/users/me/profile", query: nil)
    }

    public func getProfile(_ userId: String) async throws -> UserProfileDetails {
        try await apiClient.get("/users/\(userId)/profile", query: nil)
    }

    public func updateProfile(_ request: UpdateProfileRequest) async throws -> UserProfileDetails {
        try await apiClient.patch("/users/me/profile", body: request)
    }

    public func updateNotificationPreferences(_ request: UpdateUserPreferencesRequest) async throws -> UserProfileDetails {
        try await updateProfile(UpdateProfileRequest(preferences: request))
    }

    public func requestAccountDeletion() async throws -> SupportTicket {
        try await apiClient.post("/users/me/deletion-request", body: AccountDeletionRequest())
    }
}
