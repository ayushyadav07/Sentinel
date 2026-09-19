# Sentinel — Autonomous Vulnerability Remediation Agent

Sentinel scans a repository for dependency vulnerabilities and autonomously decides what to do
about them: if nothing serious is found, it does nothing; if it finds CRITICAL/HIGH severity
issues, it raises a pull request that fixes the safely-patchable ones and files a tracking issue
for anything that needs a human. The decision of *which tools to call and when* is made by
Gemini itself, not by hardcoded Java control flow — Sentinel is a genuine tool-calling agent,
not a fixed pipeline with an LLM bolted on for text generation.

```
                                                         RabbitMQ (work queue, one job at a time)
                                                                        │
                                                                        ▼
┌───────────────┐   scan.jobs.queue    ┌──────────────────────────────────────┐
│  api-gateway    │ ───────────────────▶ │  orchestrator-worker                  │
│  (port 8080)    │                     │  (port 8083)                           │
│                 │                     │  prefetch=1, concurrency=1              │
│  POST           │                     │                                        │
│  /scans/trigger  │                     │  AgenticRemediationAgent (Gemini)      │
│  (manual entry   │                     │  holds ALL 4 MCP tools below and        │
│  point)          │                     │  decides itself which to call, when,    │
└───────────────┘                     │  and with what arguments -- governed    │
                                       │  only by a system-prompt policy, not    │
                                       │  by Java if/else logic.                 │
                                       │                                         │
                                       │  Persists a ScanRun row (incl. the       │
                                       │  agent's full transcript) to Postgres.  │
                                       └───────────┬──────────────┬─────────────┘
                                                    │              │
                                     ┌──────────────▼───┐   ┌──────▼────────────────────┐
                                     │  scanner-mcp        │   │  remediation-mcp            │
                                     │  (port 8081)         │   │  (port 8082)                 │
                                     │  wraps Grype          │   │  wraps GitHub REST API       │
                                     │                       │   │                              │
                                     │  tools:               │   │  tools:                      │
                                     │  - scan_repository     │   │  - raise_fix_pr               │
                                     │  - get_severity_summary│   │  - send_email_alert           │
                                     └───────────────────────┘   │  (email is fallback-only)     │
                                                                  └──────────────────────────────┘
```

## How the agentic flow actually works

`AgenticRemediationAgent` builds a single `ChatClient` and hands it **all four MCP tools** —
`scan_repository` / `get_severity_summary` from `scanner-mcp`, `raise_fix_pr` / `send_email_alert`
from `remediation-mcp` — via Spring AI's `SyncMcpToolCallbackProvider`. A system prompt gives
Gemini its operating policy in plain language (always scan first; only remediate on CRITICAL/HIGH;
email is a last resort, not a default channel; never repeat an identical tool call). Spring AI's
`ChatClient` then runs the underlying tool-call loop automatically: the model asks for a tool, Spring
AI executes it for real against the live MCP server, feeds the result back, and this repeats until
the model is done and produces a final answer.

That final answer is required (by the same system prompt) to end with a fenced JSON block
summarizing what happened (counts by severity, whether action was taken, what action, and the
resulting PR/issue URL). `ScanJobListener` parses that block to populate a `ScanRun` row — and
also stores the agent's entire raw response in `agent_transcript`, so every decision the agent
made is auditable even though the decision itself lived in a prompt, not in unit-tested Java.

**Deliberate trade-off:** this hands "should we act on these findings" to the LLM's own reasoning
— more genuinely autonomous, but the gate is a prompt instead of a unit-tested method (contrast
with `PatchClassifier` below, which stays deterministic on purpose). `SeverityGate` is still
present in the codebase for reference/testing but is no longer on the active call path.

## Why Grype, not Trivy

