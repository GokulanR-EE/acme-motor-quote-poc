# ACME Motor Quote — Frontend

The chat UI for the ACME motor-quote proof of concept. It renders a
conversational assistant that collects vehicle and driver details, then
displays an illustrative quote with adjustable cover tier and excess.

The UI talks to the ACME backend (the FastAPI service) for streaming chat
and repricing. In local dev, point it at the backend with
`VITE_API_BASE=http://localhost:8000`.

See the repository-root README for full setup and run instructions.
