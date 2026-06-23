import type { CoverTier, Quote } from "../types";
import { CoverTierSelector } from "./CoverTierSelector";
import { ExcessSlider } from "./ExcessSlider";

export function QuoteCard({
  quote,
  onChange,
}: {
  quote: Quote;
  onChange: (changes: { cover_tier?: CoverTier; voluntary_excess?: number }) => void;
}) {
  const v = quote.input.vehicle;
  return (
    <div
      style={{
        background: "var(--axa-card)",
        border: "1px solid #e0e0ef",
        borderLeft: "6px solid var(--axa-blue)",
        borderRadius: 10,
        padding: 16,
        margin: "8px 0",
        maxWidth: 420,
      }}
    >
      <div style={{ color: "var(--axa-blue)", fontWeight: 700 }}>AXA Motor Quote</div>
      <div style={{ fontSize: 13, opacity: 0.7 }}>
        {v.make} {v.model} ({v.year}) · {v.registration}
      </div>
      <div style={{ fontSize: 32, fontWeight: 800, margin: "8px 0" }}>
        £{quote.annual_premium.toFixed(2)}
        <span style={{ fontSize: 14, fontWeight: 400 }}> /year</span>
      </div>
      <div className="axa-accent">£{quote.monthly_premium.toFixed(2)} /month</div>
      <CoverTierSelector
        value={quote.input.cover_tier}
        onChange={(t) => onChange({ cover_tier: t })}
      />
      <ExcessSlider
        value={quote.input.voluntary_excess}
        onChange={(e) => onChange({ voluntary_excess: e })}
      />
      <div style={{ fontSize: 11, opacity: 0.6, marginTop: 8 }}>
        Illustrative demo — mock data only, not a real or binding AXA quote.
      </div>
    </div>
  );
}
