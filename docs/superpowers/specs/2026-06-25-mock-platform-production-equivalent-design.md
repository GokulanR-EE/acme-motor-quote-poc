# Mock Motor Quote Platform — Production-Equivalent Design (Java, pricing-only mock)

- **Date:** 2026-06-25
- **Status:** Draft for review
- **Owner:** gokulan.r
- **Inputs:** the build brief (`ACME_ChatGPT_PoC_Build_Brief_v1_0.md`), the 24-Jun team
  brainstorm (`Brainstorming … Notes by Gemini`), and this session's decisions.

> Synthetic mock data only — no real ACME/client systems, data, or brand. "ACME" is a placeholder.

## 1. Goal

Make the **mock motor-quote platform production-equivalent**, with **only the pricing /
vendor-sourced data mocked**. Everything a real insurer backend owns — quote state,
validation, underwriting outcomes, policy creation, persistence, events, purchase
handoff — behaves like a live system; only the values a third-party vendor would supply
are synthetic, behind a swappable seam.

## 2. Key decision — one Java platform, no Python mock service

The 24-Jun meeting tentatively split "Java for the API, Python for the mock service." The
platform owner has resolved this: **the entire platform — API *and* mock — is Java /
Spring Boot. There is no separate Python mock service.** Rationale:

- The platform must integrate with the **external vendor** in production over whatever
  transport the vendor kit exposes (SOAP, XML, or REST); Java is a solid stack for that
  integration whichever it turns out to be.
- One service is simpler to build, test, deploy, and reason about than two.
- The "mock" is **not a separate service** — it is the current *implementation* of the
  platform's **vendor seam**, swappable to the real vendor with no other change.

(Python remains the language for the **MCP server** and the conversation backend — those
are separate components, unchanged by this decision.)

## 3. The mock-vs-production boundary

The single rule: **only vendor-sourced data is mocked.**

| Mocked — behind the `VendorClient` vendor seam | Production-grade — mimics a live system |
|---|---|
| rating / **premium values**, vehicle lookup, address lookup (what a real vendor's API returns — SOAP/XML/REST) | quote state & journey state machine; **schema validation**; missing-field calculation; **underwriting** decision (quote / refer / decline + reasons); **policy creation** (policy number, status); **price-breakdown** assembly; persistence; event store + three-layer logging; purchase link + strict-GUID landing |

Underwriting (quote/refer/decline eligibility) is the **insurer's** decision and lives in
the platform; the **rating values** it acts on come from the vendor seam (mocked).

## 4. Vendor seam

- `VendorClient` interface — the boundary to the external vendor. Methods cover the
  vendor-sourced data: `rate(quote)`, `lookupVehicle(reg)`, `lookupAddress(postcode)`
  (and, where issuance is vendor-side, `issuePolicy`). The seam is **transport-agnostic** —
  callers depend only on this interface, never on the wire protocol.
- `MockVendorClient` — **default** (`platform.vendor=mock`): synthetic, **deterministic**.
- `LiveVendorClient` — **`platform.vendor=live`** profile: stub today (throws "not
  implemented"); becomes the real client calling the vendor's API over whatever transport
  it exposes (SOAP, XML, or REST depending on the vendor kit), decided when the vendor is
  integrated. Swapping mock→live is **config-only**; no other code changes.

## 5. Mock-data coherence

The mock must be **contextually coherent**, never random (no "truck data for a car"):

- Premiums **respond to inputs** via the brief §15 rules (age, claims, convictions,
  cover level, mileage, excess) and produce an explainable **breakdown**.
- Vehicle / address lookups return plausible, seeded values + deterministic fallbacks.
- **Single deterministic quote per request** for now (matches the brief's pricing model).
- A **small multi-product / multi-cover-tier catalog** (several quotations per request) is
  a **noted future enhancement**, not in this scope.

## 6. Platform component structure (brief §9 — all Java)

`Quote Service` (state, validation, missing-field) · `Rating` (premiums **via the vendor
seam = mock**) · `Underwriting Engine` (quote/refer/decline) · `Event Store` (append-only)
· `Purchase Link Service` (signed GUID URLs). The **Document Service** (LLM extraction)
stays in the **conversation layer** (the platform/MCP remain LLM-free), per the agreed
architecture.

## 7. Production-equivalent requirements

Most are already implemented; this spec records the full bar:

- **Persistence:** Spring Data JPA; **H2** embedded for the demo (file-backed, survives
  restart), **Postgres-ready** via the `prod` profile. *(Done.)*
- **Error taxonomy:** global handler, structured `{code,message,details}`, consistent
  statuses (404 / 409 / 422 / 400 / 500). *(Done.)*
- **Validation:** bean validation; session header required; size limits. *(Done.)*
- **Config & profiles:** `dev`/`prod`; `platform.vendor=mock` / `platform.vendor=live`. *(Done.)*
- **Security:** strong-entropy **session-scoped** access (no users/sign-in/auth, per
  brief); cross-session access rejected. *(Done.)*
- **API contract:** OpenAPI published (springdoc). *(Done.)*
- **Observability:** actuator health; structured logging. *(Partial.)*
- **Still to build:** **deployment + CI/CD** (GitHub Actions; host TBD GCP/AWS), more
  **integration tests** (DB-backed, end-to-end journey), and tidying the
  **`LiveVendorClient` seam stub** + documenting the swap.

## 8. Out of scope

- A separate Python mock service (explicitly dropped).
- Multi-product/cover-tier catalog (future).
- Real vendor integration (SOAP/XML/REST), real payments, policy issuance to a live system, auth.

## 9. Testing

Unit tests per component; **integration tests** against H2 covering the full journey
(create → collect → price → underwrite → purchase → policy); contract checks on the REST
shapes the MCP/conversation layer depend on; run in **CI**.
