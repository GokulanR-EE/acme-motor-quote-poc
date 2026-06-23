import type { Quote } from "../types";
import { QuoteCard } from "./QuoteCard";

export interface ChatItem {
  role: "user" | "assistant";
  text?: string;
  quote?: Quote;
}

export function MessageList({
  items,
  onQuoteChange,
}: {
  items: ChatItem[];
  onQuoteChange: (changes: { cover_tier?: string; voluntary_excess?: number }) => void;
}) {
  return (
    <div style={{ flex: 1, overflowY: "auto", padding: 16 }}>
      {items.map((it, i) => (
        <div key={i} style={{ textAlign: it.role === "user" ? "right" : "left" }}>
          {it.text && (
            <div
              style={{
                display: "inline-block",
                background: it.role === "user" ? "var(--axa-blue)" : "#eee",
                color: it.role === "user" ? "#fff" : "#000",
                padding: "8px 12px",
                borderRadius: 12,
                margin: "4px 0",
                maxWidth: "80%",
              }}
            >
              {it.text}
            </div>
          )}
          {it.quote && <QuoteCard quote={it.quote} onChange={onQuoteChange as never} />}
        </div>
      ))}
    </div>
  );
}
