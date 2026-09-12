"""Mediara AI mediation engine.

Every function tries the LLM first (whichever provider is configured — Gemini,
or any OpenAI-compatible endpoint set via AI_BASE_URL) and falls back to a
deterministic, high-quality built-in engine so the full product flow always
works, even offline or without a key.
"""

import logging

from . import prompts
from .grounding import ground_proposals
from .llm import LLMUnavailableError, call_llm, is_llm_enabled, llm_json
from .safety import evaluate_content

logger = logging.getLogger("mediara.ai")


# ---------------------------------------------------------------------------
# Private session
# ---------------------------------------------------------------------------

def private_session_reply(mediation, participant, history, latest_message):
    """Return (reply_text, detected_category) for a participant's private message."""
    safety = evaluate_content(latest_message)
    if safety.is_high_risk:
        return high_risk_reply(safety), "SAFETY_ALERT"

    if is_llm_enabled():
        try:
            history_str = "\n".join(
                f"{m.sender}: {m.content}" for m in history[-6:]
            )
            prompt = prompts.PRIVATE_SESSION_SYSTEM.format(
                participant_name=participant.user.name or participant.user.email,
                participant_role=participant.role,
                title=mediation.title,
                description=mediation.description,
                category=mediation.category,
                history=history_str,
                message=latest_message,
            )
            text = call_llm(prompt, temperature=0.6)
            if text:
                return text, detect_category(latest_message)
        except LLMUnavailableError as exc:
            logger.debug("LLM unavailable for private session: %s", exc)

    reply = built_in_session_reply(history, latest_message, participant)
    return reply, detect_category(latest_message)


def high_risk_reply(safety):
    resources = safety.emergency_resources or []
    first = resources[0] if resources else None
    line = ""
    if first and first["phoneNumber"]:
        line = f" For immediate support, contact {first['title']} at {first['phoneNumber']}."
    return (
        "⚠️ Safety Notice: I noticed language that could indicate danger to your "
        "safety or well-being. Mediara AI is not an emergency, legal, or crisis "
        "intervention service, so I must pause this mediation session. "
        "Please reach out to a qualified human professional right away." + line
    )


def detect_category(msg: str) -> str:
    lower = msg.lower()
    if any(k in lower for k in ("goal", "want to", "hope to", "aim")):
        return "GOAL"
    if any(k in lower for k in ("worry", "concern", "afraid", "frustrat")):
        return "CONCERN"
    if any(k in lower for k in ("feel", "angry", "upset", "hurt")):
        return "EMOTION"
    if any(k in lower for k in ("need", "must have", "require", "essential")):
        return "NEED"
    if any(k in lower for k in ("compromise", "willing to", "flexible", "agree to")):
        return "COMPROMISE"
    return "GENERAL"


def built_in_session_reply(history, msg, participant):
    user_count = sum(1 for m in history if m.sender == "USER")
    name = participant.user.name or participant.user.email
    if user_count <= 1:
        return (
            f"Thank you for opening up, {name}. I hear how important this is to you, "
            "and it takes courage to address it openly. To help me understand deeper: "
            "how has this situation been impacting your day-to-day peace of mind, and "
            "what feels most unfair right now?"
        )
    if user_count == 2:
        return (
            "That gives me essential perspective. When interpersonal conflicts arise, "
            "frustration often stems from unmet core needs or unacknowledged efforts. "
            "From your viewpoint, what do you feel the other party might be misunderstanding "
            "about your actions or intentions?"
        )
    if user_count == 3:
        return (
            "That is a crucial distinction. People often assume malice when there is simply "
            "a gap in expectations. Looking toward a sustainable resolution: what would an "
            "outcome that feels truly balanced and fair look like to you?"
        )
    if user_count == 4:
        return (
            "I appreciate your clarity on what matters most. In healthy resolutions, both "
            "parties usually take one meaningful step toward the middle. What is one area "
            "or boundary where you would be willing to be flexible or offer a compromise?"
        )
    return (
        f"Thank you, {name}. You have provided clear, actionable insights into your goals, "
        "needs, and potential compromises. I now have enough information to help analyze "
        "common ground and construct practical resolution proposals. You can conclude this "
        "private session whenever you are ready."
    )


# ---------------------------------------------------------------------------
# Perspective extraction
# ---------------------------------------------------------------------------

