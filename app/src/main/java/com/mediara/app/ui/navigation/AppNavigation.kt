package com.mediara.app.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mediara.app.R
import com.mediara.app.data.preferences.UserPreferences
import com.mediara.app.data.remote.ServerConfigManager
import com.mediara.app.data.repository.MediationRepository
import com.mediara.app.ui.auth.*
import com.mediara.app.ui.dashboard.DashboardScreen
import com.mediara.app.ui.dashboard.ProfileScreen
import com.mediara.app.ui.mediation.*
import com.mediara.app.ui.settings.AdminConsoleScreen
import com.mediara.app.ui.settings.SettingsScreen

// ---------------------------------------------------------------------------
// Bottom navigation items
// ---------------------------------------------------------------------------

private data class BottomNavItem(val route: String, val labelRes: Int, val icon: ImageVector)

private val bottomNavItems = listOf(
    BottomNavItem(Screen.Dashboard.route, R.string.nav_home, Icons.Default.Home),
    BottomNavItem(Screen.Profile.route, R.string.nav_profile, Icons.Default.Person),
    BottomNavItem(Screen.Settings.route, R.string.nav_settings, Icons.Default.Settings),
)

// Animated bottom bar: slides up when visible and crowds nothing when hidden.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnimatedBottomBar(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val pressScale by animateFloatAsState(
                if (isPressed) 0.86f else 1f,
                animationSpec = tween(120)
            )
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                interactionSource = interactionSource,
                icon = {
                    Icon(
                        item.icon,
                        contentDescription = stringResource(item.labelRes),
                        modifier = Modifier.size(24.dp).scale(pressScale)
                    )
                },
                label = {
                    Text(
                        stringResource(item.labelRes),
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                )
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Navigation host
// ---------------------------------------------------------------------------

@Composable
fun AppNavigation(
    repository: MediationRepository,
    serverConfig: ServerConfigManager,
    preferences: UserPreferences,
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    // Only show the bottom bar on the three main authenticated screens.
    val showBottomBar = currentRoute in Screen.BottomBarRoutes

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(
                    animationSpec = tween(280),
                    initialOffsetY = { it }
                ) + fadeIn(animationSpec = tween(220)),
                exit = slideOutVertically(
                    animationSpec = tween(240),
                    targetOffsetY = { it }
                ) + fadeOut(animationSpec = tween(180))
            ) {
                AnimatedBottomBar(
                    items = bottomNavItems,
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier.padding(innerPadding)
        ) {

            composableWithMotion(Screen.Splash.route) {
                SplashScreen(
                    onTimeout = {
                        val destination = if (repository.isAuthenticated()) Screen.Dashboard.route else Screen.Onboarding.route
                        navController.navigate(destination) { popUpTo(Screen.Splash.route) { inclusive = true } }
                    }
                )
            }

            composableWithMotion(Screen.Onboarding.route) {
                OnboardingScreen(
                    onGetStarted = {
                        val destination = if (repository.isAuthenticated()) Screen.Dashboard.route else Screen.SignUp.route
                        navController.navigate(destination) { popUpTo(Screen.Onboarding.route) { inclusive = true } }
                    },
                    onJoinWithCode = { navController.navigate(Screen.JoinMediation.route) },
                    onLoginClick = { navController.navigate(Screen.Login.route) },
                    onAdminClick = { navController.navigate(Screen.AdminConsole.route) }
                )
            }

            composableWithMotion(Screen.Login.route) {
                LoginScreen(
                    repository = repository,
                    onLoginSuccess = {
                        navController.navigate(Screen.Dashboard.route) { popUpTo(Screen.Login.route) { inclusive = true } }
                    },
                    onNavigateToSignUp = { navController.navigate(Screen.SignUp.route) },
                    onForgotPassword = { navController.navigate(Screen.ForgotPassword.route) },
                    onEmailVerificationRequired = { email ->
                        navController.navigate(Screen.EmailVerification.createRoute(email)) { popUpTo(Screen.Login.route) { inclusive = false } }
                    }
                )
            }

            composableWithMotion(Screen.SignUp.route) {
                SignUpScreen(
                    repository = repository,
                    onSignUpSuccess = {
                        navController.navigate(Screen.Dashboard.route) { popUpTo(Screen.SignUp.route) { inclusive = true } }
                    },
                    onNavigateToLogin = { navController.navigate(Screen.Login.route) },
                    onEmailVerificationRequired = { email ->
                        navController.navigate(Screen.EmailVerification.createRoute(email)) { popUpTo(Screen.SignUp.route) { inclusive = false } }
                    }
                )
            }

            composableWithMotion(Screen.ForgotPassword.route) {
                ForgotPasswordScreen(
                    repository = repository,
                    onBack = { navController.popBackStack() },
                    onCodeSent = { email ->
                        navController.navigate(Screen.ResetPassword.createRoute(email)) { popUpTo(Screen.ForgotPassword.route) { inclusive = true } }
                    }
                )
            }

            composableWithMotion(
                route = Screen.ResetPassword.route,
                arguments = listOf(navArgument("email") { type = NavType.StringType })
            ) { backStackEntry ->
                val email = Uri.decode(backStackEntry.arguments?.getString("email") ?: "")
                ResetPasswordScreen(
                    repository = repository,
                    email = email,
                    onBack = { navController.popBackStack() },
                    onResetSuccess = {
                        navController.navigate(Screen.Login.route) { popUpTo(Screen.ResetPassword.route) { inclusive = true } }
                    }
                )
            }

            composableWithMotion(
                route = Screen.EmailVerification.route,
                arguments = listOf(navArgument("email") { type = NavType.StringType })
            ) { backStackEntry ->
                val email = Uri.decode(backStackEntry.arguments?.getString("email") ?: "")
                EmailVerificationScreen(
                    repository = repository,
                    email = email,
                    onBack = { navController.popBackStack() },
                    onVerified = {
                        navController.navigate(Screen.Login.route) { popUpTo(Screen.EmailVerification.route) { inclusive = true } }
                    },
                    onResendSuccess = { devCode ->
                        android.widget.Toast.makeText(context, "Verification code: $devCode", android.widget.Toast.LENGTH_LONG).show()
                    }
                )
            }

            composableWithMotion(Screen.AdminConsole.route) {
                AdminConsoleScreen(
                    repository = repository,
                    serverConfig = serverConfig,
                    onBackClick = { navController.popBackStack() },
                    onApplied = {
                        navController.navigate(Screen.Onboarding.route) { popUpTo(Screen.AdminConsole.route) { inclusive = true } }
                    }
                )
            }

            composableWithMotion(Screen.Dashboard.route) {
                DashboardScreen(
                    repository = repository,
                    onMediationClick = { id -> navController.navigate(Screen.MediationDetail.createRoute(id)) },
                    onCreateMediationClick = { navController.navigate(Screen.CreateMediation.route) },
                    onJoinMediationClick = { navController.navigate(Screen.JoinMediation.route) },
                    onProfileClick = {
                        navController.navigate(Screen.Profile.route) {
                            popUpTo(Screen.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composableWithMotion(Screen.Profile.route) {
                ProfileScreen(
                    repository = repository,
                    onBackClick = { navController.popBackStack() },
                    onLogoutClick = {
                        navController.navigate(Screen.Onboarding.route) { popUpTo(0) { inclusive = true } }
                    }
                )
            }

            composableWithMotion(Screen.Settings.route) {
                SettingsScreen(
                    preferences = preferences,
                    repository = repository,
                    onBack = { navController.popBackStack() },
                    onLogout = {
                        navController.navigate(Screen.Onboarding.route) { popUpTo(0) { inclusive = true } }
                    }
                )
            }

            composableWithMotion(Screen.CreateMediation.route) {
                CreateMediationScreen(
                    repository = repository,
                    onMediationCreated = { id -> navController.navigate(Screen.MediationDetail.createRoute(id)) { popUpTo(Screen.CreateMediation.route) { inclusive = true } } },
                    onBackClick = { navController.popBackStack() }
                )
            }

            composableWithMotion(Screen.JoinMediation.route) {
                JoinMediationScreen(
                    repository = repository,
                    onMediationJoined = { id -> navController.navigate(Screen.MediationDetail.createRoute(id)) { popUpTo(Screen.JoinMediation.route) { inclusive = true } } },
                    onRequireLogin = { navController.navigate(Screen.Login.route) },
                    onBackClick = { navController.popBackStack() }
                )
            }

            composableWithMotion(
                route = Screen.MediationDetail.route,
                arguments = listOf(navArgument("mediationId") { type = NavType.StringType })
            ) { backStackEntry ->
                val mediationId = backStackEntry.arguments?.getString("mediationId") ?: return@composableWithMotion
                MediationDetailScreen(
                    mediationId = mediationId,
                    repository = repository,
                    onBackClick = { navController.popBackStack() },
                    onStartPrivateSession = { participantId -> navController.navigate(Screen.PrivateSessionChat.createRoute(mediationId, participantId)) },
                    onViewAnalysis = { navController.navigate(Screen.ConflictAnalysisScreen.createRoute(mediationId)) },
                    onViewProposals = { navController.navigate(Screen.ProposalsReview.createRoute(mediationId)) },
                    onViewAgreement = { navController.navigate(Screen.AgreementResolved.createRoute(mediationId)) },
                    onFollowUpClick = { participantId -> navController.navigate(Screen.FollowUpScreen.createRoute(mediationId, participantId)) },
                    onSafetyClick = { navController.navigate(Screen.SafetyIntervention.createRoute(mediationId)) }
                )
            }

            composableWithMotion(
                route = Screen.PrivateSessionChat.route,
                arguments = listOf(navArgument("mediationId") { type = NavType.StringType }, navArgument("participantId") { type = NavType.StringType })
            ) { backStackEntry ->
                val mediationId = backStackEntry.arguments?.getString("mediationId") ?: return@composableWithMotion
                val participantId = backStackEntry.arguments?.getString("participantId") ?: return@composableWithMotion
                PrivateSessionChatScreen(
                    mediationId = mediationId,
                    participantId = participantId,
                    repository = repository,
                    onBackClick = { navController.popBackStack() },
                    onSessionCompleted = { navController.popBackStack() },
                    onSafetyAlertTriggered = { navController.navigate(Screen.SafetyIntervention.createRoute(mediationId)) }
                )
            }

            composableWithMotion(
                route = Screen.ConflictAnalysisScreen.route,
                arguments = listOf(navArgument("mediationId") { type = NavType.StringType })
            ) { backStackEntry ->
                val mediationId = backStackEntry.arguments?.getString("mediationId") ?: return@composableWithMotion
                ConflictAnalysisScreen(
                    mediationId = mediationId,
                    repository = repository,
                    onBackClick = { navController.popBackStack() },
                    onProceedToProposals = { navController.navigate(Screen.ProposalsReview.createRoute(mediationId)) }
                )
            }

            composableWithMotion(
                route = Screen.ProposalsReview.route,
                arguments = listOf(navArgument("mediationId") { type = NavType.StringType })
            ) { backStackEntry ->
                val mediationId = backStackEntry.arguments?.getString("mediationId") ?: return@composableWithMotion
                ProposalsReviewScreen(
                    mediationId = mediationId,
                    repository = repository,
                    onBackClick = { navController.popBackStack() },
                    onAgreementFinalized = { navController.navigate(Screen.AgreementResolved.createRoute(mediationId)) { popUpTo(Screen.ProposalsReview.route) { inclusive = true } } }
                )
            }

            composableWithMotion(
                route = Screen.AgreementResolved.route,
                arguments = listOf(navArgument("mediationId") { type = NavType.StringType })
            ) { backStackEntry ->
                val mediationId = backStackEntry.arguments?.getString("mediationId") ?: return@composableWithMotion
                AgreementResolvedScreen(
                    mediationId = mediationId,
                    repository = repository,
                    onBackClick = { navController.popBackStack() },
                    onOpenPdfPreview = { navController.navigate(Screen.AgreementPdfPreview.createRoute(mediationId)) },
                    onOpenFollowUp = {
                        val pId = repository.myParticipantIdNow(mediationId)
                            ?: repository.mediations.value.firstOrNull { it.id == mediationId }?.participants?.firstOrNull()?.id
                            ?: ""
                        if (pId.isNotBlank()) navController.navigate(Screen.FollowUpScreen.createRoute(mediationId, pId))
                    }
                )
            }

            composableWithMotion(
                route = Screen.AgreementPdfPreview.route,
                arguments = listOf(navArgument("mediationId") { type = NavType.StringType })
            ) { backStackEntry ->
                val mediationId = backStackEntry.arguments?.getString("mediationId") ?: return@composableWithMotion
                AgreementPdfPreviewScreen(mediationId = mediationId, repository = repository, onBackClick = { navController.popBackStack() })
            }

            composableWithMotion(
                route = Screen.FollowUpScreen.route,
                arguments = listOf(navArgument("mediationId") { type = NavType.StringType }, navArgument("participantId") { type = NavType.StringType })
            ) { backStackEntry ->
                val mediationId = backStackEntry.arguments?.getString("mediationId") ?: return@composableWithMotion
                val participantId = backStackEntry.arguments?.getString("participantId") ?: return@composableWithMotion
                FollowUpScreen(
                    mediationId = mediationId,
                    participantId = participantId,
                    repository = repository,
                    onBackClick = { navController.popBackStack() },
                    onMediationReopened = { navController.navigate(Screen.MediationDetail.createRoute(mediationId)) { popUpTo(Screen.MediationDetail.route) { inclusive = true } } }
                )
            }

            composableWithMotion(
                route = Screen.SafetyIntervention.route,
                arguments = listOf(navArgument("mediationId") { type = NavType.StringType })
            ) { backStackEntry ->
                val mediationId = backStackEntry.arguments?.getString("mediationId") ?: return@composableWithMotion
                SafetyInterventionScreen(mediationId = mediationId, onBackClick = { navController.popBackStack() })
            }
        }
    }
}