import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '@foundation/networking';
import {
  OperatorAccount,
  OperatorDashboardSummary,
  OperatorResourceHealth,
} from './operator.models';

@Injectable({ providedIn: 'root' })
export class OperatorService {
  private readonly api = inject(ApiClient);

  getCurrentOperator(): Observable<OperatorAccount> {
    return this.api.get<OperatorAccount>('/operator/me');
  }

  getDashboardSummary(): Observable<OperatorDashboardSummary> {
    return this.api.get<OperatorDashboardSummary>('/operator/dashboard/summary');
  }

  getResourceHealth(): Observable<OperatorResourceHealth[]> {
    return this.api.get<OperatorResourceHealth[]>('/operator/resources/health');
  }
}
