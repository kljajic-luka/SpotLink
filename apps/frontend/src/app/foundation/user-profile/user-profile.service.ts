import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '@foundation/networking';
import { UpdateProfileRequest, UserProfileDetails } from './user-profile.models';

@Injectable({ providedIn: 'root' })
export class UserProfileService {
  private readonly api = inject(ApiClient);

  getCurrentProfile(): Observable<UserProfileDetails> {
    return this.api.get<UserProfileDetails>('/users/me/profile');
  }

  getProfile(userId: string): Observable<UserProfileDetails> {
    return this.api.get<UserProfileDetails>(`/users/${encodeURIComponent(userId)}/profile`);
  }

  updateProfile(payload: UpdateProfileRequest): Observable<UserProfileDetails> {
    return this.api.patch<UserProfileDetails, UpdateProfileRequest>('/users/me/profile', payload);
  }
}
