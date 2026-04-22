import Foundation

// MARK: - Support Service

public final class SupportService: Sendable {
    private let apiClient: APIClientProtocol

    public init(apiClient: APIClientProtocol) {
        self.apiClient = apiClient
    }

    public func listTickets(page: Int = 0, size: Int = 20) async throws -> APIPage<SupportTicket> {
        try await apiClient.get("/support/tickets", query: ["page": String(page), "size": String(size)])
    }

    public func createTicket(_ request: CreateTicketRequest) async throws -> SupportTicket {
        try await apiClient.post("/support/tickets", body: request)
    }

    public func getMessages(ticketId: String) async throws -> [SupportMessage] {
        try await apiClient.get("/support/tickets/\(ticketId)/messages", query: nil)
    }

    public func addMessage(ticketId: String, body: String) async throws -> SupportMessage {
        try await apiClient.post("/support/tickets/\(ticketId)/messages", body: AddMessageRequest(body: body))
    }
}
