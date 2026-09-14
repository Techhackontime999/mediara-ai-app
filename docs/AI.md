# AI / LLM provider

The AI layer (`backend/ai/`) is provider-agnostic. It speaks **OpenAI-compatible
`chat/completions`** to anything, or the **native Gemini `generateContent`**,
and degrades to a **deterministic mediation engine** when no provider is
configured or reachable. Providers are configured with **server-side env vars —
the Android client never sees API keys.**

## How a provider is chosen

`AI_PROVIDER` (`auto` default) resolves like this:

1. `AI_PROVIDER=gemini` → native Gemini.
2. `AI_PROVIDER=openai` → OpenAI-compatible.
3. `auto`:
   - `AI_BASE_URL` contains `generativelanguage` → Gemini.
   - `AI_BASE_URL` is set → OpenAI-compatible at that URL.
   - legacy `GEMINI_API_KEY` set → Gemini.
   - `AI_API_KEY` set → OpenAI-compatible (default base).
   - nothing set → **deterministic engine** (`{provider:none}`).

## Providers (examples)

### OpenAI

```bash
AI_BASE_URL=https://api.openai.com/v1
AI_API_KEY=sk-...
AI_MODEL=gpt-4o-mini
```

### Groq

```bash
AI_BASE_URL=https://api.groq.com/openai/v1
AI_API_KEY=gsk_...
AI_MODEL=llama-3.1-8b-instant
```

### OpenRouter

```bash
AI_BASE_URL=https://openrouter.ai/api/v1
AI_API_KEY=sk-or-...
AI_MODEL=meta-llama/llama-3.1-8b-instruct
```

### Mistral

```bash
AI_BASE_URL=https://api.mistral.ai/v1
AI_API_KEY=...
AI_MODEL=mistral-small-latest
```

### Hugging Face

```bash
AI_BASE_URL=https://router.huggingface.co/v1
AI_API_KEY=...
AI_MODEL=mistralai/Mistral-7B-Instruct-v0.3
```

### Local — Ollama (no key)

```bash
AI_BASE_URL=http://localhost:11434/v1
AI_MODEL=llama3.1
```

### Local — LM Studio (no key)

```bash
AI_BASE_URL=http://localhost:1234/v1
AI_MODEL=the-currently-loaded-model
```

### Native Gemini

```bash
GEMINI_API_KEY=...                 # AI Studio key
GEMINI_MODEL=gemini-2.5-flash
# or equally: AI_PROVIDER=gemini
```

## Behaviour

- **Fallback**: with `AI_FALLBACK_ENABLED=True` (default), any provider failure
  (timeout, rate limit, network) falls back to the deterministic engine, so the
  full mediation flow keeps working. Set `False` to surface provider errors.
- **Timeout**: LLM requests time out after 60s, with bounded retries — a long
  unavailability does not hang a request indefinitely.
- **Tests / offline dev**: the suite patches the provider away. If a local
  `.env` sets a real key, tests can be slow (each call waits for the timeout).
  Point `AI_BASE_URL` at a closed local port to fail-fast into the fallback:

  ```bash
  AI_BASE_URL="http://127.0.0.1:59999/v1"  # tests fall back instantly
  ```

## The deterministic fallback engine

When no LLM is available the pipeline uses rule-based summarization, extraction
of participant concerns, a balance & dominance detector, and proposes structured
resolutions. It is deliberate and explainable — proposal citations
(`grounded_in`) still trace back to actual statements with a balance report.

## Security notes

- Keys live **only** in server environment files; never commit `.env`.
- Prompt boundaries include web-safety instructions; private message content is
  encrypted at rest and **de-identified before analysis** (a UUID replaces the
  party identity).