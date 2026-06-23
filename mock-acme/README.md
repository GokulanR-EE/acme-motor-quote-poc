# mock-acme

A deterministic [WireMock](https://wiremock.org/) mock of ACME's vehicle +
quote APIs for **GB** and **FR**. It backs the MCP server's `acme_client`
during local development and the demo: same inputs always yield the same
premium, so the demo is reproducible.

## What it serves

| Method | Path                       | Behaviour                                              |
| ------ | -------------------------- | ------------------------------------------------------ |
| GET    | `/{cc}/vehicles/{id}`      | Seeded vehicle JSON, or `404` for unknown ids          |
| POST   | `/{cc}/quotes`             | `{ "quote_ref", "annual_premium" }`, premium computed  |

`{cc}` is the lowercased country code (`gb` / `fr`).

### Seeded vehicles

| Country | Path                       | make / model        | year | value | insurance_group |
| ------- | -------------------------- | ------------------- | ---- | ----- | --------------- |
| GB      | `/gb/vehicles/AB12CDE`     | Volkswagen Golf     | 2019 | 14000 | 20              |
| GB      | `/gb/vehicles/TS21EVS`     | Tesla Model 3       | 2021 | 38000 | 48              |
| FR      | `/fr/vehicles/AB123CD`     | Renault Clio        | 2020 | 16000 | `null`          |

Any other `/{cc}/vehicles/...` path returns `404` (low-priority catch-all stub).

## Pricing (deterministic)

**GB** — one stub per `cover_tier`:

```
annual = (200 + insurance_group*12)
       * (2.0 - age*0.02)
       * (1 - ncb_years*0.05)
       * (1 - voluntary_excess*0.0002)
       * tier_mult
```

`tier_mult`: comprehensive `1.0`, third_party_fire_theft `0.85`,
third_party_only `0.70`.

**FR** — one stub per `formule`:

```
annual = (150 + value*0.015)
       * bonus_malus
       * formule_mult
       * (1 - franchise*0.0001)
```

`formule_mult`: tous_risques `1.0`, tiers_plus `0.80`, au_tiers `0.60`.

`quote_ref` is always `Q-<identifier>` in both countries.

### How the premium is computed: response templating

These premiums are computed at request time using **WireMock 3.x response
templating** (`"transformers": ["response-template"]`) and the `{{math}}`
helper, reading the request body via `jsonPath`. The correct multiplier is
selected by **matching the request body** (`$.cover_tier` / `$.formule`) so
each stub's template does pure arithmetic with its multiplier baked in — no
branching inside a template.

`annual_premium` is rendered as a numeric **string** (e.g. `"413.82"`). This
is intentional and contract-safe: the MCP server coerces it with `float(...)`.

Templating was **verified working** against WireMock `3.9.1` (see
[Verification](#verification) below). Pricing is therefore correct for
**arbitrary** inputs, not just the demo values. WireMock **must** be started
with `--global-response-templating` (below) for the helpers to run.

## Run it (Docker)

```bash
docker run --rm -p 8080:8080 \
  -v "$PWD/mock-acme/mappings:/home/wiremock/mappings" \
  wiremock/wiremock:3.9.1 --global-response-templating
```

The mock then listens on `http://localhost:8080`.

### Alternative: standalone jar (no Docker)

```bash
curl -sL -o wiremock-standalone-3.9.1.jar \
  https://repo1.maven.org/maven2/org/wiremock/wiremock-standalone/3.9.1/wiremock-standalone-3.9.1.jar
java -jar wiremock-standalone-3.9.1.jar \
  --port 8080 --root-dir "$PWD/mock-acme" --global-response-templating
```

## Verification

Hand-computed expected values, confirmed against a running WireMock 3.9.1:

**GB** — `AB12CDE`, insurance_group 20, age 34, ncb 5, excess 250:

```bash
# comprehensive -> 413.82
curl -s -X POST localhost:8080/gb/quotes -H 'Content-Type: application/json' \
  -d '{"identifier":"AB12CDE","insurance_group":20,"age":34,"ncb_years":5,"cover_tier":"comprehensive","voluntary_excess":250}'

# third_party_fire_theft -> 351.747
# third_party_only       -> 289.674
```

**FR** — `AB123CD`, value 16000, bonus_malus 0.90, franchise 300:

```bash
# tous_risques -> 340.47
curl -s -X POST localhost:8080/fr/quotes -H 'Content-Type: application/json' \
  -d '{"identifier":"AB123CD","value":16000,"bonus_malus":0.90,"formule":"tous_risques","franchise":300}'

# tiers_plus -> 272.376
# au_tiers   -> 204.282
```

**Vehicles:**

```bash
curl -s localhost:8080/gb/vehicles/AB12CDE      # 200 Volkswagen Golf
curl -s localhost:8080/fr/vehicles/AB123CD      # 200 Renault Clio (insurance_group null)
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/gb/vehicles/ZZ99ZZZ   # 404
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/fr/vehicles/XX000XX   # 404
```

## Tests

The mapping files are statically validated (no running WireMock needed) by:

```bash
cd mcp-server && uv run pytest tests/test_wiremock_mappings.py -q
```
