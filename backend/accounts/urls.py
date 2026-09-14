from django.urls import path

from .views import (
    DataExportView,
    DataPurgeView,
    DevResendCodeView,
    EmailVerifyView,
    LoginView,
    LogoutView,
    MeView,
    MfaEnableView,
    MfaSetupCompleteView,
    MfaSetupView,
    MfaStartEnrollmentView,
    MfaVerifyLoginView,
    PasswordResetConfirmView,
    PasswordResetRequestView,
    RegisterView,
    SecureRefreshView,
)

urlpatterns = [
    path("register/", RegisterView.as_view(), name="register"),
    path("login/", LoginView.as_view(), name="login"),
    path("logout/", LogoutView.as_view(), name="logout"),
    path("me/", MeView.as_view(), name="me"),
    path("refresh/", SecureRefreshView.as_view(), name="token_refresh"),
    path("verify-email/", EmailVerifyView.as_view(), name="verify_email"),
    path("password-reset/request/", PasswordResetRequestView.as_view(), name="password_reset_request"),
    path("password-reset/confirm/", PasswordResetConfirmView.as_view(), name="password_reset_confirm"),
    path("dev-resend-code/", DevResendCodeView.as_view(), name="dev_resend_code"),
    path("mfa/start/", MfaStartEnrollmentView.as_view(), name="mfa_start"),
    path("mfa/enable/", MfaEnableView.as_view(), name="mfa_enable"),
    path("mfa/verify/", MfaVerifyLoginView.as_view(), name="mfa_verify"),
    path("mfa/setup/", MfaSetupView.as_view(), name="mfa_setup"),
    path("mfa/setup-complete/", MfaSetupCompleteView.as_view(), name="mfa_setup_complete"),
    path("data-export/", DataExportView.as_view(), name="data_export"),
    path("data-purge/", DataPurgeView.as_view(), name="data_purge"),
]
