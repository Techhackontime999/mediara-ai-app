from contextlib import suppress
from datetime import UTC, datetime

from audit.models import AuditLog
from django.contrib.auth import authenticate
from django.core.mail import send_mail
from rest_framework import status
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response
from rest_framework.throttling import AnonRateThrottle, ScopedRateThrottle
from rest_framework.views import APIView
from rest_framework_simplejwt.exceptions import InvalidToken, TokenError
from rest_framework_simplejwt.tokens import RefreshToken
from rest_framework_simplejwt.views import TokenRefreshView

from .models import User
from .security import generate_totp_secret, generate_verification_code, totp_uri, verify_email_code, verify_totp
from .serializers import (
    AuthResponseSerializer,
    EmailVerifyRequestSerializer,
    LoginRequestSerializer,
    MfaEnableRequestSerializer,
    MfaVerifyRequestSerializer,
    RegisterRequestSerializer,
    UserSerializer,
)


class AuthRateThrottle(AnonRateThrottle):
    scope = "auth"


class _LoginScopedThrottle(ScopedRateThrottle):
    scope = "login"


def _tokens_for(user: User, mfa_verified: bool = False) -> dict:
    refresh = RefreshToken.for_user(user)
    if user.is_mfa_enabled:
        refresh["mfa"] = bool(mfa_verified)
        refresh.access_token["mfa"] = bool(mfa_verified)
    return {
        "access": str(refresh.access_token),
        "refresh": str(refresh),
    }


class RegisterView(APIView):
    permission_classes = [AllowAny]
    throttle_classes = [AuthRateThrottle]

    def post(self, request):
        serializer = RegisterRequestSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = serializer.save()
        # Verification code is dispatched via the configured email backend. When
        # EMAIL_BACKEND is the console backend (default in dev) the code is
        # printed to the server console and also surfaced by /dev-resend-code/.
        code = generate_verification_code(user)
        send_mail(
            "Mediara AI — verify your email",
            f"Your verification code is: {code}\nIt expires in 24 hours.",
            None,
            [user.email],
            fail_silently=True,
        )
        AuditLog.record(
            actor=user,
            action="auth.register",
            entity_type="user",
            entity_id=user.pk,
            request=request,
        )
        tokens = _tokens_for(user)
        return Response(
            AuthResponseSerializer({"user": user, **tokens}).data,
            status=status.HTTP_201_CREATED,
        )


class DevResendCodeView(APIView):
    """Dev-only: rotate & return the email verification code.

    In production the code only travels via email. Kept behind DEBUG to let
    local two-account testing proceed without an SMTP server.
    """

    permission_classes = [AllowAny]

    def post(self, request):
        from django.conf import settings
        if not settings.DEBUG:
            return Response({"detail": "Not available in production."}, status=status.HTTP_403_FORBIDDEN)
        email = (request.data.get("email") or "").lower().strip()
        user = User.objects.filter(email=email).first()
        if user is None:
            return Response({"detail": "No account with that email."}, status=status.HTTP_404_NOT_FOUND)
        code = generate_verification_code(user)
        return Response({"detail": "Dev verification code.", "code": code})


class EmailVerifyView(APIView):
    permission_classes = [AllowAny]
    throttle_classes = [AuthRateThrottle]

    def post(self, request):
        serializer = EmailVerifyRequestSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        email = serializer.validated_data["email"].lower().strip()
        user = User.objects.filter(email=email).first()
        if user is None or not verify_email_code(user, serializer.validated_data["code"]):
            return Response({"detail": "Invalid or expired verification code."}, status=status.HTTP_400_BAD_REQUEST)
        AuditLog.record(actor=user, action="auth.email_verified", entity_type="user", entity_id=user.pk, request=request)
        return Response({"detail": "Email verified."})


