# Developer entry points for the Entra SSO reference application.
#
# Run `make help` from the repository root. Local settings are read from the
# ignored `.env` file when it exists; command-line overrides still take
# precedence, for example: `make local BACKEND_PORT=8180`.

.DEFAULT_GOAL := help

ENV_FILE ?= .env

ifneq (,$(wildcard $(ENV_FILE)))
include $(ENV_FILE)
endif

ifeq ($(OS),Windows_NT)
MAVEN_WRAPPER := backend/mvnw.cmd
else
MAVEN_WRAPPER := ./backend/mvnw
endif

MAVEN_FLAGS ?= --batch-mode --no-transfer-progress
NPM ?= npm
CURL ?= curl
STARTUP_TIMEOUT_SECONDS ?= 90
JAVA_COMMAND := $(if $(strip $(JAVA_HOME)),$(JAVA_HOME)/bin/java,java)

BACKEND_PORT ?= 8080
ENTRA_UI_PORT ?= 5173
LOCAL_ISSUER_PORT ?= 9090
LOCAL_UI_PORT ?= 5174
LOCAL_TOKEN_AUDIENCE ?= local-spring-api
VITE_LOG_LEVEL ?= debug

BACKEND_URL ?= http://localhost:$(BACKEND_PORT)
ENTRA_UI_ORIGIN ?= http://localhost:$(ENTRA_UI_PORT)
LOCAL_ISSUER_URI ?= http://localhost:$(LOCAL_ISSUER_PORT)
LOCAL_TEST_UI_ORIGIN ?= http://localhost:$(LOCAL_UI_PORT)

export BACKEND_PORT
export ENTRA_TENANT_ID ENTRA_API_CLIENT_ID ENTRA_SPA_CLIENT_ID
export LOCAL_ISSUER_PORT LOCAL_ISSUER_URI LOCAL_TOKEN_AUDIENCE LOCAL_TEST_UI_ORIGIN
export NVD_API_KEY

define require-variable
$(if $(strip $($1)),,$(error Missing $1. Copy .env.example to .env and provide the value))
endef

define wait-for-url
	@attempt=0; \
	until $(CURL) --fail --silent "$(1)" >/dev/null 2>&1; do \
		attempt=$$((attempt + 1)); \
		if [ "$$attempt" -ge "$(STARTUP_TIMEOUT_SECONDS)" ]; then \
			echo "Timed out after $(STARTUP_TIMEOUT_SECONDS) seconds waiting for $(1)"; \
			exit 1; \
		fi; \
		sleep 1; \
	done
	@echo "Ready: $(1)"
endef

.PHONY: help setup init-env doctor show-config install \
	local entra run-local-issuer run-local-backend run-local-ui \
	run-entra-backend run-entra-ui check-entra-config \
	wait-local-issuer wait-backend _run-local-backend-when-ready \
	_run-local-ui-when-ready _run-entra-ui-when-ready \
	verify check verify-backend verify-frontend verify-local-issuer verify-local-ui \
	coverage format format-check generate-openapi \
	security security-java security-node security-backend security-local-issuer \
	security-frontend security-local-ui sbom clean

help: ## Show the available developer commands.
	@echo "Entra SSO React Spring Reference"
	@echo ""
	@echo "First-time setup:"
	@echo "  make setup              Check tools, create .env, and install Node dependencies"
	@echo ""
	@echo "Run modes:"
	@echo "  make local              Start issuer, backend, and local test UI in readiness order"
	@echo "  make entra              Start the Entra backend, then the production React UI"
	@echo "  make run-local-issuer   Start only the local JWT issuer on port $(LOCAL_ISSUER_PORT)"
	@echo "  make run-local-backend  Start only the backend configured for the local issuer"
	@echo "  make run-local-ui       Start only the local token test UI on port $(LOCAL_UI_PORT)"
	@echo "  make run-entra-backend  Start only the backend configured for Microsoft Entra"
	@echo "  make run-entra-ui       Start only the production React/MSAL UI on port $(ENTRA_UI_PORT)"
	@echo ""
	@echo "Quality:"
	@echo "  make verify             Run all Java and React quality gates"
	@echo "  make coverage           Run both Java coverage gates and generate HTML reports"
	@echo "  make format             Format both Java and React applications"
	@echo "  make format-check       Check formatting without changing files"
	@echo "  make generate-openapi   Regenerate backend sources from the OpenAPI contract"
	@echo ""
	@echo "Dependency security (network-backed):"
	@echo "  make security           Scan all dependencies and generate all SBOMs"
	@echo "  make security-java      Scan the backend and local issuer"
	@echo "  make security-node      Audit both React applications"
	@echo "  make sbom               Generate Java and Node CycloneDX SBOMs without audits"
	@echo ""
	@echo "Utilities:"
	@echo "  make doctor             Display and validate the required toolchain"
	@echo "  make show-config        Display effective non-secret local configuration"
	@echo "  make clean              Remove generated build output"
	@echo "  make help               Show this help"

