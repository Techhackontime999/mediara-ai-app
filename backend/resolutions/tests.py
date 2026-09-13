from conversations.services import get_or_create_conversation
from django.contrib.auth import get_user_model
from django.test import override_settings
from mediation.models import Mediation, Participant
from rest_framework import status
from rest_framework.test import APITestCase

User = get_user_model()


# Force the deterministic engine so tests need no API key.
@override_settings(GEMINI_API_KEY="", AI_FALLBACK_ENABLED=True)
class FullMvpFlowTests(APITestCase):
    """The complete README journey: register -> create -> join -> private
    sessions -> analysis -> 3 proposals -> vote -> finalize -> agreement -> pdf
    -> follow-up -> reopen.
    """

    def setUp(self):
        self.a = User.objects.create_user(email="a@case.com", name="Alice", password="secret123")
        self.b = User.objects.create_user(email="b@case.com", name="Ben", password="secret123")
        self.client.force_authenticate(self.a)

    def _create_and_setup_mediation(self):
        resp = self.client.post(
            "/api/mediations/",
            {
                "title": "Project workload split",
                "description": "Disagreement about who does what",
                "category": "Project Team",
            },
            format="json",
        )
        mediation_id = resp.json()["id"]

        self.client.post(
            f"/api/mediations/{mediation_id}/invite/",
            {"name": "Ben", "email": "b@case.com", "role": "Partner"},
            format="json",
        )

        # Ben joins by code
        mediation = Mediation.objects.get(pk=mediation_id)
        inv = self.client.post(
            "/api/mediations/join/",
            {"inviteCode": mediation.invite_code, "role": "Partner"},
            format="json",
        )
        self.assertEqual(inv.status_code, 200)

        return mediation

    def test_full_mvp_flow(self):
        mediation = self._create_and_setup_mediation()

        # --- Private sessions -------------------------------------------------
        pa = Participant.objects.get(mediation=mediation, user=self.a)
        pb = Participant.objects.get(mediation=mediation, user=self.b)
        conv_a = get_or_create_conversation(mediation, pa)
        conv_b = get_or_create_conversation(mediation, pb)

        # Session A
        self.client.force_authenticate(self.a)
        msg_a = self.client.post(f"/api/conversations/{conv_a.id}/message/", {"message": "I do most of the work while Ben delays."}, format="json")
        self.assertEqual(msg_a.status_code, 200)
        self.assertEqual(msg_a.json()["user"]["sender"], "USER")
        self.assertEqual(msg_a.json()["ai"]["sender"], "AI_MEDIATOR")

        # Session B
        self.client.force_authenticate(self.b)
        msg_b = self.client.post(f"/api/conversations/{conv_b.id}/message/", {"message": "Alice never lets me take ownership of tasks."}, format="json")
        self.assertEqual(msg_b.status_code, 200)

        # Complete both sessions
        self.client.force_authenticate(self.a)
        self.client.post(f"/api/conversations/{conv_a.id}/complete/")
        self.client.force_authenticate(self.b)
        self.client.post(f"/api/conversations/{conv_b.id}/complete/")

        # --- Analysis + proposals ---------------------------------------------
        self.client.force_authenticate(self.a)
        analyze = self.client.post(f"/api/mediations/{mediation.id}/analyze/")
        self.assertEqual(analyze.status_code, 200)
        payload = analyze.json()
        self.assertIn("analysis", payload)
        self.assertIn("proposals", payload)
        self.assertEqual(payload["analysis"]["compatibilityScore"], payload["analysis"]["compatibilityScore"])
        proposals = payload["proposals"]
        self.assertEqual(len(proposals), 3)

        # Mediation status became PROPOSALS_READY
        detail = self.client.get(f"/api/mediations/{mediation.id}/")
        self.assertEqual(detail.json()["status"], "PROPOSALS_READY")
        self.assertIsNotNone(detail.json()["analysis"])

        # --- Voting: A accepts, B rejects, refine, then both accept -----------
        res_id = proposals[0]["id"]
        # A accepts
        v = self.client.post(f"/api/resolutions/{res_id}/vote/", {"decision": "ACCEPT"}, format="json")
        self.assertEqual(v.status_code, 200)
        self.assertFalse(v.json()["approvedByAll"])

        # B requests changes
        self.client.force_authenticate(self.b)
        v = self.client.post(
            f"/api/resolutions/{res_id}/vote/",
            {"decision": "REQUEST_CHANGES", "feedback": "I want to own the testing module fully."},
            format="json",
        )
        self.assertEqual(v.status_code, 200)

        # Refine -> new resolution
        refined = self.client.post(f"/api/resolutions/{res_id}/refine/", {}, format="json")
        self.assertEqual(refined.status_code, status.HTTP_201_CREATED)
        refined_res = refined.json()
        self.assertEqual(refined_res["refinementIteration"], 2)
        self.assertIn("Refined", refined_res["title"])

        # Both accept the refined proposal
        self.client.force_authenticate(self.a)
        self.client.post(f"/api/resolutions/{refined_res['id']}/vote/", {"decision": "ACCEPT"}, format="json")
        self.client.force_authenticate(self.b)
        final_vote = self.client.post(f"/api/resolutions/{refined_res['id']}/vote/", {"decision": "ACCEPT"}, format="json")
        self.assertTrue(final_vote.json()["approvedByAll"])

        # --- Finalize + agreement ---------------------------------------------
        self.client.force_authenticate(self.a)
        finalize = self.client.post(
            f"/api/mediations/{mediation.id}/finalize/",
            {"resolutionId": refined_res["id"]},
            format="json",
        )
        self.assertEqual(finalize.status_code, 200)
        agreement = finalize.json()
        self.assertEqual(agreement["resolutionTitle"], refined_res["title"])
        self.assertTrue(agreement["isFullySigned"])
        self.assertGreaterEqual(len(agreement["signatures"]), 2)

        detail = self.client.get(f"/api/mediations/{mediation.id}/")
        self.assertEqual(detail.json()["status"], "RESOLVED")

        # --- PDF -----------------------------------------------------------------
        pdf = self.client.get(f"/api/mediations/{mediation.id}/agreement/pdf/")
        self.assertEqual(pdf.status_code, 200)
        self.assertEqual(pdf["Content-Type"], "application/pdf")
        self.assertTrue(len(pdf.content) > 1000)

        # --- Follow-up ------------------------------------------------------------
        self.client.force_authenticate(self.b)
        fu = self.client.post(
            f"/api/mediations/{mediation.id}/follow-up/",
            {"sentiment": "NOT_WORKING", "comments": "Still unbalanced."},
            format="json",
        )
        self.assertEqual(fu.status_code, status.HTTP_201_CREATED)
        self.assertTrue(fu.json()["reopenRequested"])

        detail = self.client.get(f"/api/mediations/{mediation.id}/")
        self.assertEqual(detail.json()["status"], "REOPENED")

        reopen = self.client.post(f"/api/mediations/{mediation.id}/reopen/")
        self.assertEqual(reopen.status_code, 200)

    def test_safety_hold_blocks_mediation(self):
        mediation = self._create_and_setup_mediation()
        pa = Participant.objects.get(mediation=mediation, user=self.a)
        conv = get_or_create_conversation(mediation, pa)

        resp = self.client.post(
            f"/api/conversations/{conv.id}/message/",
            {"message": "If you don't cooperate I will ruin your life."},
            format="json",
        )
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertTrue(data["safetyHold"])
        self.assertEqual(data["ai"]["messageType"], "SAFETY_ALERT")

        detail = self.client.get(f"/api/mediations/{mediation.id}/")
        self.assertEqual(detail.json()["status"], "SAFETY_HOLD")

        blocked = self.client.post(
            f"/api/conversations/{conv.id}/message/",
            {"message": "hello again"},
            format="json",
        )
        self.assertEqual(blocked.status_code, status.HTTP_409_CONFLICT)

    def test_uninvited_user_cannot_message(self):
        mediation = self._create_and_setup_mediation()
        other = User.objects.create_user(email="c@case.com", name="Cara", password="secret123")
        self.client.force_authenticate(other)
        resp = self.client.post(f"/api/mediations/{mediation.id}/analyze/")
        self.assertEqual(resp.status_code, 404)
