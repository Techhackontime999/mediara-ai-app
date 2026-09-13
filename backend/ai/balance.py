"""Conversational balance assessment.

Flags when one participant heavily dominates a mediation, so the mediator can
rebalance before proposals drift toward the louder party. Runs entirely
deterministically — no LLM dependency.
"""

from conversations.models import Message
from django.db.models import Count
from resolutions.models import Perspective


def assess_balance(mediation) -> dict:
    """Return a balance report based on private-caucus volume and perspective detail.

    output: {
      "balanced": bool,
      "samples": {participantName: {"messages": int, "perspectiveFields": int}},
      "dominantParticipant": name|null,
      "ratios": {name: float},            # each participant's share (0..1)
      "recommendation": str,
    }
    """
    rows = list(
        Message.objects.filter(conversation__mediation=mediation, sender="USER")
        .values("conversation__participant__user__name")
        .annotate(count=Count("id"))
        .order_by("-count")
    )
    samples = {
        str(r["conversation__participant__user__name"] or "participant"): {"messages": r["count"], "perspectiveFields": 0}
        for r in rows
    }
    for perspective in Perspective.objects.filter(mediation=mediation).select_related("participant__user"):
        key = perspective.participant.user.name or perspective.participant.user.email if perspective.participant.user_id else "participant"
        sample = samples.setdefault(key, {"messages": 0, "perspectiveFields": 0})
        sample["perspectiveFields"] = (
            len(perspective.goals or []) + len(perspective.concerns or []) + len(perspective.needs or [])
        )

    total = sum(s["messages"] for s in samples.values())
    if not samples or total == 0:
        return {
            "balanced": True,
            "samples": samples,
            "dominantParticipant": None,
            "ratios": {},
            "recommendation": "Collect caucus input from both parties before proceeding.",
        }

    ratios = {name: round(s["messages"] / total, 2) for name, s in samples.items()}
    dominant = max(ratios, key=ratios.get)
    share = ratios[dominant]

    # A single party owning >70% of the conversation, or any party with <15%,
    # is a rebalancing signal.
    min_share = min(ratios.values())
    balanced = share <= 0.7 and min_share >= 0.15
    recommendation = (
        "Conversation balance looks healthy; no individual dominates."
        if balanced
        else f"Flag for rebalancing: '{dominant}' currently drives {share:.0%} of the caucus. "
             "Invite quieter participants to elaborate on needs and compromises."
    )
    return {
        "balanced": balanced,
        "samples": samples,
        "dominantParticipant": dominant if not balanced else None,
        "ratios": ratios,
        "recommendation": recommendation,
    }
