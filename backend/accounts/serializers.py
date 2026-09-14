from rest_framework import serializers

from .models import User
from .security import validate_password_strength


class UserSerializer(serializers.ModelSerializer):
    avatarInitials = serializers.CharField(source="avatar_initials", read_only=True)
    emailVerified = serializers.BooleanField(source="email_verified", read_only=True)
    mfaEnabled = serializers.BooleanField(source="is_mfa_enabled", read_only=True)
    isStaff = serializers.BooleanField(source="is_staff", read_only=True)

    class Meta:
        model = User
        fields = ("id", "uuid", "name", "email", "avatarInitials", "date_joined", "emailVerified", "mfaEnabled", "isStaff")
        read_only_fields = ("uuid", "date_joined", "email_verified", "is_mfa_enabled", "is_staff")


class RegisterRequestSerializer(serializers.ModelSerializer):
    password = serializers.CharField(write_only=True, min_length=8)
    name = serializers.CharField(required=True, allow_blank=False, max_length=150)

    class Meta:
        model = User
        fields = ("email", "name", "password")

    def validate_password(self, value):
        validate_password_strength(value)
        return value

    def create(self, validated_data):
        return User.objects.create_user(
            email=validated_data["email"],
            name=validated_data["name"],
            password=validated_data["password"],
        )


class LoginRequestSerializer(serializers.Serializer):
    email = serializers.EmailField()
    password = serializers.CharField(trim_whitespace=False)


class EmailVerifyRequestSerializer(serializers.Serializer):
    email = serializers.EmailField()
    code = serializers.CharField(min_length=6, max_length=6)


class PasswordResetRequestSerializer(serializers.Serializer):
    email = serializers.EmailField()


class PasswordResetConfirmSerializer(serializers.Serializer):
    email = serializers.EmailField()
    code = serializers.CharField(min_length=6, max_length=6)
    password = serializers.CharField(write_only=True, min_length=8)

    def validate_password(self, value):
        validate_password_strength(value)
        return value


class MfaEnableRequestSerializer(serializers.Serializer):
    code = serializers.CharField(min_length=6, max_length=6)


class MfaVerifyRequestSerializer(serializers.Serializer):
    code = serializers.CharField(min_length=6, max_length=6)


class AuthResponseSerializer(serializers.Serializer):
    access = serializers.CharField()
    refresh = serializers.CharField()
    user = UserSerializer()


class ExportDataSerializer(serializers.Serializer):
    """Read-only serialization of everything Mediara knows about an account."""

    account = UserSerializer()
    mediations = serializers.JSONField()
    messages = serializers.JSONField()
    votes = serializers.JSONField()
    followUps = serializers.JSONField()
    audit = serializers.JSONField()
    exportedAt = serializers.CharField()
