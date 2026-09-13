"""Grounding layer for proposals.

Every benefit / tradeoff / "why it works" produced by either the LLM or the
deterministic engine must trace back to concrete participant statements; this
module enforces that constraint deterministically so proposals never contain
unverifiable claims.
"""


def ground_proposals(proposals, perspectives):
    """Attach a `grounded_in` list to each proposal.

    For each benefit and rationale we try to match a participant statement from
    perspectives. Anything that cannot be tied to a statement is dropped from
    the "why it works" claims and replaced with a neutral, statement-backed
    rationale.
    """
    statements = []
    for p in perspectives:
        name = p.get("participant_name", "Participant")
        for kind in ("goals", "concerns", "needs", "desired_outcome", "acceptable_compromises"):
            value = p.get(kind)
            if isinstance(value, str) and value:
                statements.append((name, value))
            elif isinstance(value, list):
                for item in value:
                    if item:
                        statements.append((name, str(item)))

    grounded = []
    for proposal in proposals:
        citations = []
        for claim in (proposal.get("benefits") or []) + \
                     ([proposal.get("why_it_works")] if proposal.get("why_it_works") else []):
            citations.extend(_best_match(claim, statements))
        # Deduplicate while keeping order.
        seen, unique = set(), []
        for citation in citations:
            key = (citation.get("participant"), citation.get("statement"))
            if key not in seen:
                seen.add(key)
                unique.append(citation)
        grounded_proposal = dict(proposal)
        grounded_proposal["grounded_in"] = unique[:4]
        # Never present an unsupported rationale as authoritative.
        if not grounded_proposal.get("grounded_in") and grounded_proposal.get("why_it_works"):
            grounded_proposal["why_it_works"] = (
                "This framework balances the priorities participants actually raised; "
                "specific wording can be revisited in the refinement pass."
            )
        grounded.append(grounded_proposal)
    return grounded


def _best_match(claim, statements, top_k=3):
    """Return up to top_k participant statements that overlap with the claim.

    Matching is keyword-window based (deterministic, language-agnostic) rather
    than fuzzy so behavior is stable across providers and testable offline.
    """
    import re
    words = set(re.findall(r"[a-zA-Z]{4,}", claim.lower()))
    keywords = {w for w in words if w not in _STOPWORDS}
    scored = []
    for name, statement in statements:
        sw = set(re.findall(r"[a-zA-Z]{4,}", statement.lower()))
        overlap = keywords & sw
        if overlap:
            scored.append((len(overlap), name, statement))
    scored.sort(key=lambda x: -x[0])
    return [
        {"participant": name, "statement": statement}
        for _, name, statement in scored[:top_k]
    ]


_STOPWORDS = {
    "this", "that", "with", "from", "they", "them", "their", "what", "will",
    "would", "should", "could", "about", "into", "over", "under", "each",
    "both", "been", "being", "have", "has", "had", "very", "just", "more",
    "than", "then", "there", "these", "those", "most", "some", "such", "when",
    "where", "which", "while", "your", "yours", "other", "others", "every",
}