`scanner-mcp` wraps **Grype**. Grype's Java/Node/Python/etc. catalogers don't attempt live
dependency-resolution network calls by default (Trivy's do, to compute BOM-inherited versions) —
so Grype degrades gracefully on manifests it can't fully resolve instead of failing the whole scan
outright. For an unattended agent that can't have a human standing by to retry a rate-limited
public registry call, failing soft beats failing hard.

## Multi-ecosystem remediation

`DependencyPatcher` (in `remediation-mcp`) can apply a safe, same-major-version dependency bump
directly in the manifest, across **7 ecosystems**:

| Ecosystem | File |
|---|---|
| Java (Maven) | `pom.xml` |
| Java (Gradle) | `build.gradle` |
| Python | `requirements.txt` |
| Node.js | `package.json` |
| Go | `go.mod` |
| Ruby | `Gemfile`, `Gemfile.lock` |
| PHP | `composer.json`, `composer.lock` |

`RemediationTools` dispatches to the right patcher based on the finding's manifest path. Whatever
the ecosystem, the same safety rule applies: `PatchClassifier` only allows an automatic bump when
the fix is a same-major-version upgrade (deterministic semver comparison, unit-tested). A major
version bump, an unparseable version, or "no fix published yet" always routes to a GitHub issue
instead of a PR — regardless of which agent or tool decided to try.

## Repository layout

```
common/                 Shared model + DTOs (Severity, Vulnerability, ScanReport, queue messages)
scanner-mcp/            MCP server wrapping Grype -- scan_repository, get_severity_summary
remediation-mcp/        MCP server wrapping GitHub REST -- raise_fix_pr, send_email_alert
                        DependencyPatcher supports 7 ecosystems (see above)
api-gateway/            Receives POST /scans/trigger -> publishes ScanJobMessage to RabbitMQ
orchestrator-worker/    Consumes jobs one at a time; AgenticRemediationAgent (Gemini) decides
                        tool sequencing; persists ScanRun (+ full agent transcript) to Postgres
docker-compose.yml      RabbitMQ + Postgres + all four services
.env.example            Copy to .env and fill in before `docker compose up`
```

## Running it locally

### Prerequisites
- Java 21, Maven 3.9+, Docker
- A free Gemini API key from [Google AI Studio](https://aistudio.google.com/apikey)
- A GitHub Personal Access Token (fine-grained: `contents:write`, `pull_requests:write`,
  `issues:write` on the target repo) — passed per-request, not stored as an env var

### 1. Configure
```bash
cp .env.example .env
# fill in GEMINI_API_KEY at minimum; SMTP_* only needed to test the email fallback path
```

### 2. Build and run
```bash
mvn clean package -DskipTests
docker compose up --build
```

### 3. Trigger a scan
```bash
curl -X POST http://localhost:8080/scans/trigger \
  -H "Content-Type: application/json" \
  -d '{
    "repoUrl": "https://github.com/YOUR_USERNAME/YOUR_REPO",
    "branch": "main",
    "installationToken": "ghp_yourPersonalAccessToken"
  }'
```

Watch `docker compose logs -f orchestrator-worker` for the agent's tool-call sequence, and query
`scan_run` in Postgres (`docker exec -it <postgres-container> psql -U sentinel -d sentinel`) for
the persisted outcome, including the full `agent_transcript`.

## Testing

- `orchestrator-worker`: `SeverityGateTest` — kept for reference even though the gate is no
  longer on the active path.
- `remediation-mcp`: `PatchClassifierTest` — every branch of the auto-patch-vs-human-review
  safety gate. This one *is* still load-bearing, regardless of ecosystem or agent behavior.

## Known gaps / honest limitations

- **The severity/remediation decision is now prompt-governed, not unit-tested.** If Gemini
  deviates from the stated policy (e.g. calls `raise_fix_pr` on LOW/MEDIUM-only findings, or
  skips scanning), nothing in Java stops it — only `PatchClassifier`'s semver safety net inside
  `remediation-mcp` still applies regardless of what the agent asks for.
- **Agent output parsing is best-effort.** If the model doesn't end its response with a valid
  fenced JSON block, `ScanJobListener` falls back to storing the raw transcript with a generic
  `COMPLETED_CLEAN` status rather than accurate counts — check `agent_transcript` directly in
  that case.
- **`DependencyPatcher` is regex-based per ecosystem**, not a full AST/parser per language —
  intentional (minimal, human-reviewable diffs) but unusual manifest formatting can fail to match
  and will correctly fall through to a tracking issue rather than a corrupted file.
- **Grype still needs network access for its own vulnerability database**, just not for
  per-dependency version resolution — an outage of Grype's DB source will still fail a scan.