init-env: .env ## Create the ignored root .env file without overwriting an existing one.

.env:
	@cp .env.example .env
	@echo "Created .env from .env.example. Entra mode requires the three Entra IDs to be filled in."

setup: init-env ## Prepare a new checkout for development.
	@$(MAKE) --no-print-directory doctor
	@$(MAKE) --no-print-directory install
	@echo "Setup complete. Run 'make local', or update .env and run 'make entra'."

doctor: ## Display the installed development toolchain and fail if a command is unavailable.
	@echo "Java:"
	@"$(JAVA_COMMAND)" -version
	@java_major=$$("$(JAVA_COMMAND)" -version 2>&1 | awk -F '"' '/version/ { split($$2, parts, "."); print parts[1]; exit }'); \
		if [ "$$java_major" != "25" ]; then \
			echo "Java 25 is required, but Java $$java_major is active. Set JAVA_HOME to JDK 25."; \
			exit 1; \
		fi
	@echo ""
	@echo "Maven Wrapper:"
	@$(MAVEN_WRAPPER) --version
	@echo ""
	@echo "Node.js:"
	@node --version
	@node_version=$$(node --version | sed 's/^v//'); \
		node_major=$$(echo "$$node_version" | cut -d. -f1); \
		node_minor=$$(echo "$$node_version" | cut -d. -f2); \
		if [ "$$node_major" -lt 22 ] || { [ "$$node_major" -eq 22 ] && [ "$$node_minor" -lt 12 ]; }; then \
			echo "Node.js 22.12 or newer is required, but Node.js $$node_version is active."; \
			exit 1; \
		fi
	@echo "npm:"
	@$(NPM) --version
	@echo "curl:"
	@$(CURL) --version

show-config: ## Show effective ports and whether optional/required identifiers are configured.
	@echo "Backend URL:          $(BACKEND_URL)"
	@echo "Entra UI origin:      $(ENTRA_UI_ORIGIN)"
	@echo "Local issuer URI:     $(LOCAL_ISSUER_URI)"
	@echo "Local UI origin:      $(LOCAL_TEST_UI_ORIGIN)"
	@echo "Local audience:       $(LOCAL_TOKEN_AUDIENCE)"
	@echo "Entra tenant ID:      $(if $(strip $(ENTRA_TENANT_ID)),configured,not configured)"
	@echo "Entra API client ID:  $(if $(strip $(ENTRA_API_CLIENT_ID)),configured,not configured)"
	@echo "Entra SPA client ID:  $(if $(strip $(ENTRA_SPA_CLIENT_ID)),configured,not configured)"
	@echo "NVD API key:          $(if $(strip $(NVD_API_KEY)),configured,not configured (optional))"

install: ## Install the exact Node dependencies from both lockfiles.
	@$(NPM) --prefix frontend ci
	@$(NPM) --prefix local-testing/token-test-ui ci

check-entra-config:
	$(call require-variable,ENTRA_TENANT_ID)
	$(call require-variable,ENTRA_API_CLIENT_ID)
	$(call require-variable,ENTRA_SPA_CLIENT_ID)
	@echo "Entra identifiers are configured."

local: ## Start all local-JWT components; press Ctrl+C to stop them.
	@echo "Starting local JWT issuer -> backend -> token test UI. Logs will be interleaved."
	@$(MAKE) --no-print-directory --jobs=3 \
		run-local-issuer _run-local-backend-when-ready _run-local-ui-when-ready

run-local-issuer: ## Start only the isolated local JWT issuer.
	@$(MAVEN_WRAPPER) -f local-testing/mock-jwt-issuer/pom.xml spring-boot:run

