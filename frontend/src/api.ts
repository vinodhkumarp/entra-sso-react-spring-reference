import { InteractionRequiredAuthError } from "@azure/msal-browser";
import type {
  AccountInfo,
  IPublicClientApplication,
} from "@azure/msal-browser";
import { apiScope } from "./authConfig";

/** Error type that preserves the HTTP status returned by a protected API. */
export class ApiError extends Error {
  /** Creates a readable API error without retaining or exposing the access token. */
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
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
  const accessToken = await acquireAccessToken(instance, account);
  const response = await fetch(`${baseUrl()}${path}`, {
    method: "GET",
    headers: {
      Accept: "application/json",
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (!response.ok) {
    const challenge = response.headers.get("WWW-Authenticate");
    const suffix = challenge ? ` (${challenge})` : "";
    throw new ApiError(
      response.status,
      `The backend returned HTTP ${response.status}${suffix}`,
    );
  }

  return (await response.json()) as T;
}