def extract_perspective(mediation, participant, messages, existing=None):
    """Return a normalized perspective dict for one participant."""
    user_messages = [m.content for m in messages if m.sender == "USER"]
    full_text = "\n".join(user_messages)
    fallback = build_fallback_perspective(mediation, participant, user_messages)

    if full_text.strip() and is_llm_enabled():
        try:
            prompt = prompts.PERSPECTIVE_EXTRACTION.format(
                title=mediation.title,
                participant_name=participant.user.name or participant.user.email,
                participant_role=participant.role,
                messages=full_text,
            )
            data = llm_json(prompt, temperature=0.3)
            cleaned = _clean_expected(data)
            return {
                "goals": cleaned.get("goals") or fallback["goals"],
                "concerns": cleaned.get("concerns") or fallback["concerns"],
                "needs": cleaned.get("needs") or fallback["needs"],
                "constraints": cleaned.get("constraints") or fallback["constraints"],
                "emotions": cleaned.get("emotions") or fallback["emotions"],
                "desired_outcome": cleaned.get("desired_outcome") or fallback["desired_outcome"],
                "acceptable_compromises": cleaned.get("acceptable_compromises") or fallback["acceptable_compromises"],
            }
        except LLMUnavailableError as exc:
            logger.debug("LLM unavailable for perspective: %s", exc)

    return fallback


def _clean_expected(data):
    """Map LLM JSON keys to our canonical keys (accept snake+camel)."""
    if not isinstance(data, dict):
        return {}
    out = {}
    mapping = {
        "goals": ("goals",),
        "concerns": ("concerns",),
        "needs": ("needs",),
        "constraints": ("constraints",),
        "emotions": ("emotions",),
        "desired_outcome": ("desired_outcome", "desiredOutcome"),
        "acceptable_compromises": ("acceptable_compromises", "acceptableCompromises"),
    }
    for canonical, aliases in mapping.items():
        for alias in aliases:
            if alias in data and data[alias]:
                out[canonical] = data[alias]
                break
    for key in ("goals", "concerns", "needs", "constraints", "emotions", "acceptable_compromises"):
        if key not in out:
            out[key] = []
    return out


def build_fallback_perspective(mediation, participant, user_messages):
    category = f"{mediation.category} {mediation.title}".lower()
    if "room" in category or "roommate" in category or "apartment" in category or "family" in category:
        base = {
            "goals": ["Establish predictable shared-living routines", "Maintain clean, calm shared spaces"],
            "concerns": ["Uneven distribution of responsibilities", "Disturbed quiet hours and sleep"],
            "needs": ["Consistent accountability", "Advance notice for guest visits"],
        }
        compromises = ["Willing to alternate chore schedules", "Flexible on weekend noise until 11 PM"]
    elif "work" in category or "founder" in category or "startup" in category or "project" in category or "team" in category:
        base = {
            "goals": ["Align priorities and milestone commitments", "Maintain professional respect and trust"],
            "concerns": ["Unclear decision-making ownership", "Bottlenecks in task execution"],
            "needs": ["Clear autonomy over assigned domains", "Regular alignment and check-ins"],
        }
        compromises = ["Open to bi-weekly progress checkpoints", "Willing to accept peer review of deliverables"]
    else:
        base = {
            "goals": ["Restore mutual trust and clear communication", "Resolve ongoing tension constructively"],
            "concerns": ["Feeling ignored or dismissed", "Recurring misinterpretations of intent"],
            "needs": ["Direct, calm communication", "Respect for personal boundaries"],
        }
        compromises = ["Willing to listen without interrupting", "Agreeable to scheduled check-ins"]

    return {
        "goals": base["goals"],
        "concerns": base["concerns"],
        "needs": base["needs"],
        "constraints": ["Limited weekday bandwidth", "Concurrent life/work responsibilities"],
        "emotions": ["Frustrated", "Hopeful for a genuine resolution"],
        "desired_outcome": "A transparent arrangement where expectations are explicit and respected by everyone.",
        "acceptable_compromises": compromises,
    }


# ---------------------------------------------------------------------------
# Conflict analysis
# ---------------------------------------------------------------------------

