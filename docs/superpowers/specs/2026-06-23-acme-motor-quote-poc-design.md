# ACME Motor Quote Assistant — POC Design Spec

- **Date:** 2026-06-23
- **Status:** Draft for review
- **Type:** Proof of concept / R&D prototype to demonstrate AI-assisted form-filling capability to ACME's CIO
- **Owner:** gokulan.r

> **Disclaimer / ACME isolation:** This is an independent R&D prototype. It uses
> **only synthetic mock data** and does **not** connect to, replicate, or use any
> real ACME internal systems, data, pricing, or APIs. All "ACME" references are
> placeholder branding for demo realism only. All quoting logic is served by a
> **deterministic mock** of ACME's APIs, not by AI and not by any real ACME system.

---

## 1. Background & objective

The team aligned on building an **AI-assisted motor-insurance quote form-filling**
experience for ACME (an insurer). The intelligence is **form-filling only** — an
LLM extracts and collects the parameters a quote needs. **All quoting logic lives
in ACME's (mocked) APIs**, never in the AI.

The longer-term product vision pairs a **ChatGPT app with an MCP server**, where
the MCP server is the durable, reusable core and the chat layer is a thin client.
For **this first POC slice** we deliberately substitute a **standalone branded chat
web app** for the ChatGPT form factor (faster to demo, full UI control, and it
sidesteps the "all PII enters ChatGPT context" production risk). The MCP server is
still built as the core artifact; the web app is a demo skin that replaces ChatGPT.

**Success bar:** convincing in a live demo to the CIO; shows the headline
capability (the AI reads a document and fills the form). *Not* production-readiness.
Everything in this slice is **demo-grade**; production concerns (auth, AI-security
hardening, GDPR, conflict resolution) are **documented as open questions for ACME**
rather than implemented.

### What this evolves from
A prior prototype (`axa-motor-quote-prototype`) put the *intelligence in quoting*
(LLM extract → our own pricing engine). This project **inverts** that: AI does
form-filling; the pricing engine concept moves **into the mocked ACME quote API**.
Frontend chat components and the transparent factor formula are reused, rebranded
to ACME, and re-homed behind the new architecture.

---

## 2. Scope of this slice

**In scope**
- One journey: **motor insurance quote**.
- Conversational form-filling (chat).
- **Single-document upload** with local extraction → pre-fill of the form.
- Customer **confirmation step** before the quote is requested.
- A real quote returned from a **mocked ACME API**.
- **GUID-protected handoff link** to a stub website page that renders the quote.

**Out of scope (named later iterations)**
- Multi-document **conflict resolution**.
- ChatGPT / OpenAI Apps-SDK form factor.
- Production **OAuth2 / session-token pass-through** auth.
- Live "reprice slider" (cover/excess adjustment is conversational this slice).
- Other journeys: claims, renewals, multi-vehicle (route to ACME website later).

---

## 3. Architecture

```
Standalone web app (React + Vite + TS, rebranded ACME)
        │  chat messages + single file upload
        ▼
Web-app backend (FastAPI)        ◀── the ONLY place an LLM runs
  ├─ form-filling LLM   — collects/normalizes params conversationally
  ├─ extraction LLM     — vision; parses one uploaded doc → structured fields
  └─ MCP client ───────────────────┐
                                   ▼
                MCP server (deterministic — the real artifact)
                  ├─ tool: lookup_vehicle(registration)
                  ├─ tool: submit_quote_request(QuoteInput)  ─┐
                  ├─ tool: create_handoff_link(quote_ref)     │ HTTP
                  └─ HTTP GET /handoff/{guid} (+ stub page)    ▼
                                                Mock ACME APIs (WireMock)
                                                  ├─ GET  /vehicles/{registration}
                                                  └─ POST /quotes  → premium + quote_ref
```

**Key architectural decision — LLM placement:** both LLMs (form-filling and
document extraction) run in the **web-app backend**. The **MCP server is LLM-free
and deterministic**: strict pydantic-validated tool schemas, no model server-side.
This gives the smallest prompt-injection surface on the MCP server and keeps the
"MCP as product" boundary clean.

---

## 4. Components & quality bar

| Component | Stack | Quality bar |
|---|---|---|
| Chat web app | React + Vite + TS (reuse POC UI, rebranded ACME) | Demo |
| Web-app backend | FastAPI; form-filling LLM + doc extraction LLM; MCP client | Demo (POC) |
| **MCP server** | Python MCP SDK; deterministic tools, strict pydantic schemas | **Cleanest-built artifact** |
| Mock ACME APIs | WireMock; AI-generated **stub config** (not code); response templating for premium | Demo |
| Handoff page | Minimal static page served by the MCP server; fetches quote by GUID | Demo |
| Source control / CI | GitHub + GitHub Actions (lint + tests); lightweight | — |

---

## 5. Data model (the "form")

Bounded but realistic motor-quote field set:

- **Vehicle:** `registration` → (via `lookup_vehicle`) `make`, `model`, `year`,
  `value`, `insurance_group`.
- **Driver:** `full_name`, `date_of_birth` (→ derived `age`), `postcode`,
  `ncb_years`.
- **Cover:** `cover_tier` ∈ {`comprehensive`, `third_party_fire_theft`,
  `third_party_only`}, `voluntary_excess` ∈ {0, 100, 250, 500, 750, 1000}.
- **Optional realism:** `annual_mileage`, `use` (social / commuting).

`QuoteInput` is validated by pydantic at the MCP boundary.

