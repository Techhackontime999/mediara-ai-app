"""High-risk conflict detection.

Mediara AI is designed for everyday interpersonal conflicts. It is NOT an
emergency service, legal advisor, or provider of professional mental-health
care. This module detects language that indicates a high-risk situation and
routes it to human/professional support rather than normal mediation.
"""

from dataclasses import dataclass, field


@dataclass
class SafetyAssessment:
    is_high_risk: bool
    detected_risks: list = field(default_factory=list)
    guidance_message: str = ""
    emergency_resources: list = field(default_factory=list)


VIOLENCE_PATTERNS = (
    "kill", "murder", "stab", "shoot", "beat up", "punch", "hurt you",
    "break your neck", "put you in hospital", "physical harm", "assault",
    "strangle", "choke",
)
ABUSE_PATTERNS = (
    "abuse", "abusive", "hit me", "slapped me", "locked me in", "domestic abuse",
    "afraid for my life", "threatens my family", "terrified of him", "terrified of her",
)
SELF_HARM_PATTERNS = (
    "kill myself", "suicide", "end my life", "don't want to live", "cut myself",
    "overdose", "slit my wrists", "want to die",
)
COERCION_PATTERNS = (
    "blackmail", "extort", "or else i will leak", "ruin your life",
    "revenge porn", "forced into signing", "coerced",
)

EMERGENCY_RESOURCES = [
    {
        "title": "Emergency Services",
        "phoneNumber": "911",
        "textNumber": "",
        "description": "If you or anyone else is in immediate physical danger, contact local emergency services immediately.",
        "url": "https://www.emergency.gov",
    },
    {
        "title": "988 Suicide & Crisis Lifeline",
        "phoneNumber": "988",
        "textNumber": "Text 988",
        "description": "Immediate 24/7 free and confidential emotional support for people in suicidal crisis or mental-health distress.",
        "url": "https://988lifeline.org",
    },
    {
        "title": "Crisis Text Line",
        "phoneNumber": "",
        "textNumber": "Text HOME to 741741",
        "description": "Connect with a trained crisis counselor 24/7 via text.",
        "url": "https://www.crisistextline.org",
    },
    {
        "title": "National Domestic Violence Hotline",
        "phoneNumber": "1-800-799-7233",
        "textNumber": "Text START to 88788",
        "description": "Free, confidential support for anyone experiencing domestic violence or relationship abuse.",
        "url": "https://www.thehotline.org",
    },
]

SAFETY_GUIDANCE = (
    "Mediara AI has detected language indicating a potentially high-risk "
    "situation. Mediara AI is strictly a mediation and communication platform "
    "for everyday interpersonal conflicts. It is NOT an emergency service, legal "
    "authority, or mental-health intervention tool. Mediation has been paused "
    "to protect participant safety. Please reach out to a human professional or "
    "crisis resource immediately."
)


def evaluate_content(text: str) -> SafetyAssessment:
    lower = (text or "").lower()
    detected = []
    if any(p in lower for p in SELF_HARM_PATTERNS):
        detected.append("Self-harm or crisis ideation")
    if any(p in lower for p in VIOLENCE_PATTERNS):
        detected.append("Explicit threats or physical violence")
    if any(p in lower for p in ABUSE_PATTERNS):
        detected.append("Domestic abuse or personal safety danger")
    if any(p in lower for p in COERCION_PATTERNS):
        detected.append("Coercion, blackmail, or unlawful extortion")

    if detected:
        return SafetyAssessment(
            is_high_risk=True,
            detected_risks=detected,
            guidance_message=SAFETY_GUIDANCE,
            emergency_resources=EMERGENCY_RESOURCES,
        )
    return SafetyAssessment(is_high_risk=False)