run-local-backend: export SPRING_PROFILES_ACTIVE := entra
run-local-backend: export OAUTH2_ISSUER_URI := $(LOCAL_ISSUER_URI)
run-local-backend: export OAUTH2_AUDIENCE := $(LOCAL_TOKEN_AUDIENCE)
run-local-backend: export UI_ORIGIN := $(LOCAL_TEST_UI_ORIGIN)
run-local-backend: ## Start only the production backend against the local JWT issuer.
	@$(MAVEN_WRAPPER) -f backend/pom.xml spring-boot:run

run-local-ui: export VITE_LOCAL_ISSUER_URL := $(LOCAL_ISSUER_URI)
run-local-ui: export VITE_API_BASE_URL := $(BACKEND_URL)
run-local-ui: ## Start only the isolated local token test UI.
	@$(NPM) --prefix local-testing/token-test-ui run dev -- --host 127.0.0.1 --port $(LOCAL_UI_PORT)

wait-local-issuer:
	$(call wait-for-url,$(LOCAL_ISSUER_URI)/.well-known/openid-configuration)

wait-backend:
	$(call wait-for-url,$(BACKEND_URL)/actuator/health)

_run-local-backend-when-ready:
	@$(MAKE) --no-print-directory wait-local-issuer
	@$(MAKE) --no-print-directory run-local-backend

_run-local-ui-when-ready:
	@$(MAKE) --no-print-directory wait-backend
	@$(MAKE) --no-print-directory run-local-ui

entra: check-entra-config ## Start the Entra backend and production UI; press Ctrl+C to stop them.
	@echo "Starting Entra backend, then the production React UI. Logs will be interleaved."
	@$(MAKE) --no-print-directory --jobs=2 run-entra-backend _run-entra-ui-when-ready

run-entra-backend: export SPRING_PROFILES_ACTIVE := entra
run-entra-backend: export OAUTH2_ISSUER_URI := https://login.microsoftonline.com/$(ENTRA_TENANT_ID)/v2.0
run-entra-backend: export OAUTH2_AUDIENCE := $(ENTRA_API_CLIENT_ID)
run-entra-backend: export UI_ORIGIN := $(ENTRA_UI_ORIGIN)
run-entra-backend: check-entra-config ## Start only the backend configured for Microsoft Entra.
	@$(MAVEN_WRAPPER) -f backend/pom.xml spring-boot:run

run-entra-ui: export VITE_ENTRA_TENANT_ID := $(ENTRA_TENANT_ID)
run-entra-ui: export VITE_ENTRA_SPA_CLIENT_ID := $(ENTRA_SPA_CLIENT_ID)
run-entra-ui: export VITE_ENTRA_API_CLIENT_ID := $(ENTRA_API_CLIENT_ID)
run-entra-ui: export VITE_API_BASE_URL := $(BACKEND_URL)
run-entra-ui: export VITE_LOG_LEVEL := $(VITE_LOG_LEVEL)
run-entra-ui: check-entra-config ## Start only the production React/MSAL UI.
	@$(NPM) --prefix frontend run dev -- --host 127.0.0.1 --port $(ENTRA_UI_PORT)

_run-entra-ui-when-ready:
	@$(MAKE) --no-print-directory wait-backend
	@$(MAKE) --no-print-directory run-entra-ui

verify: ## Run every deterministic Java and React quality gate in CI order.
	@$(MAKE) --no-print-directory verify-backend
	@$(MAKE) --no-print-directory verify-local-issuer
	@$(MAKE) --no-print-directory verify-frontend
	@$(MAKE) --no-print-directory verify-local-ui

check: verify ## Alias for make verify.

verify-backend: ## Test and statically analyze the backend with 85% coverage enforcement.
	@$(MAVEN_WRAPPER) -f backend/pom.xml $(MAVEN_FLAGS) clean verify

verify-local-issuer: ## Test and statically analyze the local issuer with 85% coverage enforcement.
	@$(MAVEN_WRAPPER) -f local-testing/mock-jwt-issuer/pom.xml $(MAVEN_FLAGS) clean verify

verify-frontend: ## Install, format-check, type-check, and build the production React UI.
	@$(NPM) --prefix frontend ci
	@$(NPM) --prefix frontend run check

verify-local-ui: ## Install, format-check, type-check, and build the local token UI.
	@$(NPM) --prefix local-testing/token-test-ui ci
	@$(NPM) --prefix local-testing/token-test-ui run check

