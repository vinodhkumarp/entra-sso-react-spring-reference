import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { MsalProvider } from "@azure/msal-react";
import { App } from "./App";
import { msalInstance, registerAccountSelection } from "./authConfig";
import { errorType, logger } from "./logger";
import "./styles.css";

/** Initializes MSAL and renders the production Microsoft Entra application. */
async function start(): Promise<void> {
  const rootElement = document.getElementById("root");
  if (!rootElement) {
    throw new Error("The root HTML element was not found");
  }
  const root = createRoot(rootElement);

  await msalInstance.initialize();
  registerAccountSelection();

  const redirectResult = await msalInstance.handleRedirectPromise();
  if (redirectResult?.account) {
    msalInstance.setActiveAccount(redirectResult.account);
  } else if (!msalInstance.getActiveAccount()) {
    msalInstance.setActiveAccount(msalInstance.getAllAccounts()[0] ?? null);
  }

  root.render(
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
