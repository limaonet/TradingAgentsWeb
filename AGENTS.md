# AGENTS.md

## Cursor Cloud specific instructions

### Project overview

TradingAgents Web is an AI-powered stock trading analysis platform (Chinese A-shares / Hong Kong stocks). Monorepo with two services:

| Service | Path | Port | Stack |
|---|---|---|---|
| Backend | `tradingagents-server/` | 8080 | Spring Boot 3.2, Java 17, LangChain4j, Maven |
| Frontend | `tradingagents-ui/` | 5173 (dev) | Vue 3, TypeScript, Vite, Ant Design Vue |

No database is required; results are stored on the filesystem. Redis is optional.

### Prerequisites

- **Java 17** (`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`). Java 21 is also installed on the VM — ensure `java -version` shows 17.
- **Maven 3.8+** (system package `maven`).
- **Node.js >=20.19 or >=22.12** (pre-installed on the VM as v22).

### Running services

See `README.md` for the canonical commands. Key notes:

- **Backend**: `cd tradingagents-server && JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn spring-boot:run -DskipTests`
  - Starts on port 8080. LLM features require API keys (see `.env.example`), but the server starts and serves the REST API without them.
- **Frontend**: `cd tradingagents-ui && npm run dev`
  - Starts Vite dev server on port 5173. Proxies `/api` and `/ws` to `localhost:8080`.

### Lint / Type-check / Test

- **Frontend lint**: `cd tradingagents-ui && npm run lint` — runs oxlint then eslint (with `--fix`). The codebase has pre-existing lint errors (unused vars, `no-explicit-any`, `no-new-array`).
- **Frontend type-check**: `cd tradingagents-ui && npx vue-tsc --build` — passes cleanly.
- **Backend tests**: `cd tradingagents-server && JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn test`
- **Backend compile only**: `cd tradingagents-server && JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn compile -DskipTests`

### Gotchas

- `package-lock.json` is in `.gitignore`, so `npm install` resolves fresh each time. Use `npm install` (not `npm ci`).
- The project uses `oxlint` + `eslint` in sequence via `npm-run-all2`. The `lint` script applies `--fix` by default.
- Maven first-run downloads ~100 MB of dependencies; subsequent runs use the local `~/.m2` cache.
- The backend Spring Boot app starts even without LLM API keys — the keys are only needed at analysis runtime.