coverage: ## Enforce Java coverage and generate both JaCoCo HTML reports.
	@$(MAKE) --no-print-directory verify-backend
	@$(MAKE) --no-print-directory verify-local-issuer
	@echo "Backend report:     backend/target/site/jacoco/index.html"
	@echo "Local issuer report: local-testing/mock-jwt-issuer/target/site/jacoco/index.html"

format: ## Apply Java and React formatting.
	@$(MAVEN_WRAPPER) -f backend/pom.xml spotless:apply
	@$(MAVEN_WRAPPER) -f local-testing/mock-jwt-issuer/pom.xml spotless:apply
	@$(NPM) --prefix frontend run format
	@$(NPM) --prefix local-testing/token-test-ui run format

format-check: ## Check Java and React formatting without modifying files.
	@$(MAVEN_WRAPPER) -f backend/pom.xml spotless:check
	@$(MAVEN_WRAPPER) -f local-testing/mock-jwt-issuer/pom.xml spotless:check
	@$(NPM) --prefix frontend run format:check
	@$(NPM) --prefix local-testing/token-test-ui run format:check

generate-openapi: ## Validate the API contract and regenerate backend OpenAPI sources.
	@$(MAVEN_WRAPPER) -f backend/pom.xml generate-sources

security: ## Run all network-backed dependency scans and generate all SBOMs.
	@echo "Security scans contact external services and may send dependency metadata; source files are not uploaded."
	@$(MAKE) --no-print-directory security-java
	@$(MAKE) --no-print-directory security-node

security-java: ## Run OWASP Dependency-Check for both Java applications.
	@$(MAKE) --no-print-directory security-backend
	@$(MAKE) --no-print-directory security-local-issuer

security-backend: ## Scan backend dependencies and generate its CycloneDX SBOM.
	@$(MAVEN_WRAPPER) -f backend/pom.xml $(MAVEN_FLAGS) -Psecurity verify

security-local-issuer: ## Scan local issuer dependencies and generate its CycloneDX SBOM.
	@$(MAVEN_WRAPPER) -f local-testing/mock-jwt-issuer/pom.xml $(MAVEN_FLAGS) -Psecurity verify

security-node: ## Run npm audit and generate SBOMs for both React applications.
	@$(MAKE) --no-print-directory security-frontend
	@$(MAKE) --no-print-directory security-local-ui

security-frontend: ## Audit production UI dependencies and write frontend/sbom.cdx.json.
	@$(NPM) --prefix frontend ci
	@$(NPM) --prefix frontend run security:audit
	@$(NPM) --prefix frontend run security:sbom --silent > frontend/sbom.cdx.json

security-local-ui: ## Audit local UI dependencies and write its sbom.cdx.json.
	@$(NPM) --prefix local-testing/token-test-ui ci
	@$(NPM) --prefix local-testing/token-test-ui run security:audit
	@$(NPM) --prefix local-testing/token-test-ui run security:sbom --silent \
		> local-testing/token-test-ui/sbom.cdx.json

sbom: ## Generate CycloneDX dependency inventories without vulnerability audits.
	@$(MAVEN_WRAPPER) -f backend/pom.xml $(MAVEN_FLAGS) -Psecurity -DskipTests package
	@$(MAVEN_WRAPPER) -f local-testing/mock-jwt-issuer/pom.xml $(MAVEN_FLAGS) -Psecurity -DskipTests package
	@$(NPM) --prefix frontend run security:sbom --silent > frontend/sbom.cdx.json
	@$(NPM) --prefix local-testing/token-test-ui run security:sbom --silent \
		> local-testing/token-test-ui/sbom.cdx.json
	@echo "Generated target/sbom.json for both Java apps and sbom.cdx.json for both React apps."

clean: ## Remove Maven, Vite, coverage, and generated SBOM output.
	@$(MAVEN_WRAPPER) -f backend/pom.xml clean
	@$(MAVEN_WRAPPER) -f local-testing/mock-jwt-issuer/pom.xml clean
	@$(RM) -r frontend/dist local-testing/token-test-ui/dist
	@$(RM) -r frontend/coverage local-testing/token-test-ui/coverage
	@$(RM) frontend/sbom.cdx.json local-testing/token-test-ui/sbom.cdx.json
