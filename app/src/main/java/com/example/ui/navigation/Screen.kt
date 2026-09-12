package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Onboarding : Screen("onboarding")
    object Login : Screen("login")
    object SignUp : Screen("signup")
    object Dashboard : Screen("dashboard")
    object Profile : Screen("profile")
    object CreateMediation : Screen("create_mediation")
    object JoinMediation : Screen("join_mediation")

    object MediationDetail : Screen("mediation_detail/{mediationId}") {
        fun createRoute(mediationId: String) = "mediation_detail/$mediationId"
    }

    object InviteParticipants : Screen("invite_participants/{mediationId}") {
        fun createRoute(mediationId: String) = "invite_participants/$mediationId"
    }

    object PrivateSessionChat : Screen("private_session/{mediationId}/{participantId}") {
        fun createRoute(mediationId: String, participantId: String) = "private_session/$mediationId/$participantId"
    }

    object ConflictAnalysisScreen : Screen("conflict_analysis/{mediationId}") {
        fun createRoute(mediationId: String) = "conflict_analysis/$mediationId"
    }

    object ProposalsReview : Screen("proposals_review/{mediationId}") {
        fun createRoute(mediationId: String) = "proposals_review/$mediationId"
    }

    object AgreementResolved : Screen("agreement_resolved/{mediationId}") {
        fun createRoute(mediationId: String) = "agreement_resolved/$mediationId"
    }

    object AgreementPdfPreview : Screen("agreement_pdf/{mediationId}") {
        fun createRoute(mediationId: String) = "agreement_pdf/$mediationId"
    }

    object FollowUpScreen : Screen("follow_up/{mediationId}/{participantId}") {
        fun createRoute(mediationId: String, participantId: String) = "follow_up/$mediationId/$participantId"
    }

    object SafetyIntervention : Screen("safety_intervention/{mediationId}") {
        fun createRoute(mediationId: String) = "safety_intervention/$mediationId"
    }
}
