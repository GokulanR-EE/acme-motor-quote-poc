# ACME Motor Quote Assistant — Architecture

> **Status:** POC / R&D prototype. **Synthetic mock data only — not connected to any real ACME system.**
> The AI does **form-filling only**; all quoting is served by a deterministic mock of ACME's APIs.

This document explains the architecture, the design decisions behind it, the MCP
tool contracts, and how the build maps to the reference diagram (`architecture.png`).
For the full design rationale see
[`docs/superpowers/specs/2026-06-23-acme-motor-quote-poc-design.md`](docs/superpowers/specs/2026-06-23-acme-motor-quote-poc-design.md);
for the task-by-task build plan see
[`docs/superpowers/plans/2026-06-23-acme-motor-quote-poc.md`](docs/superpowers/plans/2026-06-23-acme-motor-quote-poc.md).

---

## 1. Objective

Demonstrate **AI-assisted motor-insurance quote form-filling**: a user describes
their situation in natural language (and can upload a document); an LLM extracts
and collects the parameters a quote needs; **all pricing/quoting logic lives in
ACME's APIs** (mocked deterministically), never in the AI. The reusable core is an
**MCP server** — the durable protocol layer between the chat host and the ACME backend.

This codebase began as an AI-driven quoting prototype (`backend/`, AXA-branded) and
is evolving toward this form-filling design; the legacy app still runs while the new
architecture is layered on.

---

## 2. Architecture at a glance

```
Chat host (LLM)                Web backend (FastAPI)            MCP server (deterministic)      Mock ACME (WireMock)
───────────────                ─────────────────────            ──────────────────────────      ────────────────────
Standalone branded     ──▶     form-filling LLM        ──▶      get_quote_schema(country)  ──▶   (rules per country)
chat web app                   document-extraction LLM          lookup_vehicle(id,country) ──▶   /{cc}/vehicles/{id}
 + document upload             MCP client                       submit_quote_request(...)  ──▶   /{cc}/quotes (prices)
 + confirmation UI                                              create_handoff_link(quote)
                                                                /handoff/{guid}  ◀── browser opens the GUID link
```

**Key principle — LLMs only in the web backend.** The MCP server is **LLM-free and
deterministic**: strict pydantic validation at the boundary, no model server-side.
Document text is treated as **data, never instructions**. This keeps the
prompt-injection surface on the protocol layer minimal.

---

## 3. Request flow (six phases)

1. **Initiation & inference** — user uploads a document (or just types). The
   web-backend extraction LLM reads it and **infers the country** (e.g. a French
   *Carte Grise* → `FR`; otherwise default `GB`).
2. **Dynamic schema fetch (2-step pattern)** — the host calls
   `get_quote_schema(country_code)`; the MCP server returns the **region-specific
   field + document list** so the LLM knows exactly what to collect.
3. **Interview & data compilation** — the LLM maps extracted data to the schema and
   asks for any missing fields, normalising vague input ("about 2 years ago" → a date).
4. **Confirmation loop (critical)** — the full collected dataset is shown to the
   customer to confirm/correct before anything is priced. **Accuracy stays with the customer.**
5. **Quote generation & redirect creation** — `submit_quote_request(country, data)`
   validates the data against the country model, forwards it to mock ACME for
   pricing, then `create_handoff_link` mints a **non-enumerable GUID** checkout URL.
6. **Final result display** — the host shows the premium and a button to the
   GUID-protected ACME handoff page.

---

## 4. The dynamic-schema (2-step) pattern — GB + FR

The MCP server tells the host *which fields to collect per country*, rather than the
host hard-coding them. Supported countries: **GB** (default) and **FR**.

| | **GB** (GBP) | **FR** (EUR) |
|---|---|---|
| Vehicle id | `registration` | `immatriculation` (Carte Grise) |
| Driver | `full_name`, `date_of_birth`, `postcode` | `full_name`, `date_of_birth`, `code_postal` |
| No-claims rating | `ncb_years` (0–20) | `bonus_malus` coefficient (0.50–3.50) |
| Cover | `cover_tier` (comprehensive / TPFT / TPO) | `formule` (tous_risques / tiers_plus / au_tiers) |
| Excess | `voluntary_excess` £ (0,100,250,500,750,1000) | `franchise` € (0,150,300,500,800) |
| Documents | driving licence, renewal notice | carte grise, permis de conduire |

An unsupported country returns `{country, supported: ["GB","FR"], error: "unsupported_country"}`
so the host can route the user elsewhere (e.g. the ACME website).

