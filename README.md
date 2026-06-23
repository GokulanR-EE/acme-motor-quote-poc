# ACME Motor Quote Assistant — POC

Independent R&D prototype. **Synthetic mock data only. Not connected to any real ACME system.**

A conversational motor-insurance quoting demo. The codebase is evolving from an
AI-driven quoting prototype toward an AI **form-filling** design, where all quoting
is served by a mocked ACME API behind an MCP server. See the design spec and build
plan in `docs/superpowers/`.

## Current layout
- `backend/` — FastAPI app: pricing engine, mock vehicle/risk services, and an
  OpenAI (or offline `MOCK_LLM`) chat agent. Endpoints: `/chat` (SSE), `/reprice`, `/health`.
- `frontend/` — React + Vite + TypeScript chat web app.
- `docs/superpowers/` — design specs and implementation plans (AXA-era origin and
  the current ACME direction).

## Running locally (offline — no API key needed)

Backend (terminal 1):
```bash
cd backend
MOCK_LLM=1 uv run uvicorn app.api.main:app --reload --port 8000
```

Frontend (terminal 2):
```bash
cd frontend
npm install   # first time only
npm run dev
```

Open the printed localhost URL and type, for example:
`I drive AB12CDE, age 34, 5 years NCB, SW1A 1AA`
— a quote card appears; the excess slider and cover-tier toggle re-price live.

**Live LLM mode:** set `OPENAI_API_KEY` and omit `MOCK_LLM` in the backend.

## Tests
```bash
cd backend && uv run pytest -q     # backend test suite
cd frontend && npm run test         # frontend smoke test
```

## Design & plan
- Current direction (ACME): `docs/superpowers/specs/2026-06-23-acme-motor-quote-poc-design.md`
- Build plan: `docs/superpowers/plans/2026-06-23-acme-motor-quote-poc.md`
