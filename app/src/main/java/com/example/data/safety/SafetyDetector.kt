package com.example.data.safety

import com.example.data.model.EmergencyResource
import com.example.data.model.SafetyAssessment

object SafetyDetector {

    private val violencePatterns = listOf(
        "kill", "murder", "stab", "shoot", "beat up", "punch", "hurt you", "break your neck",
        "put you in hospital", "physical harm", "assault", "strangle", "choke"
    )

    private val abusePatterns = listOf(
        "abuse", "abusive", "hit me", "slapped me", "locked me in", "domestic abuse",
        "afraid for my life", "threatens my family", "terrified of him", "terrified of her"
    )

    private val selfHarmPatterns = listOf(
        "kill myself", "suicide", "end my life", "don't want to live", "cut myself",
        "overdose", "slit my wrists", "want to die"
    )

    private val coercionPatterns = listOf(
        "blackmail", "extort", "or else I will leak", "ruin your life", "revenge porn",
        "forced into signing", "coerced"
    )

    val standardEmergencyResources = listOf(
        EmergencyResource(
            title = "National Domestic Violence Hotline",
            phoneNumber = "1-800-799-7233",
            textNumber = "Text START to 88788",
            description = "Free, confidential support available 24/7 for anyone experiencing domestic violence or relationship abuse.",
            url = "https://www.thehotline.org"
        ),
        EmergencyResource(
            title = "988 Suicide & Crisis Lifeline",
            phoneNumber = "988",
            textNumber = "Text 988",
            description = "Immediate 24/7 free and confidential emotional support for people in suicidal crisis or mental health-related distress.",
            url = "https://988lifeline.org"
        ),
        EmergencyResource(
            title = "Crisis Text Line",
            phoneNumber = "",
            textNumber = "Text HOME to 741741",
            description = "Connect with a trained crisis counselor 24/7 for free mental health crisis intervention via text.",
            url = "https://www.crisistextline.org"
        ),
        EmergencyResource(
            title = "Emergency Services",
            phoneNumber = "911",
            textNumber = "",
            description = "If you or anyone else is in immediate physical danger, please contact local emergency services immediately.",
            url = "https://www.emergency.gov"
        )
    )

    fun evaluateContent(text: String): SafetyAssessment {
        val lower = text.lowercase()
        val detected = mutableListOf<String>()

        if (selfHarmPatterns.any { lower.contains(it) }) {
            detected.add("Self-harm or crisis ideation")
        }
        if (violencePatterns.any { lower.contains(it) }) {
            detected.add("Explicit threats or physical violence")
        }
        if (abusePatterns.any { lower.contains(it) }) {
            detected.add("Domestic abuse or personal safety danger")
        }
        if (coercionPatterns.any { lower.contains(it) }) {
            detected.add("Coercion, blackmail, or unlawful extortion")
        }

        return if (detected.isNotEmpty()) {
            SafetyAssessment(
                isHighRisk = true,
                detectedRisks = detected,
                guidanceMessage = "Mediara AI has detected safety-critical language involving potential harm, threats, or abuse. Mediara AI is strictly an everyday interpersonal communication facilitator and is NOT an emergency service, legal authority, or mental health intervention tool. Active mediation has been paused to protect participant safety. Please access immediate human crisis support.",
                emergencyResources = standardEmergencyResources
            )
        } else {
            SafetyAssessment(
                isHighRisk = false,
                detectedRisks = emptyList(),
                guidanceMessage = "",
                emergencyResources = emptyList()
            )
        }
    }
}