class LoginView(APIView):
    permission_classes = [AllowAny]
    throttle_classes = [AuthRateThrottle, _LoginScopedThrottle]

    def post(self, request):
        serializer = LoginRequestSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        email = serializer.validated_data["email"].lower().strip()
        password = serializer.validated_data["password"]
        user = authenticate(request, username=email, password=password)
        if user is None:
            AuditLog.record(action="auth.login_failed", entity_type="user", entity_id=email, request=request)
            return Response(
                {"detail": "Invalid email or password."},
                status=status.HTTP_401_UNAUTHORIZED,
            )
        if user.data_erased or (not user.is_active):
            return Response({"detail": "This account is no longer active."}, status=status.HTTP_403_FORBIDDEN)

        AuditLog.record(actor=user, action="auth.login", entity_type="user", entity_id=user.pk, request=request)

        # MFA gate: one-factor login returns a challenge the client must pass.
        if user.is_mfa_enabled:
            return Response(
                {"detail": "MFA code required.", "mfaRequired": True, "userId": user.pk},
                status=status.HTTP_200_OK,
            )
        tokens = _tokens_for(user, mfa_verified=True)
        return Response(AuthResponseSerializer({"user": user, **tokens}).data)


class MfaEnableView(APIView):
    """POST /api/auth/mfa/enable/ — enroll TOTP; the client stores the otpauth URI."""

    permission_classes = [IsAuthenticated]

    def post(self, request):
        user = request.user
        serializer = MfaEnableRequestSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        secret = user.mfa_secret or generate_totp_secret()
        if not user.mfa_secret:
            user.mfa_secret = secret
            user.save(update_fields=["mfa_secret"])
        if not verify_totp(secret, serializer.validated_data["code"]):
            return Response({"detail": "Invalid TOTP code. Scannable secrets are paired with your authenticator app."}, status=status.HTTP_400_BAD_REQUEST)
        user.is_mfa_enabled = True
        user.save(update_fields=["is_mfa_enabled"])
        AuditLog.record(actor=user, action="auth.mfa_enabled", entity_type="user", entity_id=user.pk, request=request)
        return Response({"detail": "MFA enabled."})


class MfaStartEnrollmentView(APIView):
    """POST /api/auth/mfa/start/ — get a fresh TOTP secret + otpauth:// URI."""

    permission_classes = [IsAuthenticated]

    def post(self, request):
        user = request.user
        secret = generate_totp_secret()
        user.mfa_secret = secret
        user.is_mfa_enabled = False
        user.save(update_fields=["mfa_secret", "is_mfa_enabled"])
        return Response({
            "secret": secret,
            "otpauthUri": totp_uri(secret, user.email),
        })


class MfaVerifyLoginView(APIView):
    """POST /api/auth/mfa/verify/ — completes an MFA-gated login with a TOTP code."""

    permission_classes = [AllowAny]
    throttle_classes = [AuthRateThrottle]

    def post(self, request):
        serializer = MfaVerifyRequestSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = User.objects.filter(pk=request.data.get("userId")).first()
        if user is None or not user.is_mfa_enabled or not user.mfa_secret:
            return Response({"detail": "MFA not enabled for this account."}, status=status.HTTP_400_BAD_REQUEST)
        if not verify_totp(user.mfa_secret, serializer.validated_data["code"]):
            return Response({"detail": "Invalid authentication code."}, status=status.HTTP_401_UNAUTHORIZED)
        AuditLog.record(actor=user, action="auth.login_mfa_verified", entity_type="user", entity_id=user.pk, request=request)
        tokens = _tokens_for(user, mfa_verified=True)
        return Response(AuthResponseSerializer({"user": user, **tokens}).data)


class SecureRefreshView(TokenRefreshView):
    """Refresh endpoint with reuse detection (reject blacklisted / reused tokens)."""

    def post(self, request, *args, **kwargs):
        try:
            refresh_token = request.data.get("refresh")
            RefreshToken(refresh_token)  # validates signature & reports reuse of blacklisted tokens
        except (TokenError, InvalidToken) as exc:
            AuditLog.record(actor=request.user if request.user.is_authenticated else None,
                            action="auth.token_reuse_or_invalid", entity_type="user",
                            entity_id=getattr(request.user, "pk", ""), request=request)
            return Response({"detail": "Token is invalid or expelled (possible reuse).", "code": str(getattr(exc, "default_code", "token_error"))},
                            status=status.HTTP_401_UNAUTHORIZED)
        return super().post(request, *args, **kwargs)


