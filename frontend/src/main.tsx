import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { MsalProvider } from "@azure/msal-react";
import { App } from "./App";
import { msalInstance, registerAccountSelection } from "./authConfig";
import { errorType, logger } from "./logger";
import "./styles.css";

/** Initializes MSAL redirect handling before React components request authentication state. */
async function start(): Promise<void> {
  await msalInstance.initialize();
  registerAccountSelection();

  const redirectResult = await msalInstance.handleRedirectPromise();
  if (redirectResult?.account) {
    msalInstance.setActiveAccount(redirectResult.account);
  } else if (!msalInstance.getActiveAccount()) {
    msalInstance.setActiveAccount(msalInstance.getAllAccounts()[0] ?? null);
  }

  const rootElement = document.getElementById("root");
  if (!rootElement) {
    throw new Error("The root HTML element was not found");
  }

  createRoot(rootElement).render(
    <StrictMode>
      <MsalProvider instance={msalInstance}>
        <App />
      </MsalProvider>
    </StrictMode>,
  );
  logger.info("application.started");
}

void start().catch((error: unknown) => {
  logger.error("application.start.failed", { errorType: errorType(error) });
});
