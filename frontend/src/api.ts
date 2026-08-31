import { InteractionRequiredAuthError } from "@azure/msal-browser";
import type { AccountInfo, IPublicClientApplication } from "@azure/msal-browser";
import { callApiWithToken } from "./apiClient";
import { apiScope } from "./authConfig";
import { createCorrelationId, errorType, logger } from "./logger";

/**
 * Obtains an access token silently and falls back to an interactive redirect only when required.
 */
async function acquireAccessToken(
  instance: IPublicClientApplication,
  account: AccountInfo,
): Promise<string> {
  try {
    const response = await instance.acquireTokenSilent({
      account,
      scopes: [apiScope],
    });
    return response.accessToken;
  } catch (error) {
    if (error instanceof InteractionRequiredAuthError) {
      await instance.acquireTokenRedirect({
        account,
        scopes: [apiScope],
      });
    }
    throw error;
  }
}

/**
 * Calls the protected backend with a short-lived bearer token and returns typed JSON.
 *
 * @example
 * const access = await callApi<UserAccess>(instance, account, "/api/me");
 */
export async function callApi<T>(
  instance: IPublicClientApplication,
  account: AccountInfo,
  path: string,
): Promise<T> {
  const correlationId = createCorrelationId();
  const startedAt = performance.now();
  logger.debug("api.token_acquisition.started", { correlationId, path });

  let accessToken: string;
  try {
    accessToken = await acquireAccessToken(instance, account);
  } catch (error: unknown) {
    logger.error("api.token_acquisition.failed", {
      correlationId,
      path,
      errorType: errorType(error),
    });
    throw error;
  }
  logger.debug("api.token_acquisition.completed", {
    correlationId,
    path,
    durationMs: Math.round(performance.now() - startedAt),
  });
  return callApiWithToken<T>(path, accessToken, correlationId);
}
