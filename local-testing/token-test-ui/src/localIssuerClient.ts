import type { TestIdentity, TokenResponse } from "./types";

/** Returns the configurable loopback issuer URL without a trailing slash. */
function issuerBaseUrl(): string {
  return (import.meta.env.VITE_LOCAL_ISSUER_URL ?? "http://localhost:9090").replace(/\/$/, "");
}

/** Reads a JSON response or throws a concise local-development error. */
async function readJson<T>(response: Response, operation: string): Promise<T> {
  if (!response.ok) {
    throw new Error(`${operation} failed with HTTP ${response.status}`);
  }
  return (await response.json()) as T;
}

/** Loads the allow-listed local identities; arbitrary roles cannot be supplied by the browser. */
export async function loadTestIdentities(): Promise<TestIdentity[]> {
  const response = await fetch(`${issuerBaseUrl()}/test-users`, {
    headers: { Accept: "application/json" },
  });
  return readJson<TestIdentity[]>(response, "Loading local identities");
}

/** Requests a short-lived token from the standalone issuer for one allow-listed identity. */
export async function requestTestToken(userId: string): Promise<TokenResponse> {
  const response = await fetch(`${issuerBaseUrl()}/test-token`, {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ userId }),
  });
  return readJson<TokenResponse>(response, "Creating a local token");
}
