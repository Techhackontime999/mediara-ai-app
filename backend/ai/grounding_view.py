from rest_framework.response import Response
from rest_framework.views import APIView

from ai.balance import assess_balance
from ai.grounding import ground_proposals


class GroundingView(APIView):
    """GET /api/mediations/{pk}/grounding/

    Returns the deterministic grounding view for the active proposals (which
    participant statements each benefit / "why it works" traces back to) plus a
    real-time conversational-balance report. No LLM calls are made here, so it
    is cheap and always available for the app's transparency surfaces.
    """

    def get(self, request, pk):
        from mediation.models import Mediation, Participant
        from resolutions.models import Resolution

        mediation = Mediation.objects.filter(pk=pk).first()
        if mediation is None:
            return Response({"detail": "Mediation not found."}, status=404)
        if not Participant.objects.filter(mediation=mediation, user=request.user).exists():
            return Response({"detail": "Forbidden."}, status=403)

        proposals = list(Resolution.objects.filter(mediation=mediation, is_active=True).order_by("proposal_number"))
        perspectives = _perspective_payloads(mediation)

        grounded = ground_proposals(
            [
                {
                    "title": p.title,
                    "description": p.description,
                    "benefits": p.benefits,
                    "tradeoffs": p.tradeoffs,
                    "why_it_works": p.why_it_works,
                }
                for p in proposals
            ],
            perspectives,
        )
        for proposal, original in zip(grounded, proposals, strict=True):
            proposal["id"] = original.id
            proposal["proposalNumber"] = original.proposal_number
            proposal["modelType"] = original.model_type
        return Response({"groundedProposals": grounded, "balance": assess_balance(mediation)})


def _perspective_payloads(mediation):
    from resolutions.models import Perspective
    payloads = []
    for perspective in Perspective.objects.filter(mediation=mediation).select_related("participant__user"):
        payloads.append({
            "participant_name": (
                perspective.participant.user.name or perspective.participant.user.email
                if perspective.participant.user_id else (perspective.participant.name or "Participant")
            ),
            "goals": perspective.goals,
            "concerns": perspective.concerns,
            "needs": perspective.needs,
            "desired_outcome": perspective.desired_outcome,
            "acceptable_compromises": perspective.acceptable_compromises,
        })
    return payloads
