"""Server-side LLM client with provider abstraction.

Supports any OpenAI-compatible endpoint (OpenAI, Groq, Mistral, OpenRouter,
DeepSeek, local Ollama/LM Studio/vLLM — anything exposing `/chat/completions`)
as well as the native Google Gemini API.

Selecting the provider (env vars, never seen by the Android client):

    AI_PROVIDER = "auto" | "gemini" | "openai"     (default: "auto")
    AI_BASE_URL = e.g. https://api.openai.com/v1
                         http://localhost:11434/v1   (Ollama)
                         https://router.huggingface.co/v1
    AI_API_KEY  = bearer key for the provider (omit for local Ollama/LM Studio)
    AI_MODEL    = model name, e.g. gpt-4o-mini, llama3.1, gemini-2.5-flash

Backwards-compatible: GEMINI_API_KEY / GEMINI_MODEL still work and behave as
`AI_PROVIDER=gemini`. When `AI_BASE_URL` points at a Google endpoint or
`AI_PROVIDER=gemini`, the native Gemini `:generateContent` payload is used;
otherwise an OpenAI-compatible `chat/completions` request is sent.

If no LLM is configured at all, `is_llm_enabled()` returns False and the
deterministic mediation engine is used instead.
"""

import json
import logging
import re

import requests
from django.conf import settings

logger = logging.getLogger("mediara.ai")

_GEMINI_DEFAULT_BASE = "https://generativelanguage.googleapis.com/v1beta"
_OPENAI_DEFAULT_BASE = "https://api.openai.com/v1"


class LLMUnavailableError(Exception):
    """Raised when the LLM cannot be reached and fallback should be used."""


# ---------------------------------------------------------------------------
# Configuration resolution
# ---------------------------------------------------------------------------

def llm_config() -> dict:
    """Resolve the effective provider configuration, normalising legacy names."""
    provider = (getattr(settings, "AI_PROVIDER", "") or "auto").strip().lower()
    base_url = (getattr(settings, "AI_BASE_URL", "") or "").strip().rstrip("/")
    api_key = (getattr(settings, "AI_API_KEY", "") or "").strip()
    model = (getattr(settings, "AI_MODEL", "") or "").strip()

    # Legacy Gemini variables keep working unchanged.
    legacy_key = (getattr(settings, "GEMINI_API_KEY", "") or "").strip()
    legacy_model = (getattr(settings, "GEMINI_MODEL", "gemini-2.5-flash") or "gemini-2.5-flash").strip()
    using_legacy_key = bool(legacy_key)
    if not api_key:
        api_key = legacy_key
    if not model:
        model = legacy_model

    if provider not in ("auto", "gemini", "openai"):
        logger.warning("Unknown AI_PROVIDER=%r, falling back to auto", provider)
        provider = "auto"

    # Auto-detect from the base URL when no explicit provider was chosen.
    if provider == "auto":
        if "generativelanguage.googleapis.com" in base_url or "generativelanguage" in base_url:
            provider = "gemini"
        elif base_url:
            provider = "openai"
        elif using_legacy_key:
            provider = "gemini"
        elif api_key:
            provider = "openai"
        else:
            return {"provider": "none", "base_url": "", "api_key": "", "model": ""}

    if provider == "gemini":
        return {
            "provider": "gemini",
            "base_url": base_url or _GEMINI_DEFAULT_BASE,
            "api_key": api_key,
            "model": model,
        }

    return {
        "provider": "openai",
        "base_url": base_url or _OPENAI_DEFAULT_BASE,
        "api_key": api_key,
        "model": model or "gpt-4o-mini",
    }


def is_llm_enabled() -> bool:
    """True when any LLM provider is configured (key or base URL present)."""
    return llm_config()["provider"] != "none"


# ---------------------------------------------------------------------------
# Response parsing helpers (shared across providers)
# ---------------------------------------------------------------------------

def _strip_json_fence(raw: str) -> str:
    text = (raw or "").strip()
    text = re.sub(r"^```(?:json)?\s*", "", text).strip()
    text = re.sub(r"\s*```$", "", text).strip()
    return text


def extract_json(raw: str):
    """Extract the first JSON value (object or array) from an LLM response."""
    text = _strip_json_fence(raw)
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass

    start_candidates = []
    if "[" in text:
        start_candidates.append(text.index("["))
    if "{" in text:
        start_candidates.append(text.index("{"))
    if not start_candidates:
        raise LLMUnavailableError("No JSON found in LLM response")
    start = min(start_candidates)
    open_char = text[start]
    close_char = "]" if open_char == "[" else "}"
    depth = 0
    in_string = False
    escape = False
    for i in range(start, len(text)):
        ch = text[i]
        if in_string:
            if escape:
                escape = False
            elif ch == "\\":
                escape = True
            elif ch == '"':
                in_string = False
            continue
        if ch == '"':
            in_string = True
        elif ch == open_char:
            depth += 1
        elif ch == close_char:
            depth -= 1
            if depth == 0:
                return json.loads(text[start : i + 1])
    raise LLMUnavailableError("Unbalanced JSON in LLM response")


