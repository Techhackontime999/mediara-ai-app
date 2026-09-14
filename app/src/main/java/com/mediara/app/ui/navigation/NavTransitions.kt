package com.mediara.app.ui.navigation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

// Deterministic motion tokens. Forward navigation slides in from the right,
// back navigation reverses left-to-right. Auth/overlay screens rise from the
// bottom to feel less "foreign", matching the skill's rule that one thing
// leads the eye at a time.
private const val ForwardDuration = 320
private const val BackDuration = 280
private const val FadeDuration = 220

private val Standard = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
private val ExitEasing = CubicBezierEasing(0.4f, 0.0f, 0.6f, 1.0f)

fun forwardIn(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(ForwardDuration, easing = Standard),
        initialOffsetX = { it / 6 }
    ) + fadeIn(animationSpec = tween(FadeDuration))

fun forwardOut(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(ForwardDuration, easing = ExitEasing),
        targetOffsetX = { -it / 6 }
    ) + fadeOut(animationSpec = tween(FadeDuration))

fun backwardIn(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(BackDuration, easing = Standard),
        initialOffsetX = { -it / 6 }
    ) + fadeIn(animationSpec = tween(FadeDuration))

fun backwardOut(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(BackDuration, easing = ExitEasing),
        targetOffsetX = { it / 6 }
    ) + fadeOut(animationSpec = tween(FadeDuration))

// Subtle crossfade for top-level bottom-bar destinations (dashboard,
// profile, settings) so switching tabs feels calm rather than pushing around.
fun crossfadeIn(): EnterTransition = fadeIn(animationSpec = tween(FadeDuration))
fun crossfadeOut(): ExitTransition = fadeOut(animationSpec = tween(FadeDuration))

// Rise-from-bottom used by auth and overlay screens.
fun riseIn(): EnterTransition =
    slideInVertically(
        animationSpec = tween(ForwardDuration, easing = Standard),
        initialOffsetY = { it / 5 }
    ) + fadeIn(animationSpec = tween(FadeDuration))

fun riseOut(): ExitTransition =
    slideOutVertically(
        animationSpec = tween(ForwardDuration, easing = ExitEasing),
        targetOffsetY = { it / 5 }
    ) + fadeOut(animationSpec = tween(FadeDuration))

/*
 * Route groups so a single NavHost description decides the right animation
 * type instead of hand-picking per screen (single source of truth).
 */

internal enum class RouteMotion { STANDARD, CROSSFADE, RISE }

/** Classifies a NavHost destination into its motion group. */
internal fun routeMotion(route: String?): RouteMotion = when (route) {
    in Screen.CrossfadeRoutes -> RouteMotion.CROSSFADE
    in Screen.RiseRoutes -> RouteMotion.RISE
    else -> RouteMotion.STANDARD
}

internal fun RouteMotion.asEnter(): EnterTransition = when (this) {
    RouteMotion.STANDARD -> forwardIn()
    RouteMotion.CROSSFADE -> crossfadeIn()
    RouteMotion.RISE -> riseIn()
}

internal fun RouteMotion.asExit(): ExitTransition = when (this) {
    RouteMotion.STANDARD -> forwardOut()
    RouteMotion.CROSSFADE -> crossfadeOut()
    RouteMotion.RISE -> riseOut()
}

internal fun RouteMotion.asPopEnter(): EnterTransition = when (this) {
    RouteMotion.STANDARD -> backwardIn()
    RouteMotion.CROSSFADE -> crossfadeIn()
    RouteMotion.RISE -> riseIn()
}

internal fun RouteMotion.asPopExit(): ExitTransition = when (this) {
    RouteMotion.STANDARD -> backwardOut()
    RouteMotion.CROSSFADE -> crossfadeOut()
    RouteMotion.RISE -> riseOut()
}

// Internal helper used by composableWithMotion.
internal fun transitionsFor(motion: RouteMotion) = TransitionSet(
    enter = motion.asEnter(),
    exit = motion.asExit(),
    popEnter = motion.asPopEnter(),
    popExit = motion.asPopExit()
)

internal data class TransitionSet(
    val enter: EnterTransition,
    val exit: ExitTransition,
    val popEnter: EnterTransition,
    val popExit: ExitTransition
)

/**
 * Like NavGraphBuilder.composable but automatically applies this route's
 * motion group (determined from [route]) so screens self-animate. Callers keep
 * the exact same [content] signature as the platform composable.
 */
internal fun NavGraphBuilder.composableWithMotion(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    val t = transitionsFor(routeMotion(route))
    @Suppress("DEPRECATION")
    composable(
        route = route,
        arguments = arguments,
        enterTransition = { t.enter },
        exitTransition = { t.exit },
        popEnterTransition = { t.popEnter },
        popExitTransition = { t.popExit },
        content = content
    )
}