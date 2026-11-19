export type Role = 'SUPER_ADMIN' | 'OWNER' | 'AGENT';

export interface AreaRef {
  id: number;
  name: string;
}

export interface AppUser {
  id: number;
  name: string;
  phone: string;
  role: Role;
  areas: AreaRef[];
}

/** POST /auth/login and /auth/refresh. */
export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  user: AppUser;
}

/** Where each role lands after login. Agents are not allowed on the web. */
export const HOME_BY_ROLE: Record<Exclude<Role, 'AGENT'>, string> = {
  OWNER: '/dashboard',
  SUPER_ADMIN: '/admin/financiers',
};
