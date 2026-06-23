import { useRef, useState } from "react";
import { reprice, streamChat } from "../api";
import type { Quote } from "../types";
import { Composer } from "./Composer";
import { type ChatItem, MessageList } from "./MessageList";

export function ChatWindow() {
  const [items, setItems] = useState<ChatItem[]>([
    { role: "assistant", text: "Hi! I'm your AXA motor assistant. Tell me about your car to get a quote." },
  ]);
  const sessionId = useRef(crypto.randomUUID()).current;

  async function send(msg: string) {
    setItems((p) => [...p, { role: "user", text: msg }]);
    await streamChat(sessionId, msg, (e) => {
      if (e.type === "text") setItems((p) => [...p, { role: "assistant", text: e.data as string }]);
      if (e.type === "quote") setItems((p) => [...p, { role: "assistant", quote: e.data as Quote }]);
    });
  }

  async function onQuoteChange(changes: { cover_tier?: string; voluntary_excess?: number }) {
    const updated = await reprice(sessionId, changes as never);
    setItems((p) => {
      const copy = [...p];
      for (let i = copy.length - 1; i >= 0; i--) {
        if (copy[i].quote) {
          copy[i] = { ...copy[i], quote: updated };
          break;
        }
      }
      return copy;
    });
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100vh" }}>
      <div className="axa-header">AXA <span className="axa-accent">Motor</span> — quote assistant (demo)</div>
      <MessageList items={items} onQuoteChange={onQuoteChange} />
      <Composer onSend={send} />
    </div>
  );
}
