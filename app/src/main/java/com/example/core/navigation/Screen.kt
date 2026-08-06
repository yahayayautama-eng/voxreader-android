package com.example.core.navigation

import kotlinx.serialization.Serializable

sealed interface Screen {
    @Serializable data object Splash : Screen
    @Serializable data object Onboarding : Screen
    @Serializable data object ModelSetup : Screen
    @Serializable data object Library : Screen
    @Serializable data object Import : Screen
    @Serializable data class BookDetails(val bookId: String) : Screen
    @Serializable data class Reader(val bookId: String) : Screen
    @Serializable data object VoiceSelection : Screen
    @Serializable data object Bookmarks : Screen
    @Serializable data object Search : Screen
    @Serializable data object Settings : Screen
    @Serializable data object About : Screen
}
