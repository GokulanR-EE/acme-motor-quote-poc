import type { CoverTier } from "../types";

const TIERS: { id: CoverTier; label: string }[] = [
  { id: "comprehensive", label: "Comprehensive" },
  { id: "third_party_fire_theft", label: "TPFT" },
  { id: "third_party_only", label: "Third Party" },
];

export function CoverTierSelector({
  value,
  onChange,
}: {
  value: CoverTier;
  onChange: (t: CoverTier) => void;
}) {
  return (
    <div style={{ display: "flex", gap: 8, margin: "8px 0" }}>
      {TIERS.map((t) => (
        <button
          key={t.id}
          onClick={() => onChange(t.id)}
          style={{
            padding: "6px 10px",
            border: "1px solid var(--acme-blue)",
            background: value === t.id ? "var(--acme-blue)" : "#fff",
            color: value === t.id ? "#fff" : "var(--acme-blue)",
            borderRadius: 6,
          }}
        >
          {t.label}
        </button>
      ))}
    </div>
  );
}
