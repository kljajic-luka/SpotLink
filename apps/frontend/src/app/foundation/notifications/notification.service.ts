import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient, ApiPage } from '@foundation/networking';
import {
  NotificationItem,
  RegisterDeviceTokenRequest,
  UnreadNotificationCount,
} from './notification.models';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly api = inject(ApiClient);

  list(page = 0, size = 20): Observable<ApiPage<NotificationItem>> {
    return this.api.get<ApiPage<NotificationItem>>('/notifications', {
      params: {
        page,
        size,
      },
    });
  }

  unreadCount(): Observable<UnreadNotificationCount> {
    return this.api.get<UnreadNotificationCount>('/notifications/unread-count');
  }

  markRead(notificationId: string): Observable<void> {
    return this.api.post<void>(`/notifications/${encodeURIComponent(notificationId)}/read`, {});
  }

  registerDeviceToken(payload: RegisterDeviceTokenRequest): Observable<void> {
    return this.api.post<void, RegisterDeviceTokenRequest>('/notifications/device-tokens', payload);
  }
}