---

## 6. Happy-path data flow

1. User chats and/or uploads **one** document (primary: an **old policy / renewal
   notice**; alternative: a **driving licence**).
2. Backend **extraction LLM** (vision) parses the document into structured fields.
   The **raw file never leaves the backend and is discarded after extraction** —
   honors "no raw documents stored on the MCP server", extended for the POC to
   "not persisted anywhere".
3. Backend **form-filling LLM** fills remaining gaps conversationally and
   **normalizes vague input** ("about 2 years ago" → a concrete date).
4. Backend assembles a candidate `QuoteInput`; the UI shows a **confirmation card**;
   the user confirms. **Accuracy responsibility stays with the customer.**
5. Backend calls MCP `lookup_vehicle` → `submit_quote_request`; the MCP server
   validates and calls **WireMock ACME** `/quotes`; returns premium + `quote_ref`.
6. Backend calls MCP `create_handoff_link(quote_ref)`; the MCP server mints a
   **GUID** (non-sequential, to prevent enumeration), stores the quote keyed by
   GUID, and returns `…/handoff/{guid}`.
7. The UI shows the quote card + handoff link. Opening the link renders the quote
   on the stub website page.

**Cover/excess adjustment:** conversational only this slice — e.g. "change my
excess to £500" re-runs `submit_quote_request`. No slider.

---

## 7. Decisions adopted (defaults; revisable)

- **Demo document type:** old policy / renewal notice (pre-fills the most
  quote-relevant fields: reg, NCB years, current cover tier/excess). Driving
  licence is the noted alternative (identity + address).
- **Cover/excess adjustment:** conversational, no slider.
- **Auth:** demo-grade session id (client-generated UUID keying in-memory session
  state), same approach as the prior POC. Production OAuth2 / ChatGPT
  session-token pass-through is an **open question for ACME**, not implemented.
- **LLM provider:** OpenAI (matches reused POC code), with a `MOCK_LLM` offline
  mode so the demo runs with **no API key**; document extraction uses a
  vision-capable model.
- **Mock premium:** port the prior POC's transparent factor formula into
  **WireMock response templating**, so premiums vary deterministically with inputs
  (feels real, stays a mock). Factor model:
  `premium = base_rate(group) × age_factor × cover_factor × postcode_factor ×
  (1 − ncb_discount) × excess_factor`.
- **Repo:** new GitHub repo `acme-motor-quote-poc`, reusing prior-POC frontend
  components rebranded to ACME.

---

## 8. Error handling / non-happy paths (demo-safe)

- **Unknown registration:** ACME mock returns "not found" → assistant asks the
  user to confirm the car manually.
- **Low-confidence / unreadable document:** fall back to asking conversationally;
  never block the journey.
- **Invalid parameters:** pydantic validation at the MCP boundary → assistant
  re-asks the offending field.
- **ACME referral / decline responses:** outside the happy path, low priority —
  surfaced as a graceful in-chat message and logged as an open UX question.
- **Bad / expired GUID at handoff:** friendly "quote not found or expired" page.

---

## 9. AI-security posture (POC-appropriate)

- The **MCP server is LLM-free and deterministic**, with strict schemas and
  boundary validation.
- **Extracted document text is treated as data, not instructions** — the primary
  mitigation against prompt injection delivered via an uploaded document.
- Server-side prompt-injection / jailbreak hardening is **documented as a
  production concern** for any future design where an LLM runs server-side.

---

## 10. Testing (right-sized for a POC)

- **MCP tools:** schema validation + dispatch, with a **stubbed ACME** (no
  network).
- **WireMock contract:** `lookup_vehicle` and `quote` response shapes +
  determinism (same inputs → same premium).
- **Backend:** form-filling loop with a **stubbed LLM**; document extraction with
  a **stubbed vision response**; handoff GUID mint + fetch.
- **Frontend:** one smoke test (confirmation card / quote card renders).
- **CI:** GitHub Actions runs the above on push (lightweight).

---

## 11. Risk dispositions → ACME open-questions document

These are captured for the team's consolidated "open questions for ACME" document;
the POC makes a defensible interim choice and writes the production concern down.

| Risk / question | POC disposition |
|---|---|
| PII exposure | Not in ChatGPT context (standalone app). Shifts to "PII is sent to the LLM provider's API for extraction/form-filling" — flagged for ACME (data residency, provider, redaction). |
| Multi-document conflict resolution | Out of scope (single doc). Named later iteration. |
| Server-side AI security (injection/jailbreak) | MCP kept LLM-free + deterministic; extracted text treated as data. Hardening documented for future server-side-LLM designs. |
| Customer accuracy / approximate data | In-scope feature: LLM normalizes vague input; full dataset confirmed by the customer before quote/handoff. |
| Session token mechanism & expiry | Demo-grade session id now; OAuth2 pass-through + ~2h expiry are open questions for ACME. |
| GDPR | Open question for ACME; POC stores no raw documents and no persistent PII. |

---

## 12. Future iterations (not in this slice)

- Multi-document upload + conflict-resolution flow.
- ChatGPT app / OpenAI Apps-SDK form factor on the same MCP server.
- Production OAuth2 / ChatGPT session-token pass-through + defined expiry.
- Embedded AI journey within ACME's existing quote flow (stretch goal).
- Additional journeys (claims, renewals, multi-vehicle) — or routing them to the
  ACME website.
- Dashboard and landing page (demo-safe quality, separate sub-projects).
