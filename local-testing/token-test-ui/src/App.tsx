import { useEffect, useMemo, useState } from "react";
import { callBackend } from "./backendClient";
import { loadTestIdentities, requestTestToken } from "./localIssuerClient";
import type {
  AdminUser,
  DashboardResponse,
  ReportsResponse,
  TestIdentity,
  TokenResponse,
  UserAccess,
} from "./types";

type FlowResult = DashboardResponse | ReportsResponse | AdminUser[] | null;

/** Demonstrates token issuance and production API authorization without loading MSAL. */
export function App() {
  const [identities, setIdentities] = useState<TestIdentity[]>([]);
  const [selectedId, setSelectedId] = useState("");
  const [token, setToken] = useState<TokenResponse | null>(null);
  const [access, setAccess] = useState<UserAccess | null>(null);
  const [result, setResult] = useState<FlowResult>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  /** Loads identities once from the external issuer; React cannot invent arbitrary roles. */
  useEffect(() => {
    let active = true;
    void loadTestIdentities()
      .then((loaded) => {
        if (active) {
          setIdentities(loaded);
          setSelectedId(
            loaded.find((identity) => identity.id === "manager")?.id ?? loaded[0]?.id ?? "",
          );
        }
      })
      .catch((loadError: unknown) => {
        if (active) {
          setError(errorMessage(loadError));
        }
      });
    return () => {
      active = false;
    };
  }, []);

  /** Creates the set of UI hints returned by the production backend. */
  const functionalities = useMemo(() => new Set(access?.functionalities ?? []), [access]);

  /** Requests a signed token externally and immediately sends it to the backend `/api/me`. */
  async function createSession(): Promise<void> {
    if (!selectedId) {
      setError("Select a local identity.");
      return;
    }
    setLoading(true);
    setError(null);
    setMessage(null);
    setResult(null);
    try {
      const issuedToken = await requestTestToken(selectedId);
      const userAccess = await callBackend<UserAccess>("/api/me", issuedToken.accessToken);
      setToken(issuedToken);
      setAccess(userAccess);
    } catch (sessionError: unknown) {
      setToken(null);
      setAccess(null);
      setError(errorMessage(sessionError));
    } finally {
      setLoading(false);
    }
  }

  /** Copies the local token for optional curl or Postman API tests. */
  async function copyToken(): Promise<void> {
    if (!token) {
      return;
    }
    try {
      await navigator.clipboard.writeText(token.accessToken);
      setMessage(
        "Bearer token copied. It expires shortly and is valid only for this local issuer.",
      );
    } catch (copyError: unknown) {
      setError(errorMessage(copyError));
    }
  }

  /** Clears all in-memory token and backend response data. */
  function clearSession(): void {
    setToken(null);
    setAccess(null);
    setResult(null);
    setMessage(null);
    setError(null);
  }

  /** Calls a protected production endpoint with the externally issued bearer token. */
  async function runFlow<T>(path: string): Promise<void> {
    if (!token) {
      setError("Create a local token first.");
      return;
    }
    setLoading(true);
    setError(null);
    setMessage(null);
    try {
      setResult((await callBackend<T>(path, token.accessToken)) as FlowResult);
    } catch (flowError: unknown) {
      setResult(null);
      setError(errorMessage(flowError));
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="shell">
      <aside className="warning" role="note">
        <strong>Local testing utility</strong>
        <span>Not Microsoft Entra · never deploy this application</span>
      </aside>

      <header className="hero">
        <div>
          <p className="eyebrow">External RS256 issuer</p>
          <h1>JWT Test Console</h1>
          <p className="subtitle">
            Obtain a short-lived token from a standalone issuer and send it through the production
            backend's normal bearer-token validation chain.
          </p>
        </div>
        {token && (
          <button className="secondary" onClick={clearSession}>
            Clear token
          </button>
        )}
      </header>

      {!token ? (
        <section className="card token-form">
          <h2>Choose a fixed test identity</h2>
          <p>The issuer—not this browser—owns the private signing key and assigned role.</p>
          <label htmlFor="identity">Identity and application role</label>
          <select
            id="identity"
            disabled={identities.length === 0 || loading}
            value={selectedId}
            onChange={(event) => setSelectedId(event.target.value)}
          >
            {identities.map((identity) => (
              <option key={identity.id} value={identity.id}>
                {identity.displayName} — {identity.role}
              </option>
            ))}
          </select>
          <button
            className="primary"
            disabled={!selectedId || loading}
            onClick={() => void createSession()}
          >
            {loading ? "Issuing and validating…" : "Get token and call /api/me"}
          </button>
        </section>
      ) : (
        <>
          <section className="identity-grid">
            <article className="card">
              <p className="eyebrow">Backend identity</p>
              <h2>{access?.displayName}</h2>
              <p>{access?.username}</p>
              <p className="hint">
                Token expires at {new Date(token.expiresAt).toLocaleTimeString()}.
              </p>
              <button className="text-button" onClick={() => void copyToken()}>
                Copy bearer token
              </button>
            </article>

            <article className="card">
              <p className="eyebrow">Validated roles</p>
              <div className="chips">
                {(access?.roles ?? []).map((role) => (
                  <span className="chip" key={role}>
                    {role}
                  </span>
                ))}
              </div>
            </article>

            <article className="card">
              <p className="eyebrow">UI functionality</p>
              <div className="chips">
                {[...functionalities].map((functionality) => (
                  <span className="chip accent" key={functionality}>
                    {functionality}
                  </span>
                ))}
              </div>
            </article>
          </section>

          <section className="card flows">
            <div className="section-heading">
              <div>
                <p className="eyebrow">Production API</p>
                <h2>Protected endpoint tests</h2>
              </div>
              {loading && <span className="status">Working…</span>}
            </div>
            <div className="flow-grid">
              {functionalities.has("DASHBOARD_VIEW") && (
                <button onClick={() => void runFlow<DashboardResponse>("/api/dashboard")}>
                  <strong>Dashboard</strong>
                  <span>APP_USER+</span>
                </button>
              )}
              {functionalities.has("REPORTS_VIEW") && (
                <button onClick={() => void runFlow<ReportsResponse>("/api/reports")}>
                  <strong>Reports</strong>
                  <span>APP_MANAGER+</span>
                </button>
              )}
              {functionalities.has("ADMIN_USERS_VIEW") && (
                <button onClick={() => void runFlow<AdminUser[]>("/api/admin/users")}>
                  <strong>Admin users</strong>
                  <span>APP_ADMIN</span>
                </button>
              )}
            </div>
            {result && <pre>{JSON.stringify(result, null, 2)}</pre>}
          </section>
        </>
      )}

      {message && <p className="message">{message}</p>}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </main>
  );
}

/** Converts unknown failures into safe local-development messages. */
function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "An unexpected local-test error occurred.";
}
