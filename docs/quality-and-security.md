# Quality gates and dependency security

This repository keeps its quality tooling in the build so contributors do not need global
Checkstyle, SpotBugs, JaCoCo, OWASP Dependency-Check, or CycloneDX installations. Java checks run
through the Maven Wrapper; frontend checks run through the npm version and lockfiles committed to
the repository.

From the repository root, `make verify`, `make coverage`, `make security`, and `make sbom` provide
the complete developer entry points. See [developer-workflow.md](developer-workflow.md) for the
Makefile command and environment-variable reference. The component commands below document what
each Make target executes.

## Enforced Java quality gates

Both `backend` and `local-testing/mock-jwt-issuer` use the same policy:

| Gate                   | Purpose                                                | Build behavior                         |
| ---------------------- | ------------------------------------------------------ | -------------------------------------- |
| Maven Enforcer         | Require Java 25, Maven 3.9.x, and release dependencies | Fails early                            |
| Unit/integration tests | Verify security and application behavior               | Fails on a test failure                |
| JaCoCo                 | Measure handwritten Java line coverage                 | Fails below 85%                        |
| Checkstyle             | Enforce the committed Java source rules                | Fails on any violation                 |
| SpotBugs               | Inspect compiled bytecode for bug patterns             | Fails on low, medium, or high findings |
| Spotless               | Enforce deterministic google-java-format output        | Fails on formatting drift              |

The 85% threshold applies to aggregate **line coverage** for each Java application. OpenAPI
Generator output under `com.example.entrasso.generated` is excluded because it is not handwritten
code. The production delegates, security configuration, role mapping, logging filter, formatter,
and other application classes remain in scope.

The HTML coverage reports are generated at:

```text
backend/target/site/jacoco/index.html
local-testing/mock-jwt-issuer/target/site/jacoco/index.html
```

Do not exclude a class merely to recover the percentage. Add behavior-focused tests unless the
class is machine-generated or contains no application behavior. The threshold is a regression
guard, not evidence that the software is defect-free.

## Run all Java checks locally

macOS/Linux, from the repository root:

```bash
cd backend
./mvnw clean verify

cd ../local-testing/mock-jwt-issuer
../../backend/mvnw clean verify
```

PowerShell, from the repository root:

```powershell
cd backend
.\mvnw.cmd clean verify

cd ..\local-testing\mock-jwt-issuer
..\..\backend\mvnw.cmd clean verify
```

`verify` is the authoritative command: it runs tests, coverage enforcement, Checkstyle, SpotBugs,
and Spotless. Checkstyle uses [config/checkstyle/checkstyle.xml](../config/checkstyle/checkstyle.xml).
SpotBugs suppressions are restricted to documented, narrow matches in
[config/spotbugs/exclude.xml](../config/spotbugs/exclude.xml).

When a static-analysis result is a confirmed false positive, document why, restrict the
suppression to the smallest class/method and bug pattern possible, and require review. Never lower
the global severity or disable an analyzer to silence one finding.

## Frontend static checks

Both React applications use Prettier and the TypeScript compiler in strict mode:

```bash
cd frontend
npm ci
npm run check

cd ../local-testing/token-test-ui
npm ci
npm run check
```

TypeScript 7 is currently outside the supported TypeScript range published by `typescript-eslint`,
so this repository does not install an incompatible ESLint parser. Add ESLint when the parser
officially supports the project's TypeScript version; until then, strict type checking is the
frontend static-analysis gate.

## Scan Java dependencies and generate an SBOM

The opt-in Maven `security` profile runs OWASP Dependency-Check and generates a CycloneDX JSON
software bill of materials. It fails for a known vulnerability with CVSS `7.0` or higher, or if
the analyzer itself fails.

An NVD API key is optional for an occasional local scan but strongly recommended for reliable and
faster updates. [Request a key from NVD](https://nvd.nist.gov/developers/request-an-api-key) and
place it only in the `NVD_API_KEY` environment variable; never put it in a POM, command-line
property, log, or committed file.

macOS/Linux:

```bash
cd backend
export NVD_API_KEY='<your-NVD-api-key>'
./mvnw --no-transfer-progress -Psecurity verify
```

PowerShell:

```powershell
cd backend
$env:NVD_API_KEY = '<your-NVD-api-key>'
.\mvnw.cmd --no-transfer-progress -Psecurity verify
```

Scan the local issuer by running the same profile from its directory:

```bash
cd local-testing/mock-jwt-issuer
../../backend/mvnw --no-transfer-progress -Psecurity verify
```

```powershell
cd local-testing\mock-jwt-issuer
..\..\backend\mvnw.cmd --no-transfer-progress -Psecurity verify
```

The first Dependency-Check run downloads its vulnerability database and can take substantially
longer than later runs. Maven caches the plugin and vulnerability data for subsequent scans.

Each Java scan writes the following ignored build artifacts under that application's `target/`:

```text
dependency-check-report.html
dependency-check-report.json
dependency-check-report.sarif
sbom.json
```

The SBOM is an inventory, not a vulnerability verdict. Dependency-Check performs best-effort
matching and can produce false positives or false negatives. Confirm the dependency path,
affected version range, exploitability, and upstream remediation before changing or suppressing a
finding.

## Scan Node dependencies and generate an SBOM

Run the commands in both `frontend` and `local-testing/token-test-ui`:

```bash
npm ci
npm run security:audit
npm run security:sbom --silent > sbom.cdx.json
```

Use an explicit UTF-8 encoding with Windows PowerShell:

```powershell
npm ci
npm run security:audit
npm run security:sbom --silent | Set-Content -Encoding utf8 sbom.cdx.json
```

The audit fails when npm reports a high or critical vulnerability. The SBOM command uses only the
committed package lock and writes a CycloneDX application inventory when redirected as shown.

Do not run `npm audit fix --force` automatically. Review the proposed dependency changes, keep the
lockfile in sync, and rerun formatting, type checking, the production build, and the security
audit.

## Continuous monitoring

- The normal `CI` workflow runs all deterministic Java gates, both React checks, and both npm
  audits on pushes and pull requests. JaCoCo HTML reports are retained as workflow artifacts.
- `Dependency security` runs weekly and on manual dispatch. It scans both Java applications,
  uploads Dependency-Check reports/SBOMs, publishes SARIF findings to GitHub code scanning, audits
  both npm applications, and archives their SBOMs.
- Dependabot covers both Maven POMs, both npm lockfiles, and GitHub Actions.
- The scheduled workflow reads an optional repository secret named `NVD_API_KEY`. Configure that
  secret for reliable operational scans.

## Repository security practices

For a public production-oriented repository:

1. Protect `main` and require every CI job before merge.
2. Require pull-request review and dismiss approvals when security-sensitive code changes.
3. Enable Dependabot alerts/security updates, dependency review, secret scanning, push protection,
   code scanning, and private vulnerability reporting.
4. Keep workflow permissions minimal and review every third-party GitHub Action update.
5. Never commit tenant secrets, client secrets, NVD keys, bearer tokens, `.env.local`, reports, or
   generated build output.
6. Treat scanner suppression as reviewed security code with a reason and removal condition.
7. Patch high/critical vulnerabilities promptly, while triaging reachability and compensating
   controls rather than blindly applying breaking upgrades.
8. Generate and retain an SBOM for releases, and record the source revision used to create it.
9. Continue threat modelling, authorization tests, logging review, and runtime monitoring; build
   scanners cover only part of the system risk.
