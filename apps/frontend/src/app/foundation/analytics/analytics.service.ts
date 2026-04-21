import { Injectable, OnDestroy, inject } from '@angular/core';

import { SPOTLINK_APP_CONFIG } from '@foundation/core';
import { environment } from '@env/environment';
import { AnalyticsEvent } from './analytics.models';

@Injectable({ providedIn: 'root' })
export class AnalyticsService implements OnDestroy {
  private readonly config = inject(SPOTLINK_APP_CONFIG);
  private readonly queue: AnalyticsEvent[] = [];
  private readonly sessionId = this.createSessionId();
  private readonly flushIntervalMs = 30000;
  private readonly maxQueueSize = 20;
  private readonly endpoint = `${this.config.baseApiUrl}/analytics/events`;
  private readonly flushTimer: ReturnType<typeof setInterval> | null = environment.production
    ? setInterval(() => this.flush(), this.flushIntervalMs)
    : null;

  track(event: string, properties?: Record<string, unknown>): void {
    this.queue.push({
      event,
      properties,
      timestamp: new Date().toISOString(),
      url: typeof window === 'undefined' ? '' : window.location.href,
      sessionId: this.sessionId,
    });

    if (!environment.production) {
      console.debug(`[${this.config.appName}:analytics]`, event, properties ?? {});
    }

    if (this.queue.length >= this.maxQueueSize) {
      this.flush();
    }
  }

  flush(): void {
    if (this.queue.length === 0 || typeof navigator === 'undefined') {
      return;
    }

    const events = this.queue.splice(0, this.queue.length);
    const body = JSON.stringify({ events });

    if ('sendBeacon' in navigator) {
      navigator.sendBeacon(this.endpoint, new Blob([body], { type: 'application/json' }));
      return;
    }

    fetch(this.endpoint, {
      method: 'POST',
      body,
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      keepalive: true,
    }).catch(() => undefined);
  }

  ngOnDestroy(): void {
    if (this.flushTimer) {
      clearInterval(this.flushTimer);
    }
    this.flush();
  }

  private createSessionId(): string {
    return `sl_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 10)}`;
  }
}
