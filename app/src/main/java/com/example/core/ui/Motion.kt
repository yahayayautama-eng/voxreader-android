package com.example.core.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

/**
 * Material 3 motion specs. Compose scales these by the system animator duration setting, so
 * "Remove animations" in accessibility settings collapses them to instant cuts for free.
 */
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

const val MOTION_ENTER_MS = 300
const val MOTION_EXIT_MS = 220

/** Fade-through: for switching between peer destinations that have no spatial relationship. */
fun fadeThroughEnter(): EnterTransition =
    fadeIn(tween(MOTION_ENTER_MS, delayMillis = 60, easing = EmphasizedDecelerate)) +
        scaleIn(tween(MOTION_ENTER_MS, delayMillis = 60, easing = EmphasizedDecelerate), initialScale = 0.94f)

fun fadeThroughExit(): ExitTransition =
    fadeOut(tween(MOTION_EXIT_MS, easing = Emphasized))

/** Shared X axis: for forward/back moves through a hierarchy, where direction carries meaning. */
fun AnimatedContentTransitionScope<NavBackStackEntry>.forwardEnter(): EnterTransition =
    slideInHorizontally(tween(MOTION_ENTER_MS, easing = Emphasized)) { it / 6 } +
        fadeIn(tween(MOTION_ENTER_MS, easing = Emphasized))

fun AnimatedContentTransitionScope<NavBackStackEntry>.forwardExit(): ExitTransition =
    slideOutHorizontally(tween(MOTION_EXIT_MS, easing = Emphasized)) { -it / 8 } +
        fadeOut(tween(MOTION_EXIT_MS, easing = Emphasized))

fun AnimatedContentTransitionScope<NavBackStackEntry>.backEnter(): EnterTransition =
    slideInHorizontally(tween(MOTION_ENTER_MS, easing = Emphasized)) { -it / 8 } +
        fadeIn(tween(MOTION_ENTER_MS, easing = Emphasized))

fun AnimatedContentTransitionScope<NavBackStackEntry>.backExit(): ExitTransition =
    slideOutHorizontally(tween(MOTION_EXIT_MS, easing = Emphasized)) { it / 6 } +
        fadeOut(tween(MOTION_EXIT_MS, easing = Emphasized))
