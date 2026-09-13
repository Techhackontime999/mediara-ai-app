from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.test import TestCase, override_settings
from security.crypto import decrypt_text, encrypt_text
from security.fields import EncryptedTextField

User = get_user_model()


class LlmProviderRoutingTests(TestCase):
    """Proof that any OpenAI-compatible base URL works, not just Gemini."""

    def test_openai_compatible_base_url_routing(self):
        with override_settings(
            AI_PROVIDER="openai",
            AI_BASE_URL="http://localhost:11434/v1",  # e.g. Ollama / LM Studio
            AI_API_KEY="",
            AI_MODEL="llama3.1",
            GEMINI_API_KEY="",
        ), patch("ai.llm.requests.post") as post:
            post.return_value.status_code = 200
            post.return_value.json.return_value = {"choices": [{"message": {"content": '{"ok": true}'}}]}

            from ai.llm import call_llm, is_llm_enabled, llm_config

            self.assertTrue(is_llm_enabled())
            self.assertEqual(llm_config()["provider"], "openai")

            text = call_llm("hello")
            self.assertEqual(text, '{"ok": true}')

            url = post.call_args.args[0]
            body = post.call_args.kwargs["json"]
            headers = post.call_args.kwargs["headers"]
            self.assertEqual(url, "http://localhost:11434/v1/chat/completions")
            self.assertEqual(body["model"], "llama3.1")
            self.assertNotIn("Authorization", headers)  # no key configured

    def test_openai_compatible_uses_bearer_key(self):
        with override_settings(
            AI_PROVIDER="auto",
            AI_BASE_URL="https://api.groq.com/openai/v1",
            AI_API_KEY="groq-secret",
            AI_MODEL="llama-3.1-8b-instant",
            GEMINI_API_KEY="",
        ), patch("ai.llm.requests.post") as post:
            post.return_value.status_code = 200
            post.return_value.json.return_value = {"choices": [{"message": {"content": "hi"}}]}

            from ai.llm import call_llm

            call_llm("ping")
            headers = post.call_args.kwargs["headers"]
            self.assertEqual(headers["Authorization"], "Bearer groq-secret")

    def test_gemini_native_routing_preserved(self):
        with override_settings(
            AI_PROVIDER="auto",
            AI_BASE_URL="",
            AI_API_KEY="",
            AI_MODEL="",
            GEMINI_API_KEY="legacy-key",
            GEMINI_MODEL="gemini-2.5-flash",
        ), patch("ai.llm.requests.post") as post:
            post.return_value.status_code = 200
            post.return_value.json.return_value = {
                "candidates": [{"content": {"parts": [{"text": '["a"]'}]}}]
            }

            from ai.llm import call_llm, llm_config

            self.assertEqual(llm_config()["provider"], "gemini")
            self.assertEqual(call_llm("ping"), '["a"]')
            self.assertIn(":generateContent", post.call_args.args[0])
            self.assertEqual(post.call_args.kwargs["params"]["key"], "legacy-key")

    def test_unconfigured_disables_llm(self):
        with override_settings(AI_PROVIDER="auto", AI_BASE_URL="", AI_API_KEY="", GEMINI_API_KEY="", AI_MODEL=""):
            from ai.llm import is_llm_enabled, llm_config
            self.assertFalse(is_llm_enabled())
            self.assertEqual(llm_config()["provider"], "none")


class EncryptionAtRestTests(TestCase):
    def test_roundtrip_fernet(self):
        token = encrypt_text("interpersonal-confidential")
        self.assertNotIn("confidential", token)
        self.assertEqual(decrypt_text(token), "interpersonal-confidential")

    def test_field_encrypts_on_save(self):
        field = EncryptedTextField()
        value = "a very private message"
        prepared = field.get_db_prep_value(value, connection=None, prepared=False)
        self.assertNotEqual(prepared, value)
        restored = field.to_python(prepared)
        self.assertEqual(restored, value)