GB pricing inputs use a **derived age** (computed from date of birth — normalisation,
not pricing); FR rates on the **bonus-malus coefficient**, not age.

---

## 5. MCP server — tool reference (the core artifact)

`mcp-server/` is a standalone, deterministic Python MCP server (FastMCP, streamable-HTTP,
port 8090). It contains **no pricing logic** — it validates input and forwards to ACME.

| Tool | Input | Returns |
|---|---|---|
| `get_quote_schema` | `country_code="GB"` | `{country, currency, documents, fields[]}` (or `unsupported_country`) |
| `lookup_vehicle` | `identifier, country_code="GB"` | `{found, country_code, …vehicle}` |
| `submit_quote_request` | `country_code, data` | `Quote` `{quote_ref, currency, annual_premium, monthly_premium, country_code, input}` |
| `create_handoff_link` | `quote` | `{guid, handoff_url}` |

Plus an HTTP route `GET /handoff/{guid}` that renders the stored quote as an
HTML page (all interpolated values HTML-escaped).

**Modules:** `models.py` (country-specific pydantic models), `schemas.py` (static
GB/FR schemas), `acme_client.py` (transport-only HTTP client to ACME), `server.py`
(tool orchestration + handoff page), `store.py` (in-memory GUID→quote store).

---

## 6. Quoting ownership & the ACME mock

All premiums come from the **mock ACME API** (deterministic, WireMock — config only,
no AI-generated code). The MCP server POSTs a country-specific payload to
`/{cc}/quotes` and parses the returned premium; it only does presentation rounding
(2 dp annual, monthly = annual/12). This preserves the design rule: **the AI never
prices; a deterministic ACME service does.**

---

## 7. Security & isolation posture (POC-appropriate)

- **ACME isolation:** synthetic/public data only; no real ACME systems, data, or pricing.
- **MCP server is LLM-free + deterministic;** input validated by pydantic at the boundary.
- **Uploaded document text treated as data, not instructions** (prompt-injection mitigation).
- **GUID handoff links are random uuid4s** (non-enumerable), defeating ID-guessing.
- **No raw documents stored** — extraction yields structured fields, the raw file is discarded.
- Production concerns (PII to LLM provider, OAuth2/session tokens + expiry, GDPR,
  multi-document conflict resolution, server-side AI hardening) are **documented open
  questions for ACME**, not implemented in this POC.

---

## 8. How this compares to `architecture.png`

The reference diagram and this build agree on the core chain:

| Aspect | `architecture.png` | This build |
|---|---|---|
| MCP as protocol layer between LLM and ACME | ✅ | ✅ |
| ACME owns quoting/rules | ✅ | ✅ (mocked) |
| Dynamic schema fetch (2-step pattern) | ✅ | ✅ (`get_quote_schema`, GB + FR) |
| Confirmation loop before quoting | ✅ | ✅ |
| GUID-protected checkout/handoff URL | ✅ | ✅ |
| Document OCR / extraction | ChatGPT lane | web-backend extraction LLM |
| **Host** | **ChatGPT (Apps host)** | **standalone branded web app** *(deliberate POC choice)* |

**Divergence:** the diagram uses **ChatGPT as host**; for this POC we deliberately
use a **standalone branded web app** (full UI control; avoids all customer PII entering
ChatGPT context). Moving to a ChatGPT/Apps-SDK host is documented future work — the MCP
server is built so it could back that host with minimal change.

---

## 9. Build status

| Component | Status |
|---|---|
| MCP server: models, ACME client, GUID store, tools, handoff page | ✅ built, tested |
| MCP server: country-aware dynamic schema (GB + FR) | ✅ built, tested |
| Mock ACME (WireMock) GB + FR pricing | ✅ built, verified (live) |
| Backend: form-filling agent + `/chat`·`/upload`·`/confirm` + MCP client (legacy AI-quoting retired) | ✅ built, tested |
| Frontend: document upload + confirmation card + quote/handoff (ACME-branded) | ✅ built, tested |
| Full stack verified end-to-end (UI → backend → MCP → WireMock), GB + FR | ✅ GB £401.28 / FR €340.47 |
| Lightweight CI (GitHub Actions) | ✅ added |
| ChatGPT/Apps-SDK host; MCP auth/session; multi-doc conflict resolution | ⏳ future (see open questions) |

---

## 10. Running locally

See [`README.md`](README.md) for run and test commands. The MCP server runs with
`uv run python -m app.server` (streamable-HTTP on `:8090`); its test suite is
`cd mcp-server && uv run pytest -q`.
