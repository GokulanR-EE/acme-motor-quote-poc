export type CoverTier =
  | "comprehensive"
  | "third_party_fire_theft"
  | "third_party_only";

export interface Quote {
  quote_id: string;
  annual_premium: number;
  monthly_premium: number;
  input: {
    cover_tier: CoverTier;
    voluntary_excess: number;
    vehicle: { make: string; model: string; year: number; registration: string };
  };
  breakdown: Record<string, number>;
}

export interface ChatEvent {
  type: "text" | "quote" | "done";
  data?: string | Quote;
}
