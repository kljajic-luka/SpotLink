export type UserRole = 'CUSTOMER' | 'OPERATOR' | 'SUPPORT' | 'ADMIN';

export interface RoleRequirement {
  roles: readonly UserRole[];
  redirectTo?: string;
}
