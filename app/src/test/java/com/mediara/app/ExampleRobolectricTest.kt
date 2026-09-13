package com.mediara.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mediara.app.data.model.AgreementMilestone
import com.mediara.app.data.model.ConflictAnalysis
import com.mediara.app.data.model.Mediation
import com.mediara.app.data.model.MutualAgreement
import com.mediara.app.data.model.Participant
import com.mediara.app.data.model.ResolutionProposal
import com.mediara.app.data.pdf.PdfAgreementGenerator
import com.mediara.app.data.safety.SafetyDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Mediara AI", appName)
  }

  @Test
  fun `safety detector flags high risk violence and provides emergency hotline`() {
    val result = SafetyDetector.evaluateContent("I am terrified he will hurt you and assault me")
    assertTrue(result.isHighRisk)
    assertTrue(result.emergencyResources.isNotEmpty())
  }

  @Test
  fun `safety detector allows constructive mediation conversation`() {
    val result = SafetyDetector.evaluateContent("I want us to agree on quiet hours at 10 PM and split cleaning.")
    assertEquals(false, result.isHighRisk)
  }

  @Test
  fun `constructed mediation model carries full case data`() {
    val mediation = sampleMediation()
    assertEquals(2, mediation.participants.size)
    assertTrue(mediation.proposals.size == 3)
    assertTrue(mediation.analysis != null)
    assertTrue(mediation.analysis!!.commonGoals.isNotEmpty())
  }

  @Test
  fun `generate official resolution accord pdf creates valid file`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val mediation = sampleMediation()
    val agreement = MutualAgreement(
      id = "agr-1",
      mediationId = mediation.id,
      proposalId = mediation.proposals.first().id,
      resolutionTitle = "Equal Distribution Framework",
      preamble = "This accord is ratified by all participants.",
      agreedTerms = listOf("Quiet hours from 10 PM.", "Cleaning duties split weekly."),
      participantResponsibilities = mapOf(
        "Alex" to listOf("Clean kitchen Wednesdays."),
        "Jordan" to listOf("Take out trash Sundays.")
      ),
      milestones = listOf(
        AgreementMilestone(id = "m1", title = "First review", dueDate = "Day 14", assignedTo = "Both")
      ),
      disputeEscalationClause = "Parties may reopen mediation before external action.",
      signatures = mapOf("p1" to 1000L, "p2" to 2000L),
      isFullySigned = true
    )

    val pdfFile = PdfAgreementGenerator.generateAgreementPdf(context, mediation, agreement)
    assertTrue(pdfFile.exists())
    assertTrue(pdfFile.length() > 0)
  }

  private fun sampleMediation(): Mediation {
    val participant1 = Participant(
      id = "p1",
      userId = "u1",
      mediationId = "m1",
      name = "Alex",
      email = "alex@example.com",
      role = "Roommate A"
    )
    val participant2 = Participant(
      id = "p2",
      userId = "u2",
      mediationId = "m1",
      name = "Jordan",
      email = "jordan@example.com",
      role = "Roommate B"
    )
    val analysis = ConflictAnalysis(
      id = "a1",
      mediationId = "m1",
      commonGoals = listOf("Peaceful shared home"),
      conflictingGoals = listOf("Late-night studying vs early-morning work"),
      potentialCompromises = listOf("Fixed quiet hours")
    )
    val proposal: (Int, String) -> ResolutionProposal = { idx, title ->
      ResolutionProposal(
        id = "r$idx",
        mediationId = "m1",
        proposalNumber = idx,
        title = title,
        modelType = "Balanced",
        description = "A fair framework for shared living.",
        benefits = listOf("Clarity", "Fairness"),
        tradeoffs = listOf("Flexibility"),
        whyItWorks = "Clear expectations reduce friction."
      )
    }
    val proposals = listOf(
      proposal(1, "Equal Distribution"),
      proposal(2, "Process & Cadence"),
      proposal(3, "Responsibility Ownership")
    )
    return Mediation(
      id = "m1",
      title = "Roommate Conflict",
      description = "Quiet hours and chores dispute.",
      category = "Roommates",
      inviteCode = "ABCD12",
      creatorId = "u1",
      participants = listOf(participant1, participant2),
      analysis = analysis,
      proposals = proposals
    )
  }
}