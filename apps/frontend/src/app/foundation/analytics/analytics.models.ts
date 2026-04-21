export interface AnalyticsEvent {
  event: string;
  properties?: Record<string, unknown>;
  timestamp: string;
  url: string;
  sessionId: string;
}

export interface WebVitalMetric {
  name: string;
  value: number;
  rating?: 'good' | 'needs-improvement' | 'poor';
}
