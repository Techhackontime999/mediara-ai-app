from django.contrib.auth import get_user_model
from rest_framework import status
from rest_framework.test import APITestCase


class AuthFlowTests(APITestCase):
    def setUp(self):
        self.register_url = "/api/auth/register/"
        self.login_url = "/api/auth/login/"

    def register_and_verify(self, **kwargs):
        resp = self.client.post(self.register_url, kwargs, format="json")
        self.assertEqual(resp.status_code, status.HTTP_201_CREATED)
        # Mark the address verified directly (the production code path sends a
        # code by email / verifies via /api/auth/verify-email/).
        user = get_user_model().objects.get(email=kwargs["email"])
        user.email_verified = True
        user.save(update_fields=["email_verified"])

    def test_register_returns_jwt_and_user(self):
        resp = self.client.post(
            self.register_url,
            {"email": "alex@example.com", "name": "Alex Morgan", "password": "Secret123"},
            format="json",
        )
        self.assertEqual(resp.status_code, status.HTTP_201_CREATED)
        data = resp.json()
        self.assertIn("access", data)
        self.assertIn("refresh", data)
        self.assertEqual(data["user"]["email"], "alex@example.com")
        self.assertEqual(data["user"]["name"], "Alex Morgan")
        self.assertTrue(get_user_model().objects.filter(email="alex@example.com").exists())

    def test_register_rejects_duplicate_email(self):
        self.client.post(self.register_url, {"email": "a@b.com", "name": "A", "password": "Secret123"}, format="json")
        resp = self.client.post(self.register_url, {"email": "a@b.com", "name": "B", "password": "secret456"}, format="json")
        self.assertEqual(resp.status_code, status.HTTP_400_BAD_REQUEST)

    def test_login_success_and_failure(self):
        self.register_and_verify(email="a@b.com", name="A B", password="Secret123")
        ok = self.client.post(self.login_url, {"email": "a@b.com", "password": "Secret123"}, format="json")
        self.assertEqual(ok.status_code, 200)
        self.assertIn("access", ok.json())
        bad = self.client.post(self.login_url, {"email": "a@b.com", "password": "wrong"}, format="json")
        self.assertEqual(bad.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_me_requires_auth(self):
        resp = self.client.get("/api/auth/me/")
        self.assertEqual(resp.status_code, status.HTTP_401_UNAUTHORIZED)

        self.register_and_verify(email="a@b.com", name="A B", password="Secret123")
        login = self.client.post(self.login_url, {"email": "a@b.com", "password": "Secret123"}, format="json")
        token = login.json()["access"]
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token}")
        me = self.client.get("/api/auth/me/")
        self.assertEqual(me.status_code, 200)
        self.assertEqual(me.json()["email"], "a@b.com")