def generate_conflict_analysis(mediation, perspectives):
    """Return a normalized conflict-analysis dict synthesizing perspectives."""
    fallback = build_fallback_analysis(mediation, perspectives)

    if perspectives and is_llm_enabled():
        try:
            summary = "\n---\n".join(
                (
                    f"Participant: {p['participant_name']}\n"
                    f"Goals: {', '.join(p['goals'])}\n"
                    f"Concerns: {', '.join(p['concerns'])}\n"
                    f"Needs: {', '.join(p['needs'])}\n"
                    f"Emotions: {', '.join(p['emotions'])}\n"
                    f"Desired outcome: {p['desired_outcome']}\n"
                    f"Compromises: {', '.join(p['acceptable_compromises'])}"
                )
                for p in perspectives
            )
            prompt = prompts.CONFLICT_ANALYSIS.format(
                title=mediation.title,
                description=mediation.description,
                perspectives=summary,
            )
            data = llm_json(prompt, temperature=0.3)
            return {
                "common_goals": _as_list(data.get("common_goals") or data.get("commonGoals")),
                "conflicting_goals": _as_list(data.get("conflicting_goals") or data.get("conflictingGoals")),
                "root_causes": _as_list(data.get("root_causes") or data.get("rootCauses")),
                "misunderstandings": _as_list(data.get("misunderstandings")),
                "emotional_factors": _as_list(data.get("emotional_factors") or data.get("emotionalFactors")),
                "expectation_gaps": _as_list(data.get("expectation_gaps") or data.get("expectationGaps")),
                "non_negotiable_concerns": _as_list(data.get("non_negotiable_concerns") or data.get("nonNegotiableConcerns")),
                "potential_compromises": _as_list(data.get("potential_compromises") or data.get("potentialCompromises")),
                "common_ground_summary": data.get("common_ground_summary") or fallback["common_ground_summary"],
                "compatibility_score": _coerce_int(data.get("compatibility_score"), fallback["compatibility_score"]),
            }
        except LLMUnavailableError as exc:
            logger.debug("LLM unavailable for analysis: %s", exc)

    return fallback


def _as_list(value):
    if isinstance(value, list):
        return [str(v) for v in value]
    if value is None:
        return []
    return [str(value)]


def _coerce_int(value, default):
    try:
        return max(0, min(100, int(value)))
    except (TypeError, ValueError):
        return default


def build_fallback_analysis(mediation, perspectives):
    return {
        "common_goals": [
            "Everyone wants a predictable, peaceful outcome free of recurring hostility.",
            "Everyone wants clear, objective agreements rather than vague spoken understandings.",
            "Everyone values mutual respect and preserving the relationship.",
        ],
        "conflicting_goals": [
            "Different preferred communication cadence (spontaneous vs. scheduled).",
            "Different standards of timeliness and task-completion benchmarks.",
        ],
        "root_causes": [
            "Unspoken initial expectations were never codified explicitly.",
            "Small instances of friction went unaddressed until they spiraled.",
        ],
        "misunderstandings": [
            "Brief or quiet responses were read as apathy instead of fatigue.",
            "Independent decisions were perceived as deliberate exclusion.",
        ],
        "emotional_factors": [
            "Feelings of unacknowledged effort and emotional exhaustion.",
            "Defensiveness triggered by past criticism.",
        ],
        "expectation_gaps": [
            "Different definitions of key terms such as 'on time', 'clean', or 'fair share'.",
        ],
        "non_negotiable_concerns": [
            "Unannounced disruptions of personal boundaries.",
            "Unilateral changes to shared agreements.",
        ],
        "potential_compromises": [
            "Adopting an objective checklist or shared calendar.",
            "Scheduling a short weekly check-in.",
        ],
        "common_ground_summary": (
            "Both parties share significant common ground: they want structure, mutual "
            "dignity, and fairness. The primary divergence is operational habit and "
            "communication timing, which is readily addressable through a clear agreement."
        ),
        "compatibility_score": 82,
    }


# ---------------------------------------------------------------------------
# Resolution generation
# ---------------------------------------------------------------------------

def generate_proposals(mediation, perspectives, analysis):
    """Return a list of exactly 3 proposal dicts."""
    fallback = build_fallback_proposals(mediation, perspectives, analysis)
    if not is_llm_enabled():
        return fallback

    try:
        names = [p["participant_name"] for p in perspectives] or ["Party A", "Party B"]
        ref = names + ["Party A", "Party B"]
        prompt = prompts.RESOLUTION_GENERATION.format(
            title=mediation.title,
            category=mediation.category,
            common_goals=", ".join(analysis.get("common_goals", [])),
            common_ground=analysis.get("common_ground_summary", ""),
            participants=", ".join(names),
            participant_a=ref[0],
            participant_b=ref[1],
        )
        data = llm_json(prompt, temperature=0.5)
        if isinstance(data, dict):
            data = data.get("proposals") or data.get("resolutions") or []
        if not isinstance(data, list) or len(data) < 3:
            raise LLMUnavailableError("Expected 3 proposals")
        proposals = []
        for i, item in enumerate(data[:3]):
            model_type = item.get("modelType", item.get("model_type"))
            if model_type is None:
                model_type = ["Balanced Compromise Model", "Process & Cadence Model", "Responsibility Ownership Model"][i]
            proposals.append({
                "proposal_number": _coerce_int(item.get("proposalNumber", i + 1), i + 1),
                "title": item.get("title") or f"Proposal {i + 1}",
                "model_type": model_type,
                "description": item.get("description", ""),
                "benefits": _as_list(item.get("benefits")),
                "tradeoffs": _as_list(item.get("tradeoffs")),
                "required_compromises": item.get("requiredCompromises", item.get("required_compromises", {})) or {},
                "expected_impact": item.get("expectedImpact", item.get("expected_impact", "")),
                "why_it_works": item.get("whyItWorks", item.get("why_it_works", "")),
            })
        if len(proposals) == 3:
            return ground_proposals(proposals, perspectives)
    except LLMUnavailableError as exc:
        logger.debug("LLM unavailable for proposals: %s", exc)
    return ground_proposals(fallback, perspectives)


