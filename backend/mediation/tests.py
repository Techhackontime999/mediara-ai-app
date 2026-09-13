from django.contrib.auth import get_user_model
from rest_framework import status
from rest_framework.test import APITestCase

from .models import Mediation, Participant

User = get_user_model()


class MediationFlowTests(APITestCase):
    def setUp(self):
        self.user_a = User.objects.create_user(email="a@example.com", name="Alice A", password="secret123")
        self.user_b = User.objects.create_user(email="b@example.com", name="Ben B", password="secret123")

    def _auth(self, user):
        self.client.force_authenticate(user)

    def test_create_mediation_and_invite_by_email(self):
        self._auth(self.user_a)
        resp = self.client.post(
            "/api/mediations/",
            {
                "title": "Shared apartment cleanup conflict",
                "description": "Disagreement on chores and quiet hours.",
                "category": "Roommates",
            },
            format="json",
        )
        self.assertEqual(resp.status_code, status.HTTP_201_CREATED)
        data = resp.json()
        self.assertEqual(data["title"], "Shared apartment cleanup conflict")
        self.assertIn("inviteCode", data)
        self.assertEqual(len(data["participants"]), 1)
        mediation_id = data["id"]

        invite = self.client.post(
            f"/api/mediations/{mediation_id}/invite/",
            {"name": "Ben B", "email": "b@example.com", "role": "Roommate B"},
            format="json",
        )
        self.assertEqual(invite.status_code, 200)
        self.assertEqual(invite.json()["email"], "b@example.com")

        detail = self.client.get(f"/api/mediations/{mediation_id}/")
        self.assertEqual(detail.status_code, 200)
        self.assertEqual(len(detail.json()["participants"]), 2)

    def test_join_by_code(self):
        mediation = Mediation.objects.create(
            title="Test dispute",
            description="desc",
            category="General",
            creator=self.user_a,
        )
        Participant.objects.create(mediation=mediation, user=self.user_a, role="Initiator")

        lookup = self.client.get(f"/api/mediations/by-code/{mediation.invite_code}/")
        self.assertEqual(lookup.status_code, 200)
        self.assertEqual(lookup.json()["title"], "Test dispute")

        self._auth(self.user_b)
        join = self.client.post(
            "/api/mediations/join/",
            {"inviteCode": mediation.invite_code, "role": "Party B"},
            format="json",
        )
        self.assertEqual(join.status_code, 200)
        names = {p["email"] for p in join.json()["participants"]}
        self.assertIn("b@example.com", names)

    def test_non_participant_cannot_view(self):
        mediation = Mediation.objects.create(title="T", description="d", category="C", creator=self.user_a)
        Participant.objects.create(mediation=mediation, user=self.user_a, role="A")
        self._auth(self.user_b)
        resp = self.client.get(f"/api/mediations/{mediation.id}/")
        self.assertEqual(resp.status_code, status.HTTP_403_FORBIDDEN)

    def test_mediation_list_shows_only_own(self):
        m1 = Mediation.objects.create(title="M1", description="d", category="C", creator=self.user_a)
        Participant.objects.create(mediation=m1, user=self.user_a, role="A")
        Participant.objects.create(mediation=m1, user=self.user_b, role="B")
        m2 = Mediation.objects.create(title="M2", description="d", category="C", creator=self.user_b)
        Participant.objects.create(mediation=m2, user=self.user_b, role="B")

        self._auth(self.user_a)
        resp = self.client.get("/api/mediations/")
        titles = {m["title"] for m in resp.json()}
        self.assertIn("M1", titles)
        self.assertNotIn("M2", titles)
