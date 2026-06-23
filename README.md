# ACME Motor Quote Assistant — POC

Independent R&D prototype. **Synthetic mock data only. Not connected to any real ACME system.**

A conversational motor-insurance quoting demo where an LLM does **form-filling only**
— it collects the parameters a quote needs (and can read them from an uploaded
document) — while **all pricing is served by a mocked ACME API behind an MCP server**.
Supports **GB** and **FR** via a dynamic, region-aware schema. See `ARCHITECTURE.md`
for the full design and `docs/` for the spec, build plan, and open questions for ACME.

## Components
- `frontend/` — React + Vite + TS chat web app: document upload, confirmation card, quote + handoff link.
- `backend/` — FastAPI form-filling backend: runs the LLM(s) and document extraction, talks to the MCP server. **Never prices anything itself.** Endpoints: `/chat` (SSE), `/upload`, `/confirm`, `/health`.
- `mcp-server/` — deterministic, LLM-free **MCP server** (the core artifact): `get_quote_schema`, `lookup_vehicle`, `submit_quote_request`, `create_handoff_link`, plus a GUID-protected `/handoff/{guid}` page.
- `mock-acme/` — **WireMock** config standing in for ACME's vehicle + quote APIs (GB + FR). Config only, no code.
- `docs/` — design spec, build plan, `open-questions-for-acme.md`.

## Architecture (one line)
`Chat UI → backend (form-filling LLM) → MCP server → mock ACME (WireMock, prices the quote) → GUID handoff page`

## Running the full stack locally (offline — no API key needed)

Four processes. The offline mode (`MOCK_LLM=1`) uses deterministic extraction/form-filling but still drives the **real** MCP → WireMock chain via `QUOTE_SERVICE=mcp`.

**1) Mock ACME (WireMock)** — either Docker:
```bash
cd mock-acme
docker run --rm -p 8080:8080 -v "$PWD/mappings:/home/wiremock/mappings" \
  wiremock/wiremock:3.9.1 --global-response-templating
```
…or the standalone jar (no Docker; download once from Maven Central — see `mock-acme/README.md`):
```bash
java -jar mock-acme/wiremock-standalone-3.9.1.jar \
  --root-dir mock-acme --global-response-templating --port 8080
```

**2) MCP server** (serves the tools + the `/handoff` page on :8090):
```bash
cd mcp-server
ACME_BASE_URL=http://localhost:8080 PUBLIC_BASE_URL=http://localhost:8090 \
  uv run python -m app.server
```

**3) Form-filling backend** (:8000):
```bash
cd backend
MOCK_LLM=1 QUOTE_SERVICE=mcp MCP_URL=http://localhost:8090/mcp \
  uv run uvicorn app.api.main:app --port 8000
```

**4) Frontend** (:5173):
```bash
cd frontend
npm install   # first time only
npm run dev
```

Open the printed localhost URL. Upload a document (a renewal notice → GB; a *carte
grise* → FR) or just chat, review the **confirmation card**, confirm, and you get a
quote with a **Continue to ACME →** handoff link. Verified end-to-end: GB ≈ £401.28,
FR ≈ €340.47 (priced by WireMock, not the AI).

**Live LLM mode:** set `OPENAI_API_KEY` and omit `MOCK_LLM`. The backend then uses
OpenAI for document extraction + form-filling; `QUOTE_SERVICE` defaults to `mcp`.

## Tests
```bash
cd mcp-server && uv run pytest -q   # MCP server (units + live-WireMock integration)
cd backend    && uv run pytest -q   # form-filling backend
cd frontend   && npm run test        # frontend smoke test
```
The MCP↔WireMock integration test auto-skips unless Java + the WireMock jar are present, so it stays out of lightweight CI.

## Docs
- `ARCHITECTURE.md` — architecture, request flow, GB/FR schema, comparison to `architecture.png`
- `docs/superpowers/specs/2026-06-23-acme-motor-quote-poc-design.md` — design spec
- `docs/superpowers/plans/2026-06-23-acme-motor-quote-poc.md` — build plan
- `docs/open-questions-for-acme.md` — open questions to confirm with ACME