def build_fallback_proposals(mediation, perspectives, analysis):
    names = [p["participant_name"] for p in perspectives] or ["Party A", "Party B"]
    a, b = names[0], names[1] if len(names) > 1 else "Party B"

    reopened = getattr(mediation, "reopened_context", None) or {}
    if reopened.get("accepted_proposal"):
        # A previous agreement failed in practice: lead with a corrective pass
        # that references the exact points that were flagged, without exposing
        # any private statements.
        return [
            {
                "proposal_number": 1,
                "title": "Corrective Pass on Previous Agreement",
                "model_type": "Balanced Compromise Model",
                "description": (
                    f"A prior agreement ('{reopened['accepted_proposal']}') was reported as "
                    "not holding up. This proposal diagnoses the specific breakdowns called out "
                    "by participants and rebuilds the agreement around fixable mechanics, "
                    "e.g. clearer escalation steps and a defined remedy timeline."
                ),
                "benefits": [
                    "Directly addresses the exact clauses participants flagged",
                    "Adds a defined remedy and re-check schedule",
                    "Rebuilds trust with a visible corrective intent",
                ],
                "tradeoffs": [
                    "Participants must re-commit to new mechanics",
                    "Takes one fresh agreement cycle to feel normal",
                ],
                "required_compromises": {
                    a: "Accepts a specific remedy timeline instead of open-ended patience.",
                    b: "Accepts scheduling one follow-up re-check in the agreement.",
                },
                "expected_impact": "Closes the previously observed failure mode within one check-in cycle.",
                "why_it_works": "The prior agreement was re-opened because its mechanics lapsed; "
                               "a concrete remedy and re-check schedule directly targets the reported failure.",
            },
            {
                "proposal_number": 2,
                "title": "Cadence Reinforcements",
                "model_type": "Process & Cadence Model",
                "description": "Restores a lightweight weekly cadence with a hard 24-hour cooling-off rule and a monthly self-review.",
                "benefits": ["Prevents drift between check-ins", "Gives each party a voice at a fixed time"],
                "tradeoffs": ["Requires meeting discipline", "Adds one recurring calendar slot"],
                "required_compromises": {
                    a: "Returns concerns at the sync rather than in reactive asides.",
                    b: "Attends the weekly sync unless a plan is agreed in advance.",
                },
                "expected_impact": "Establishes the regularity that was missing after the first resolution.",
                "why_it_works": "Re-openings typically follow cadence drift; restoring structure is the highest-leverage fix.",
            },
            {
                "proposal_number": 3,
                "title": "Outcome-Locked Ownership",
                "model_type": "Responsibility Ownership Model",
                "description": "Assigns each party measurable ownership for one explicit outcome and ties the monthly review to those outcomes.",
                "benefits": ["Measurable, verifiable commitments", "Reduces ambiguity about who does what"],
                "tradeoffs": ["Needs clear metrics up front", "Less room for improvisation"],
                "required_compromises": {
                    a: "Accepts outcome metrics defined jointly ahead of time.",
                    b: "Accepts being answerable to the agreed metric each month.",
                },
                "expected_impact": "Turns good intentions into behaviour that can actually be checked.",
                "why_it_works": "Reported reopening reasons lose force when outcomes are explicit and reviewable.",
            },
        ]
    return [
        {
            "proposal_number": 1,
            "title": "Balanced Rotation & Equal Division",
            "model_type": "Balanced Compromise Model",
            "description": "A strictly symmetrical division of tasks, time, and responsibilities on a clear alternating rhythm.",
            "benefits": [
                "Absolute parity and perceived fairness",
                "Zero ambiguity about whose turn it is",
                "Eliminates subjective feelings of imbalance",
            ],
            "tradeoffs": [
                "Requires keeping track of the alternating schedule",
                "Less flexibility for unexpected emergencies",
            ],
            "required_compromises": {
                a: "Accepts strict alternating weeks with 24-hour notice for swaps.",
                b: "Commits to completing assigned duties without reminders.",
            },
            "expected_impact": "Rapid reduction of daily friction through mathematically equal division.",
            "why_it_works": "Everyone expressed a desire for unambiguous fairness and shared accountability.",
        },
        {
            "proposal_number": 2,
            "title": "Cadence, Checkpoints & Ground Rules",
            "model_type": "Process & Cadence Model",
            "description": "A short recurring check-in plus codified ground rules with clear quiet times and a cooling-off period.",
            "benefits": [
                "High predictability for everyone's schedule",
                "Prevents grievances from accumulating silently",
                "Built-in 24-hour cool-down before addressing sensitive topics",
            ],
            "tradeoffs": [
                "Requires regular meeting discipline",
                "May feel slightly formal at first",
            ],
            "required_compromises": {
                a: "Raises concerns during the sync rather than in reactive moments.",
                b: "Attends the weekly 15-minute sync with an open, constructive mindset.",
            },
            "expected_impact": "Creates a safe recurring container to calibrate expectations before tension flares.",
            "why_it_works": "Misunderstandings arose from reactive communication under stress; a set cadence resolves this.",
        },
        {
            "proposal_number": 3,
            "title": "Distinct Domain Ownership",
            "model_type": "Responsibility Ownership Model",
            "description": "Each participant fully owns specific domains, with total autonomy over their area and clear accountability.",
            "benefits": [
                "Maximizes individual strengths and preferences",
                "No daily coordination or nagging",
                "Complete freedom within each owned domain",
            ],
            "tradeoffs": [
                "Domains must be rebalanced if one becomes much heavier",
                "Requires trusting the other's execution style",
            ],
            "required_compromises": {
                a: "Relinquishes micromanagement of the other's domains.",
                b: "Accepts full accountability for the domains they own.",
            },
            "expected_impact": "High autonomy and minimal micro-friction.",
            "why_it_works": "Frustration came from being micromanaged or questioned on one's own work.",
        },
    ]


