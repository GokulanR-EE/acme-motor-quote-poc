import json
import os
from pathlib import Path

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

from app.llm.agent import run_agent_turn
from app.quoting.engine import price
from app.quoting.models import CoverTier, QuoteInput

app = FastAPI(title="ACME Motor Quoting POC (mock)")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# In-memory session store: session_id -> {"history": [...], "state": {...}}
sessions: dict[str, dict] = {}


def _get_client():
    if os.getenv("MOCK_LLM") == "1":
        return None
    from openai import OpenAI

    return OpenAI()


class ChatRequest(BaseModel):
    session_id: str
    message: str


class RepriceRequest(BaseModel):
    session_id: str
    cover_tier: CoverTier | None = None
    voluntary_excess: int | None = None


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/chat")
def chat(req: ChatRequest):
    session = sessions.setdefault(req.session_id, {"history": [], "state": {}})
    client = _get_client()

    def event_stream():
        for event in run_agent_turn(req.message, session, client=client):
            yield f"data: {json.dumps(event)}\n\n"
        yield "data: {\"type\": \"done\"}\n\n"

    return StreamingResponse(event_stream(), media_type="text/event-stream")


@app.post("/reprice")
def reprice(req: RepriceRequest):
    session = sessions.get(req.session_id)
    if not session or "quote_input" not in session.get("state", {}):
        raise HTTPException(status_code=404, detail="No quote in session yet.")
    qi: QuoteInput = session["state"]["quote_input"]
    updated = qi.model_copy(
        update={
            "cover_tier": req.cover_tier or qi.cover_tier,
            "voluntary_excess": req.voluntary_excess
            if req.voluntary_excess is not None
            else qi.voluntary_excess,
        }
    )
    session["state"]["quote_input"] = updated
    return price(updated).model_dump()


# Serve the built frontend (if present) from the same origin, so a single
# tunnel exposes both UI and API with no CORS. Guarded so tests/dev still work
# when the frontend hasn't been built yet. Mounted last: API routes win.
_DIST = Path(__file__).resolve().parents[3] / "frontend" / "dist"
if _DIST.is_dir():
    app.mount("/", StaticFiles(directory=str(_DIST), html=True), name="static")
