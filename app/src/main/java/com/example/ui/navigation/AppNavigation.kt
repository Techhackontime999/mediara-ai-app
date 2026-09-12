package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.repository.MediationRepository
import com.example.ui.auth.LoginScreen
import com.example.ui.auth.OnboardingScreen
import com.example.ui.auth.SignUpScreen
import com.example.ui.auth.SplashScreen
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.dashboard.ProfileScreen
import com.example.ui.mediation.*

@Composable
fun AppNavigation(
    repository: MediationRepository,
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        modifier = modifier
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onTimeout = {
                    val destination =
                        if (repository.isAuthenticated()) Screen.Dashboard.route else Screen.Onboarding.route
                    navController.navigate(destination) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onGetStarted = {
                    val destination =
                        if (repository.isAuthenticated()) Screen.Dashboard.route else Screen.SignUp.route
                    navController.navigate(destination) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
                onJoinWithCode = {
                    navController.navigate(Screen.JoinMediation.route)
                },
                onLoginClick = {
                    navController.navigate(Screen.Login.route)
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                repository = repository,
                onLoginSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToSignUp = {
                    navController.navigate(Screen.SignUp.route)
                }
            )
        }

        composable(Screen.SignUp.route) {
            SignUpScreen(
                repository = repository,
                onSignUpSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.SignUp.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route)
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                repository = repository,
                onMediationClick = { id ->
                    navController.navigate(Screen.MediationDetail.createRoute(id))
                },
                onCreateMediationClick = {
                    navController.navigate(Screen.CreateMediation.route)
                },
                onJoinMediationClick = {
                    navController.navigate(Screen.JoinMediation.route)
                },
                onProfileClick = {
                    navController.navigate(Screen.Profile.route)
                }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                repository = repository,
                onBackClick = { navController.popBackStack() },
                onLogoutClick = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }
            composable(Screen.CreateMediation.route) {
            CreateMediationScreen(
                repository = repository,
                onMediationCreated = { id ->
                    navController.navigate(Screen.MediationDetail.createRoute(id)) {
                        popUpTo(Screen.CreateMediation.route) { inclusive = true }
                    }
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.JoinMediation.route) {
            JoinMediationScreen(
                repository = repository,
                onMediationJoined = { id ->
                    navController.navigate(Screen.MediationDetail.createRoute(id)) {
                        popUpTo(Screen.JoinMediation.route) { inclusive = true }
                    }
                },
                onRequireLogin = {
                    navController.navigate(Screen.Login.route)
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.MediationDetail.route,
            arguments = listOf(navArgument("mediationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val mediationId = backStackEntry.arguments?.getString("mediationId") ?: return@composable
            MediationDetailScreen(
                mediationId = mediationId,
                repository = repository,
                onBackClick = { navController.popBackStack() },
                onStartPrivateSession = { participantId ->
                    navController.navigate(Screen.PrivateSessionChat.createRoute(mediationId, participantId))
                },
                onViewAnalysis = {
                    navController.navigate(Screen.ConflictAnalysisScreen.createRoute(mediationId))
                },
                onViewProposals = {
                    navController.navigate(Screen.ProposalsReview.createRoute(mediationId))
                },
                onViewAgreement = {
                    navController.navigate(Screen.AgreementResolved.createRoute(mediationId))
                },
                onFollowUpClick = { participantId ->
                    navController.navigate(Screen.FollowUpScreen.createRoute(mediationId, participantId))
                },
                onSafetyClick = {
                    navController.navigate(Screen.SafetyIntervention.createRoute(mediationId))
                }
            )
        }

        composable(
            route = Screen.PrivateSessionChat.route,
            arguments = listOf(
                navArgument("mediationId") { type = NavType.StringType },
                navArgument("participantId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val mediationId = backStackEntry.arguments?.getString("mediationId") ?: return@composable
            val participantId = backStackEntry.arguments?.getString("participantId") ?: return@composable

            PrivateSessionChatScreen(
                mediationId = mediationId,
                participantId = participantId,
                repository = repository,
                onBackClick = { navController.popBackStack() },
                onSessionCompleted = { navController.popBackStack() },
                onSafetyAlertTriggered = {
                    navController.navigate(Screen.SafetyIntervention.createRoute(mediationId))
                }
            )
        }

        composable(
            route = Screen.ConflictAnalysisScreen.route,
            arguments = listOf(navArgument("mediationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val mediationId = backStackEntry.arguments?.getString("mediationId") ?: return@composable
            ConflictAnalysisScreen(
                mediationId = mediationId,
                repository = repository,
                onBackClick = { navController.popBackStack() },
                onProceedToProposals = {
                    navController.navigate(Screen.ProposalsReview.createRoute(mediationId))
                }
            )
        }

        composable(
            route = Screen.ProposalsReview.route,
            arguments = listOf(navArgument("mediationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val mediationId = backStackEntry.arguments?.getString("mediationId") ?: return@composable
            ProposalsReviewScreen(
                mediationId = mediationId,
                repository = repository,
                onBackClick = { navController.popBackStack() },
                onAgreementFinalized = {
                    navController.navigate(Screen.AgreementResolved.createRoute(mediationId)) {
                        popUpTo(Screen.ProposalsReview.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.AgreementResolved.route,
            arguments = listOf(navArgument("mediationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val mediationId = backStackEntry.arguments?.getString("mediationId") ?: return@composable
            AgreementResolvedScreen(
                mediationId = mediationId,
                repository = repository,
                onBackClick = { navController.popBackStack() },
                onOpenPdfPreview = {
                    navController.navigate(Screen.AgreementPdfPreview.createRoute(mediationId))
                },
                onOpenFollowUp = {
                    val pId = repository.myParticipantIdNow(mediationId)
                        ?: repository.mediations.value.firstOrNull { it.id == mediationId }
                            ?.participants?.firstOrNull()?.id
                        ?: ""
                    if (pId.isNotBlank()) {
                        navController.navigate(Screen.FollowUpScreen.createRoute(mediationId, pId))
                    }
                }
            )
        }

        composable(
            route = Screen.AgreementPdfPreview.route,
            arguments = listOf(navArgument("mediationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val mediationId = backStackEntry.arguments?.getString("mediationId") ?: return@composable
            AgreementPdfPreviewScreen(
                mediationId = mediationId,
                repository = repository,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.FollowUpScreen.route,
            arguments = listOf(
                navArgument("mediationId") { type = NavType.StringType },
                navArgument("participantId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val mediationId = backStackEntry.arguments?.getString("mediationId") ?: return@composable
            val participantId = backStackEntry.arguments?.getString("participantId") ?: return@composable

            FollowUpScreen(
                mediationId = mediationId,
                participantId = participantId,
                repository = repository,
                onBackClick = { navController.popBackStack() },
                onMediationReopened = {
                    navController.navigate(Screen.MediationDetail.createRoute(mediationId)) {
                        popUpTo(Screen.MediationDetail.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.SafetyIntervention.route,
            arguments = listOf(navArgument("mediationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val mediationId = backStackEntry.arguments?.getString("mediationId") ?: return@composable
            SafetyInterventionScreen(
                mediationId = mediationId,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
