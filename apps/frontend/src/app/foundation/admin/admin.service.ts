import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient, ApiPage } from '@foundation/networking';
import { AdminAuditEvent, AdminDashboardSummary, AdminUserSummary } from './admin.models';

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly api = inject(ApiClient);

  getDashboardSummary(): Observable<AdminDashboardSummary> {
    return this.api.get<AdminDashboardSummary>('/admin/dashboard/summary');
  }

  listUsers(page = 0, size = 25): Observable<ApiPage<AdminUserSummary>> {
    return this.api.get<ApiPage<AdminUserSummary>>('/admin/users', {
      params: {
        page,
        size,
      },
    });
  }

  listAuditEvents(page = 0, size = 25): Observable<ApiPage<AdminAuditEvent>> {
    return this.api.get<ApiPage<AdminAuditEvent>>('/admin/audit-events', {
      params: {
        page,
        size,
      },
    });
  }
}
