import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { QuoteCard } from "./QuoteCard";
import type { Quote } from "../types";

const quote: Quote = {
  quote_id: "q1",
  annual_premium: 612.34,
  monthly_premium: 51.03,
  input: {
    cover_tier: "comprehensive",
    voluntary_excess: 250,
    vehicle: { make: "Volkswagen", model: "Golf", year: 2019, registration: "AB12CDE" },
  },
  breakdown: {},
};

describe("QuoteCard", () => {
  it("renders the annual premium and vehicle", () => {
    render(<QuoteCard quote={quote} onChange={() => {}} />);
    expect(screen.getByText(/612.34/)).toBeInTheDocument();
    expect(screen.getByText(/Volkswagen Golf/)).toBeInTheDocument();
  });
});
