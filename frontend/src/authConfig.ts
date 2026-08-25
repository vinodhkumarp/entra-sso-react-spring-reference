import {
  BrowserCacheLocation,
  EventType,
  LogLevel,
  PublicClientApplication,
} from "@azure/msal-browser";
import type {
  AuthenticationResult,
  Configuration,
  EventMessage,
  RedirectRequest,
} from "@azure/msal-browser";

/** Reads a required Vite setting and fails before authentication starts when it is missing. */
function requiredEnvironmentValue(name: keyof ImportMetaEnv): string {
  const value = import.meta.env[name];
  if (!value?.trim()) {
    throw new Error(`Missing required frontend setting: ${name}`);
  }
  return value.trim();
}

const tenantId = requiredEnvironmentValue("VITE_ENTRA_TENANT_ID");
const spaClientId = requiredEnvironmentValue("VITE_ENTRA_SPA_CLIENT_ID");
const apiClientId = requiredEnvironmentValue("VITE_ENTRA_API_CLIENT_ID");

/** Delegated API scope requested by the SPA for the Spring Boot backend. */
export const apiScope = `api://${apiClientId}/access_as_user`;

/**
 * MSAL browser configuration for a tenant-specific SPA using authorization code with PKCE.
 *
 * {@code Configuration} is imported with {@code import type}; importing it as a runtime value is
 * the cause of the Vite "does not provide an export named Configuration" error.
 */
export const msalConfig: Configuration = {
  auth: {
    clientId: spaClientId,
    authority: `https://login.microsoftonline.com/${tenantId}`,
    redirectUri: window.location.origin,
    postLogoutRedirectUri: window.location.origin,
  },
  cache: {
    cacheLocation: BrowserCacheLocation.SessionStorage,
  },
  system: {
    loggerOptions: {
      logLevel: import.meta.env.DEV ? LogLevel.Warning : LogLevel.Error,
      piiLoggingEnabled: false,
      loggerCallback: (level, message, containsPii) => {
        if (!containsPii && level <= LogLevel.Warning) {
          console.warn(`[MSAL] ${message}`);
        }
      },
    },
  },
};

/** Scope request used for interactive login and silent access-token acquisition. */
export const loginRequest: RedirectRequest = {
  scopes: [apiScope],
};

/** Single MSAL client shared by the React component tree. */
export const msalInstance = new PublicClientApplication(msalConfig);

/** Keeps MSAL's active account synchronized after a successful interactive login. */
export function registerAccountSelection(): void {
  msalInstance.addEventCallback((event: EventMessage) => {
    if (event.eventType === EventType.LOGIN_SUCCESS) {
      const result = event.payload as AuthenticationResult | null;
      const activeAccount = msalInstance.getActiveAccount();
      if (
        result?.account &&
        result.account.homeAccountId !== activeAccount?.homeAccountId
      ) {
        msalInstance.setActiveAccount(result.account);
      }
    }
  });
}
