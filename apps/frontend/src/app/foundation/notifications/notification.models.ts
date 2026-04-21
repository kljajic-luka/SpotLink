export type NotificationType =
  | 'RESERVATION_CONFIRMED'
  | 'RESERVATION_CANCELLED'
  | 'PAYMENT_ACTION_REQUIRED'
  | 'ACCESS_INSTRUCTIONS_READY'
  | 'SUPPORT_REPLY'
  | 'OPERATOR_ALERT'
  | 'SYSTEM';

export interface NotificationItem {
  id: string;
  type: NotificationType;
  title: string;
  body: string;
  relatedEntityId?: string;
  read: boolean;
  createdAt: string;
}

export interface RegisterDeviceTokenRequest {
  deviceToken: string;
  platform: 'WEB' | 'IOS' | 'ANDROID';
}

export interface UnreadNotificationCount {
  count: number;
}