class ContentSafetyTests(TestCase):
    def test_totp_and_password_strength(self):
        from accounts.security import generate_totp_secret, validate_password_strength, verify_totp
        from django.core.exceptions import ValidationError

        secret = generate_totp_secret()
        self.assertFalse(verify_totp(secret, "123456"))

        with self.assertRaises(ValidationError):
            validate_password_strength("short")
        with self.assertRaises(ValidationError):
            validate_password_strength("alllower12")
        validate_password_strength("Secret123")  # must not raise


class AuditTrailTests(TestCase):
    def test_audit_record_and_listing(self):
        from audit.models import AuditLog
        from rest_framework.test import APIClient

        user = User.objects.create_user(email="audit@example.com", name="Auditor", password="Secret123")
        AuditLog.record(actor=user, action="test.event", entity_type="user", entity_id=1)
        client = APIClient()
        client.force_authenticate(user=user)
        resp = client.get("/api/audit/")
        self.assertEqual(resp.status_code, 200)
        results = resp.json()["results"]
        self.assertEqual(results[0]["action"], "test.event")


class NotificationTests(TestCase):
    def test_create_notification_and_mark_read(self):
        from notifications.models import Notification
        from notifications.services import create_notification
        from rest_framework.test import APIClient

        user = User.objects.create_user(email="n@example.com", name="Nina", password="Secret123")
        notification = create_notification(user, notification_type="generic", title="Hello", body="Hi", send_email=False)
        self.assertIsNotNone(notification)
        self.assertEqual(Notification.objects.filter(user=user).count(), 1)

        client = APIClient()
        client.force_authenticate(user=user)
        resp = client.get("/api/notifications/")
        self.assertEqual(resp.status_code, 200)
        self.assertEqual(resp.json()[0]["title"], "Hello")
        resp = client.post(f"/api/notifications/{notification.id}/read/")
        self.assertEqual(resp.status_code, 200)
        self.assertTrue(Notification.objects.get(pk=notification.id).is_read)


class GroundingTests(TestCase):
    def test_ground_proposals_cites_statements(self):
        from ai.grounding import ground_proposals
        proposals = [{
            "title": "Test",
            "description": "A proposal",
            "benefits": ["Improves communication during team check-ins"],
            "why_it_works": "Responds to the wish for clear communication",
        }]
        perspectives = [
            {"participant_name": "Alex", "goals": ["Clear communication during team check-ins"], "concerns": ["Being ignored"], "needs": ["Respect"], "desired_outcome": "", "acceptable_compromises": []},
        ]
        grounded = ground_proposals(proposals, perspectives)
        self.assertTrue(grounded[0]["grounded_in"])
        self.assertEqual(grounded[0]["grounded_in"][0]["participant"], "Alex")

    def test_balance_flags_dominance(self):
        from conversations.models import Conversation, Message
        from mediation.models import Mediation

        from ai.balance import assess_balance

        creator = User.objects.create_user(email="creator@example.com", name="Creator", password="Secret123")
        mediation = Mediation.objects.create(title="Balance", category="General", creator=creator)
        from mediation.models import Participant
        loud = User.objects.create_user(email="loud@example.com", name="Loud", password="Secret123")
        quiet = User.objects.create_user(email="quiet@example.com", name="Quiet", password="Secret123")
        pl = Participant.objects.create(mediation=mediation, user=loud, role="Initiator")
        pq = Participant.objects.create(mediation=mediation, user=quiet, role="Participant")

        conv_l, _ = Conversation.objects.get_or_create(mediation=mediation, participant=pl)
        conv_q, _ = Conversation.objects.get_or_create(mediation=mediation, participant=pq)
        for _i in range(10):
            Message.objects.create(conversation=conv_l, sender="USER", content="s")
        Message.objects.create(conversation=conv_q, sender="USER", content="s")

        report = assess_balance(mediation)
        self.assertFalse(report["balanced"])
        self.assertEqual(report["dominantParticipant"], "Loud")