class LogoutView(APIView):
    """Blacklists the supplied refresh token so it cannot be used again."""

    def post(self, request):
        refresh = request.data.get("refresh")
        if not refresh:
            return Response({"detail": "Refresh token required."}, status=status.HTTP_400_BAD_REQUEST)
        with suppress(Exception):
            RefreshToken(refresh).blacklist()
        AuditLog.record(actor=request.user if request.user.is_authenticated else None,
                        action="auth.logout", entity_type="user",
                        entity_id=getattr(request.user, "pk", ""), request=request)
        return Response({"detail": "Logged out."})


class MeView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request):
        return Response(UserSerializer(request.user).data)


class DataExportView(APIView):
    """GET /api/auth/data-export/ — everything Mediara holds about this account."""

    permission_classes = [IsAuthenticated]

    def get(self, request):
        from agreements.models import FollowUpReport
        from conversations.models import Message
        from resolutions.models import Vote

        user = request.user
        mediations = []
        for p in user.participations.select_related("mediation").prefetch_related("mediation__participants"):
            m = p.mediation
            mediations.append({
                "role": p.role,
                "status": p.status,
                "mediationId": m.pk,
                "title": m.title,
                "category": m.category,
                "mediationStatus": m.status,
                "inviteCode": m.invite_code,
                "createdAt": m.created_at.isoformat(),
            })
        messages = [
            {"conversationId": msg.conversation_id, "sender": msg.sender, "content": msg.content,
             "riskFlagged": msg.risk_flagged, "createdAt": msg.created_at.isoformat()}
            for msg in Message.objects.filter(conversation__participant__user=user).select_related("conversation").order_by("created_at")
        ]
        votes = list(Vote.objects.filter(participant__user=user).values_list("resolution__mediation_id", "resolution_id", "decision", "feedback", "created_at"))
        follow_ups = list(FollowUpReport.objects.filter(participant__user=user).values_list("mediation_id", "sentiment", "comments", "created_at"))
        audit = list(AuditLog.objects.filter(actor=user).values_list("action", "created_at"))
        payload = {
            "account": UserSerializer(user).data,
            "mediations": mediations,
            "messages": messages,
            "votes": [tuple_to_dict(v, ("mediation_id", "resolution_id", "decision", "feedback", "created_at")) for v in votes],
            "followUps": [tuple_to_dict(f, ("mediation_id", "sentiment", "comments", "created_at")) for f in follow_ups],
            "audit": [{"action": a, "at": t.isoformat()} for a, t in audit],
            "exportedAt": datetime.now(UTC).isoformat(),
        }
        AuditLog.record(actor=user, action="privacy.data_export", entity_type="user", entity_id=user.pk, request=request)
        return Response(payload)


def tuple_to_dict(values, keys):
    return dict(zip(keys, values, strict=True))


class DataPurgeView(APIView):
    """POST /api/auth/data-purge/ — GDPR right-to-erasure.

    Anonymizes the account and deletes personal content the user authored.
    Mediation records are preserved but de-identified so co-parties keep a
    working record.
    """

    permission_classes = [IsAuthenticated]

    def post(self, request):
        from agreements.models import FollowUpReport
        from conversations.models import Message
        from mediation.models import Mediation, MediationStatus, Participant
        from resolutions.models import Vote

        user = request.user
        AuditLog.record(actor=user, action="privacy.data_purge", entity_type="user", entity_id=user.pk, request=request)

        Message.objects.filter(conversation__participant__user=user).delete()
        Vote.objects.filter(participant__user=user).delete()
        FollowUpReport.objects.filter(participant__user=user).delete()
        Participant.objects.filter(user=user).update(
            user=None, name="Deleted user", email=None, consent_given=False,
            has_completed_private_session=False,
        )
        # Remove the user's own mediations from active circulation (never destroy
        # co-participants' records) — de-identify instead of cascade-deleting.
        for m in Mediation.objects.filter(creator=user):
            m.status = MediationStatus.CLOSED
            m.save(update_fields=["status"])

        user.name = "Deleted user"
        user.email = f"deleted-{user.uuid}@erased.mediara.ai"
        user.is_active = False
        user.data_erased = True
        user.deleted_at = datetime.now(UTC)
        user.is_mfa_enabled = False
        user.mfa_secret = ""
        user.save(update_fields=["name", "email", "is_active", "data_erased", "deleted_at", "is_mfa_enabled", "mfa_secret"])
        return Response({"detail": "Your account and personal data were erased."})
