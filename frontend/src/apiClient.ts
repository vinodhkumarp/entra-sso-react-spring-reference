import { createCorrelationId, errorType, logger } from "./logger";

/** Header used to correlate one browser request with every backend log entry for that request. */
export const CORRELATION_ID_HEADER = "X-Correlation-Id";

/** Error type that preserves the HTTP status returned by a protected API. */
export class ApiError extends Error {
  /** Creates a readable API error without retaining or exposing the access token. */
  constructor(
    public readonly status: number,
    public readonly correlationId: string,
    message: string,
  ) {
    super(`${message}. Correlation ID: ${correlationId}`);
    this.name = "ApiError";
  }
}

/** Resolves the configured URL for the single Spring Boot backend. */
function baseUrl(): string {
  const value = import.meta.env.VITE_API_BASE_URL;
  if (!value?.trim()) {
    throw new Error("Missing required frontend setting: VITE_API_BASE_URL");
  }
  return value.replace(/\/$/, "");
}

/**
 * Sends an already acquired bearer token to the protected backend.
 *
 * <p>The token is intentionally never logged or persisted here. Local JWT testing uses a separate
 * application under {@code local-testing} and does not load this production module.
 *
 * @example
 * const access = await callApiWithToken<UserAccess>("/api/me", accessToken);
 */
export async function callApiWithToken<T>(
  path: string,
  accessToken: string,
  correlationId = createCorrelationId(),
): Promise<T> {
  const startedAt = performance.now();
  logger.info("api.request.started", { correlationId, method: "GET", path });

  let response: Response;
  try {
    response = await fetch(`${baseUrl()}${path}`, {
      method: "GET",
      headers: {
        Accept: "application/json",
        Authorization: `Bearer ${accessToken}`,
        [CORRELATION_ID_HEADER]: correlationId,
      },
    });
  } catch (error: unknown) {
    logger.error("api.request.network_failed", {
      correlationId,
      method: "GET",
      path,
      durationMs: Math.round(performance.now() - startedAt),
      errorType: errorType(error),
    });
    throw error;
  }

  const effectiveCorrelationId = response.headers.get(CORRELATION_ID_HEADER) ?? correlationId;
  const completionContext = {
    correlationId: effectiveCorrelationId,
    method: "GET",
    path,
    status: response.status,
    durationMs: Math.round(performance.now() - startedAt),
  };

  if (!response.ok) {
    logger.warn("api.request.failed", completionContext);
    const challenge = response.headers.get("WWW-Authenticate");
    const suffix = challenge ? ` (${challenge})` : "";
    throw new ApiError(
      response.status,
      effectiveCorrelationId,
      `The backend returned HTTP ${response.status}${suffix}`,
    );
  }

  logger.info("api.request.completed", completionContext);
  return (await response.json()) as T;
}
