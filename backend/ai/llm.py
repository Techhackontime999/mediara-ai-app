"""Server-side LLM client for the Gemini API.

The API key lives only on the server. The native Android client never sees it,
which is why AI orchestration belongs here rather than in the app.
"""

import json
import logging
import re

import requests
from django.conf import settings

logger = logging.getLogger("mediara.ai")


class LLMUnavailableError(Exception):
    """Raised when the LLM cannot be reached and fallback should be used."""


def _strip_json_fence(raw: str) -> str:
    text = (raw or "").strip()
    text = re.sub(r"^```(?:json)?\s*", "", text).strip()
    text = re.sub(r"\s*```$", "", text).strip()
    return text


def extract_json(raw: str):
    """Extract the first JSON value (object or array) from an LLM response."""
    text = _strip_json_fence(raw)
    # try direct parse first
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass

    # find the outermost JSON object or array
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


def call_gemini(prompt: str, *, temperature: float = 0.4, max_tokens: int = 1600) -> str:
    """Return the text of a Gemini completion."""
    api_key = getattr(settings, "GEMINI_API_KEY", "") or ""
    if not api_key:
        raise LLMUnavailableError("GEMINI_API_KEY is not configured")

    model = getattr(settings, "GEMINI_MODEL", "gemini-2.5-flash")
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"

    payload = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {
            "temperature": temperature,
            "maxOutputTokens": max_tokens,
        },
    }
    try:
        resp = requests.post(
            url,
            params={"key": api_key},
            json=payload,
            timeout=45,
        )
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


def gemini_json(prompt: str, **kwargs):
    """Call Gemini and return parsed JSON, raising LLMUnavailableError on failure."""
    raw = call_gemini(prompt, **kwargs)
    try:
        return extract_json(raw)
    except (json.JSONDecodeError, LLMUnavailableError) as exc:
        logger.warning("Could not parse Gemini JSON: %s", raw[:300])
        raise LLMUnavailableError(str(exc)) from exc