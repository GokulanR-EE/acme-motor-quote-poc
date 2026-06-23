import { useState } from "react";

export function Composer({ onSend }: { onSend: (msg: string) => void }) {
  const [text, setText] = useState("");
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        if (text.trim()) {
          onSend(text.trim());
          setText("");
        }
      }}
      style={{ display: "flex", gap: 8, padding: 12, borderTop: "1px solid #ddd" }}
    >
      <input
        value={text}
        onChange={(e) => setText(e.target.value)}
        placeholder="e.g. I drive AB12CDE, age 34, 5 years NCB, SW1A 1AA"
        style={{ flex: 1, padding: 10, borderRadius: 8, border: "1px solid #ccc" }}
      />
      <button style={{ background: "var(--axa-blue)", color: "#fff", border: 0, borderRadius: 8, padding: "0 16px" }}>
        Send
      </button>
    </form>
  );
}