def ground_proposal(proposal, perspectives):
    """Ground a single proposal dict (used after refinement)."""
    grounded = ground_proposals([proposal], perspectives)
    return grounded[0] if grounded else proposal


# ---------------------------------------------------------------------------
# Refinement
# ---------------------------------------------------------------------------

def refine_proposal(original, feedback_summary, iteration):
    """Return an updated proposal dict integrating participant feedback."""
    fallback = {
        "title": f"{original['title']} (Refined v{iteration})",
        "description": f"{original['description']}\n\nAdjusted based on participant feedback: {feedback_summary}",
        "benefits": original.get("benefits", []) + ["Addresses explicit participant feedback", "Clarifies boundary expectations"],
        "tradeoffs": [t for t in original.get("tradeoffs", []) if "rigid" not in t.lower()] + ["Requires an active check-in commitment"],
        "required_compromises": original.get("required_compromises", {}),
        "expected_impact": "Higher mutual buy-in because both sides' sticking points were integrated.",
        "why_it_works": f"Directly bridges the sticking points identified in iteration {iteration - 1}.",
    }
    if not is_llm_enabled():
        return fallback

    try:
        prompt = prompts.REFINEMENT.format(
            title=original["title"],
            description=original["description"],
            benefits=", ".join(original.get("benefits", [])),
            tradeoffs=", ".join(original.get("tradeoffs", [])),
            feedback=feedback_summary,
        )
        data = llm_json(prompt, temperature=0.4)
        refined = {
            "title": data.get("title") or fallback["title"],
            "description": data.get("description") or fallback["description"],
            "benefits": _as_list(data.get("benefits")) or original.get("benefits", []),
            "tradeoffs": _as_list(data.get("tradeoffs")) or original.get("tradeoffs", []),
            "required_compromises": data.get("requiredCompromises", data.get("required_compromises", {}))
            or original.get("required_compromises", {}),
            "expected_impact": data.get("expectedImpact", data.get("expected_impact", fallback["expected_impact"])),
            "why_it_works": data.get("whyItWorks", data.get("why_it_works", fallback["why_it_works"])),
        }
        return refined
    except LLMUnavailableError as exc:
        logger.debug("LLM unavailable for refinement: %s", exc)
        return fallback