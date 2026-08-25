import { useEffect, useMemo, useState } from "react";
import { useIsAuthenticated, useMsal } from "@azure/msal-react";
import { callApi } from "./api";
import { loginRequest } from "./authConfig";
import type { AdminUser, DashboardResponse, ReportsResponse, UserAccess } from "./types";

type FlowResult = DashboardResponse | ReportsResponse | AdminUser[] | null;

/** Renders sign-in, authorization metadata, role-driven navigation, and protected flow examples. */
export function App() {
  const { instance, accounts } = useMsal();
  const isAuthenticated = useIsAuthenticated();
  const account = instance.getActiveAccount() ?? accounts[0];
  const accountId = account?.homeAccountId;
  const [access, setAccess] = useState<UserAccess | null>(null);
  const [result, setResult] = useState<FlowResult>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  /** Loads the backend's role-to-functionality view after successful SSO. */
  useEffect(() => {
    const selectedAccount =
      instance.getActiveAccount() ??
      instance.getAllAccounts().find((candidate) => candidate.homeAccountId === accountId);

    if (!isAuthenticated || !selectedAccount) {
      setAccess(null);
      return;
    }

    let active = true;
    setLoading(true);
    setError(null);

    void callApi<UserAccess>(instance, selectedAccount, "/api/me")
      .then((userAccess) => {
        if (active) {
          setAccess(userAccess);
        }
      })
      .catch((loadError: unknown) => {
        if (active) {
          setError(errorMessage(loadError));
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [accountId, instance, isAuthenticated]);

  /** Converts the backend's UI hints into a set for simple menu visibility checks. */
  const functionalities = useMemo(() => new Set(access?.functionalities ?? []), [access]);

  /** Returns whether the current UI metadata includes a named functionality. */
  const can = (functionality: string): boolean => functionalities.has(functionality);

  /** Starts the Entra authorization-code-with-PKCE redirect flow. */
  const signIn = (): void => {
    void instance.loginRedirect(loginRequest);
  };

  /** Clears the MSAL account and returns the browser to the application origin. */
  const signOut = (): void => {
    void instance.logoutRedirect({
      account,
      postLogoutRedirectUri: window.location.origin,
    });
  };

  /** Calls one example endpoint and renders either its JSON response or a readable error. */
  async function runFlow<T>(path: string): Promise<void> {
    if (!account) {
      setError("Sign in before calling an API.");
      return;
    }
    setLoading(true);
    setError(null);
    try {
      setResult((await callApi<T>(instance, account, path)) as FlowResult);
    } catch (flowError: unknown) {
      setError(errorMessage(flowError));
      setResult(null);
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="shell">
      <header className="hero">
        <div>
          <p className="eyebrow">Reference architecture</p>
          <h1>Microsoft Entra SSO</h1>
          <p className="subtitle">
            One React SPA, one Spring Boot resource API, and no custom authentication endpoint.
          </p>
        </div>
        {isAuthenticated ? (
          <button className="secondary" onClick={signOut}>
            Sign out
          </button>
        ) : (
          <button className="primary" onClick={signIn}>
            Sign in with Microsoft
          </button>
        )}
      </header>

      {!isAuthenticated ? (
        <section className="card sign-in-card">
          <span className="step">1</span>
          <div>
            <h2>Start the SSO flow</h2>
            <p>
              MSAL redirects to Microsoft Entra, then returns with an authorization code and
              exchanges it using PKCE. The SPA requests an access token for this API—not an ID token
              and not a token created by Spring Boot.
            </p>
          </div>
        </section>
      ) : (
        <>
          <section className="identity-grid">
            <article className="card identity-card">
              <p className="eyebrow">Signed in</p>
              <h2>{access?.displayName ?? account?.name ?? "Loading user"}</h2>
              <p>{access?.username ?? account?.username}</p>
              <dl>
                <dt>Object ID</dt>
                <dd>{access?.subjectId ?? "Loading…"}</dd>
                <dt>Tenant</dt>
                <dd>{access?.tenantId ?? "Loading…"}</dd>
              </dl>
            </article>

            <article className="card">
              <p className="eyebrow">Token roles</p>
              <div className="chips">
                {(access?.roles ?? []).map((role) => (
                  <span className="chip" key={role}>
                    {role}
                  </span>
                ))}
              </div>
              <p className="hint">Roles come from Entra's validated access-token `roles` claim.</p>
            </article>

            <article className="card">
              <p className="eyebrow">UI functionality</p>
              <div className="chips">
                {[...functionalities].sort().map((functionality) => (
                  <span className="chip accent" key={functionality}>
                    {functionality}
                  </span>
                ))}
              </div>
              <p className="hint">These values hide UI controls; they never replace API checks.</p>
            </article>
          </section>

          <section className="card flows">
            <div className="section-heading">
              <div>
                <p className="eyebrow">End-to-end examples</p>
                <h2>Protected API flows</h2>
              </div>
              {loading && <span className="status">Working…</span>}
            </div>

            <div className="flow-grid">
              {can("DASHBOARD_VIEW") && (
                <button onClick={() => runFlow<DashboardResponse>("/api/dashboard")}>
                  <strong>Dashboard</strong>
                  <span>backend · APP_USER+</span>
                </button>
              )}
              {can("REPORTS_VIEW") && (
                <button onClick={() => runFlow<ReportsResponse>("/api/reports")}>
                  <strong>Reports</strong>
                  <span>backend · APP_MANAGER+</span>
                </button>
              )}
              {can("ADMIN_USERS_VIEW") && (
                <button onClick={() => runFlow<AdminUser[]>("/api/admin/users")}>
                  <strong>Admin users</strong>
                  <span>backend · APP_ADMIN</span>
                </button>
              )}
            </div>

            {!loading && functionalities.size === 0 && (
              <p className="empty">The user has no application role assigned.</p>
            )}
            {error && (
              <p className="error" role="alert">
                {error}
              </p>
            )}
            {result && <pre>{JSON.stringify(result, null, 2)}</pre>}
          </section>
        </>
      )}
    </main>
  );
}

/** Converts unknown promise failures into safe user-facing text. */
function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "An unexpected request error occurred.";
}
