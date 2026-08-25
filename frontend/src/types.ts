/** Authorization metadata returned by the backend's `/api/me` endpoint. */
export interface UserAccess {
  subjectId: string;
  username: string;
  displayName: string;
  tenantId: string;
  roles: string[];
  functionalities: string[];
  tokenExpiresAt: string;
}

/** Example dashboard response returned by the Spring Boot backend. */
export interface DashboardResponse {
  message: string;
  widgets: string[];
  generatedAt: string;
}

/** Example reports response returned by the Spring Boot backend. */
export interface ReportsResponse {
  message: string;
  reports: string[];
  generatedAt: string;
}

/** Example administrator projection returned by the Spring Boot backend. */
export interface AdminUser {
  id: string;
  displayName: string;
  status: string;
}
