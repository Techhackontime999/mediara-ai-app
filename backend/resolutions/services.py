"""Orchestration of the AI mediation pipeline (README section 6-10)."""

import logging

from ai import services as ai_services
from conversations.models import Message
from mediation.models import Mediation, MediationStatus, Participant
from .models import ConflictAnalysis, Perspective, Resolution, Vote

logger = logging.getLogger("mediara.resolutions")


def _perspectives_payload(perspective: Perspective) -> dict:
    return {
        "participant_id": perspective.participant_id,
        "participant_name": perspective.participant.user.name or perspective.participant.user.email,
        "goals": perspective.goals,
        "concerns": perspective.concerns,
        "needs": perspective.needs,
        "constraints": perspective.constraints,
        "emotions": perspective.emotions,
        "desired_outcome": perspective.desired_outcome,
        "acceptable_compromises": perspective.acceptable_compromises,
    }


def extract_and_store_perspectives(mediation: Mediation):
    """Extract a structured Perspective for every participant and persist it."""
    created = []
    for participant in mediation.participants.select_related("user").exclude(status="INVITED"):
        messages = Message.objects.filter(
            conversation__mediation=mediation,
            conversation__participant=participant,
            sender="USER",
        ).order_by("created_at")
        data = ai_services.extract_perspective(mediation, participant, list(messages))

        perspective, _ = Perspective.objects.update_or_create(
            mediation=mediation,
            participant=participant,
            defaults={
                "goals": data["goals"],
                "concerns": data["concerns"],
                "needs": data["needs"],
                "constraints": data["constraints"],
                "emotions": data["emotions"],
                "desired_outcome": data["desired_outcome"],
                "acceptable_compromises": data["acceptable_compromises"],
            },
        )
        created.append(perspective)
    return created


def run_analysis(mediation: Mediation) -> ConflictAnalysis:
    perspectives = extract_and_store_perspectives(mediation)
    if not perspectives:
        raise ValueError("No participant perspectives available yet.")

    payloads = [_perspectives_payload(p) for p in perspectives]
    data = ai_services.generate_conflict_analysis(mediation, payloads)

    analysis, _ = ConflictAnalysis.objects.update_or_create(
        mediation=mediation,
        defaults={
            "common_goals": data["common_goals"],
            "conflicting_goals": data["conflicting_goals"],
            "root_causes": data["root_causes"],
            "misunderstandings": data["misunderstandings"],
            "emotional_factors": data["emotional_factors"],
            "expectation_gaps": data["expectation_gaps"],
            "non_negotiable_concerns": data["non_negotiable_concerns"],
            "potential_compromises": data["potential_compromises"],
            "common_ground_summary": data["common_ground_summary"],
            "compatibility_score": data["compatibility_score"],
        },
    )
    return analysis


def run_generate_resolutions(mediation: Mediation) -> list[Resolution]:
    analysis = run_analysis(mediation)
    perspectives = list(Perspective.objects.filter(mediation=mediation).select_related("participant__user"))
    payloads = [_perspectives_payload(p) for p in perspectives]

    proposals_data = ai_services.generate_proposals(mediation, payloads, _analysis_payload(analysis))

    # Deactivate previous proposals so each generation starts clean for voting.
    Resolution.objects.filter(mediation=mediation).update(is_active=False)

    resolutions = []
    for data in proposals_data:
        resolution = Resolution.objects.create(
            mediation=mediation,
            proposal_number=data["proposal_number"],
            title=data["title"],
            model_type=data["model_type"],
            description=data["description"],
            benefits=data["benefits"],
            tradeoffs=data["tradeoffs"],
            required_compromises=data["required_compromises"] or {},
            expected_impact=data["expected_impact"],
            why_it_works=data["why_it_works"],
            grounded_in=data.get("grounded_in", []),
            is_active=True,
        )
        resolutions.append(resolution)

    mediation.status = MediationStatus.PROPOSALS_READY
    mediation.save(update_fields=["status"])
    return resolutions


def _analysis_payload(analysis: ConflictAnalysis) -> dict:
    return {
        "common_goals": analysis.common_goals or [],
        "conflicting_goals": analysis.conflicting_goals or [],
        "root_causes": analysis.root_causes or [],
        "misunderstandings": analysis.misunderstandings or [],
        "emotional_factors": analysis.emotional_factors or [],
        "expectation_gaps": analysis.expectation_gaps or [],
        "non_negotiable_concerns": analysis.non_negotiable_concerns or [],
        "potential_compromises": analysis.potential_compromises or [],
        "common_ground_summary": analysis.common_ground_summary,
        "compatibility_score": analysis.compatibility_score,
    }


def required_participants(mediation: Mediation):
    return mediation.participants.exclude(status="INVITED").select_related("user")


def all_approved(resolution: Resolution) -> bool:
    required = required_participants(resolution.mediation)
    if not required.exists():
        return False
    accepted = set(Vote.objects.filter(resolution=resolution, decision=Vote.Decision.ACCEPT).values_list("participant_id", flat=True))
    return all(p.id in accepted for p in required)


def cast_vote(resolution: Resolution, participant: Participant, decision: str, feedback: str):
    Vote.objects.update_or_create(
        resolution=resolution,
        participant=participant,
        defaults={"decision": decision, "feedback": feedback or ""},
    )

    mediation = resolution.mediation
    if all_approved(resolution):
        return resolution

    decisions = list(Vote.objects.filter(resolution=resolution).values_list("decision", flat=True))
    if Vote.Decision.REQUEST_CHANGES in decisions or Vote.Decision.REJECT in decisions:
        if mediation.status == MediationStatus.PROPOSALS_READY:
            mediation.status = MediationStatus.NEGOTIATING
            mediation.save(update_fields=["status"])
    return resolution


def refine_resolution(resolution: Resolution, feedback_summary: str) -> Resolution:
    original = {
        "title": resolution.title,
        "description": resolution.description,
        "benefits": resolution.benefits or [],
        "tradeoffs": resolution.tradeoffs or [],
        "required_compromises": resolution.required_compromises or {},
        "expected_impact": resolution.expected_impact,
        "why_it_works": resolution.why_it_works,
    }
    iteration = resolution.refinement_iteration + 1
    refined = ai_services.refine_proposal(original, feedback_summary, iteration)

    # Keep refined proposals grounded in participant statements, same as initial generation.
    perspectives = [_perspectives_payload(p) for p in
                    Perspective.objects.filter(mediation=resolution.mediation).select_related("participant__user")]
    grounded = ai_services.ground_proposal(refined, perspectives)

    Resolution.objects.filter(mediation=resolution.mediation).update(is_active=False)
    new_resolution = Resolution.objects.create(
        mediation=resolution.mediation,
        proposal_number=resolution.proposal_number,
        title=grounded["title"],
        model_type=resolution.model_type,
        description=grounded["description"],
        benefits=grounded["benefits"],
        tradeoffs=grounded["tradeoffs"],
        required_compromises=grounded["required_compromises"] or {},
        expected_impact=grounded["expected_impact"],
        why_it_works=grounded["why_it_works"],
        grounded_in=grounded.get("grounded_in", []),
        refinement_iteration=iteration,
        is_active=True,
    )

    mediation = resolution.mediation
    mediation.status = MediationStatus.PROPOSALS_READY
    mediation.save(update_fields=["status"])
    return new_resolution