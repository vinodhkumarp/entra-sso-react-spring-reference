/** Fixed identity metadata published by the standalone local issuer. */
export interface TestIdentity {
  id: string;
  subjectId: string;
  username: string;
  displayName: string;
  role: "APP_USER" | "APP_MANAGER" | "APP_ADMIN";
}

/** Short-lived token response returned only by the local issuer. */
export interface TokenResponse {
  accessToken: string;
  tokenType: "Bearer";
  expiresIn: number;
  issuedAt: string;
  expiresAt: string;
  user: TestIdentity;
}

/** Authorization metadata returned by the production backend's `/api/me` endpoint. */
export interface UserAccess {
  subjectId: string;
  username: string;
  displayName: string;
  tenantId: string;
  roles: string[];
  functionalities: string[];
  tokenExpiresAt: string;
}

/** Example dashboard response returned by the production backend. */
export interface DashboardResponse {
  message: string;
  widgets: string[];
  generatedAt: string;
}

/** Example reports response returned by the production backend. */
export interface ReportsResponse {
  message: string;
  reports: string[];
  generatedAt: string;
}

/** Example administrator response returned by the production backend. */
export interface AdminUser {
  id: string;
  displayName: string;
  status: string;
}
