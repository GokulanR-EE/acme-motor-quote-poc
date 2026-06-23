# Open Questions for ACME

> **Purpose:** the consolidated list of decisions that need ACME / domain-SME
> confirmation before any production build (a 19-Jun action item). For each, the
> POC makes a **defensible interim choice** so we can keep moving; none of these
> are settled by the prototype.

> **Reminder:** the POC uses **synthetic mock data only** and is **not connected
> to any real ACME system**. Everything below is about the eventual real build.

---

## 1. Data privacy & GDPR
- **Questions:** What is the lawful basis for processing customer PII? Where must
  data reside (EU residency for FR customers)? What retention applies, and how is
  right-to-erasure honoured? Is a DPA needed with the LLM provider whose API sees
  the PII during extraction/form-filling?
- **POC stance:** No raw documents stored; no persistent PII; in-memory session
  state only. PII does transit the LLM provider's API during extraction.

## 2. Front-end host (ChatGPT exposure)
- **Questions:** For the CIO demo and beyond — is the front end a **ChatGPT-style
  app we build and host**, or one running **inside OpenAI ChatGPT** (Apps SDK /
  connector)? If the latter, all customer PII enters ChatGPT's context — is that
  acceptable, and under what agreement?
- **POC stance:** Standalone branded chat web app (our own host) — avoids PII
  entering ChatGPT context and gives full UI control. The MCP server is built
  host-agnostic, so an in-ChatGPT front end could be added later without rework.

## 3. Session & authentication
- **Questions:** What is the exact session-token mechanism (ChatGPT pass-through?
  OAuth2?)? What session-expiry duration (≈2 hours?)? Is auth in scope for the
  "production-quality MCP" bar now, or a later milestone?
- **POC stance:** Demo-grade session id, no login/auth. Documented, not implemented.

## 4. Quoting APIs & rating
- **Questions:** What are ACME's real motor rating factors and their weightings,
  per region? What are the actual product/cover tiers and naming (GB:
  comprehensive / TPFT / TPO; FR: formules)? Which regions must be supported
  (GB, FR, others)?
- **POC stance:** All quoting is a **deterministic mock (WireMock)** for GB + FR.
  The AI never prices — quoting stays entirely in ACME's (mocked) APIs.

## 5. Document ingestion & conflict resolution
- **Questions:** Which documents are authoritative (old policy, passport, driving
  licence, Carte Grise)? When multiple uploaded documents conflict (different
  DOB / address), what are the resolution rules — which source wins, or is it
  always user-confirmed?
- **POC stance:** Single-document extraction; multi-document **conflict resolution
  deferred** to a later iteration.

## 6. Journey scope
- **Questions:** How should unsupported journeys (claims, renewals, multi-vehicle)
  be handled — routed to the ACME website? Is an **embedded AI journey within the
  existing quote flow** wanted as a stretch goal?
- **POC stance:** Motor new-quote only. The schema tool returns an
  `unsupported_country` signal so the host can route elsewhere; journey-level
  routing (claims/renewals) is not yet built.

## 7. Non-happy-path API responses
- **Questions:** How should **referral** vs **decline** responses from ACME be
  presented in the chat UX?
- **POC stance:** Low priority for now; surfaced as a graceful in-chat message.

## 8. AI security
- **Questions:** If an LLM ever runs **server-side** (on the MCP), what
  prompt-injection / jailbreak controls are required?
- **POC stance:** The MCP server is **LLM-free and deterministic**; input is
  validated at the boundary and any text originating from a document is treated as
  **data, never instructions**. Server-side hardening is documented for any future
  design that puts an LLM on the server.

## 9. Customer accuracy
- **Questions:** Confirmed that **accuracy responsibility stays with the customer**
  — final data is shown for confirmation before purchase?
- **POC stance:** Yes — a confirmation step precedes quoting; vague inputs ("about
  2 years ago") are normalised and shown back for confirmation.