# ---------------------------------------------------------------------------
# Provider adapters
# ---------------------------------------------------------------------------

def _call_openai_compatible(cfg: dict, prompt: str, temperature: float, max_tokens: int) -> str:
    """POST /chat/completions — the de-facto standard used by most providers."""
    url = f"{cfg['base_url']}/chat/completions"
    payload = {
        "model": cfg["model"],
        "messages": [
            {"role": "system", "content": "You are Mediara AI, an impartial, empathetic mediation assistant."},
            {"role": "user", "content": prompt},
        ],
        "temperature": temperature,
        "max_tokens": max_tokens,
    }
    headers = {"Content-Type": "application/json"}
    if cfg["api_key"]:
        headers["Authorization"] = f"Bearer {cfg['api_key']}"

    try:
        resp = requests.post(url, json=payload, headers=headers, timeout=60)
    except requests.RequestException as exc:
        logger.warning("LLM request to %s failed: %s", url, exc)
        raise LLMUnavailableError(str(exc)) from exc

    if resp.status_code != 200:
        logger.warning("LLM %s returned %s: %s", url, resp.status_code, resp.text[:500])
        raise LLMUnavailableError(f"LLM HTTP {resp.status_code}")

    data = resp.json()
    choices = data.get("choices") or []
    if not choices:
        raise LLMUnavailableError("LLM returned no choices")
    text = (choices[0].get("message") or {}).get("content") or ""
    if not text.strip():
        raise LLMUnavailableError("LLM returned empty text")
    return text.strip()


def _call_gemini(cfg: dict, prompt: str, temperature: float, max_tokens: int) -> str:
    """Native Google Generative Language endpoint."""
    if not cfg["api_key"]:
        raise LLMUnavailableError("AI_API_KEY/GEMINI_API_KEY is not configured")
    url = f"{cfg['base_url']}/models/{cfg['model']}:generateContent"
    payload = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {
            "temperature": temperature,
            "maxOutputTokens": max_tokens,
        },
    }
    try:
        resp = requests.post(url, params={"key": cfg["api_key"]}, json=payload, timeout=60)
    except requests.RequestException as exc:
        logger.warning("Gemini request failed: %s", exc)
        raise LLMUnavailableError(str(exc)) from exc

    if resp.status_code != 200:
        logger.warning("Gemini returned %s: %s", resp.status_code, resp.text[:500])
        raise LLMUnavailableError(f"Gemini HTTP {resp.status_code}")

    data = resp.json()
    candidates = data.get("candidates") or []
    if not candidates:
        raise LLMUnavailableError("Gemini returned no candidates")
    parts = (candidates[0].get("content") or {}).get("parts") or []
    text = "".join(part.get("text", "") for part in parts)
    if not text.strip():
        raise LLMUnavailableError("Gemini returned empty text")
    return text.strip()


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

def call_llm(prompt: str, *, temperature: float = 0.4, max_tokens: int = 1600) -> str:
    """Return the text of a completion from the configured provider."""
    cfg = llm_config()
    if cfg["provider"] == "none":
        raise LLMUnavailableError("No LLM provider configured (set AI_BASE_URL + AI_API_KEY, or GEMINI_API_KEY)")
    if cfg["provider"] == "gemini":
        return _call_gemini(cfg, prompt, temperature, max_tokens)
    return _call_openai_compatible(cfg, prompt, temperature, max_tokens)


def llm_json(prompt: str, **kwargs):
    """Call the configured LLM and return parsed JSON; LLMUnavailableError on failure."""
    raw = call_llm(prompt, **kwargs)
    try:
        return extract_json(raw)
    except (json.JSONDecodeError, LLMUnavailableError) as exc:
        logger.warning("Could not parse LLM JSON: %s", raw[:300])
        raise LLMUnavailableError(str(exc)) from exc


# ---------------------------------------------------------------------------
# Deprecated aliases — kept so existing imports/tests keep working.
# ---------------------------------------------------------------------------

def call_gemini(prompt: str, **kwargs) -> str:
    return call_llm(prompt, **kwargs)


def gemini_json(prompt: str, **kwargs):
    return llm_json(prompt, **kwargs)
