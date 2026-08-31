/** Header used to correlate the test UI request with production-backend logs. */
const CORRELATION_ID_HEADER = "X-Correlation-Id";

/** Returns the configurable production backend URL without a trailing slash. */
function backendBaseUrl(): string {
  return (import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080").replace(/\/$/, "");
}

/** Sends a local issuer token through the production backend's normal bearer-token boundary. */
export async function callBackend<T>(path: string, accessToken: string): Promise<T> {
  const correlationId = crypto.randomUUID();
  const response = await fetch(`${backendBaseUrl()}${path}`, {
    headers: {
      Accept: "application/json",
      Authorization: `Bearer ${accessToken}`,
      [CORRELATION_ID_HEADER]: correlationId,
    },
  });
  const effectiveCorrelationId = response.headers.get(CORRELATION_ID_HEADER) ?? correlationId;
  if (!response.ok) {
    throw new Error(
      `Backend returned HTTP ${response.status}; correlation ID ${effectiveCorrelationId}`,
    );
  }
  return (await response.json()) as T;
}
