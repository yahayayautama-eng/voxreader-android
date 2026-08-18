package com.voxleaf.reader.core.navigation

import kotlinx.serialization.Serializable

sealed interface Screen {
    @Serializable data object Splash : Screen
    @Serializable data object Library : Screen
    @Serializable data object Import : Screen
    @Serializable data class BookDetails(val bookId: String) : Screen
    @Serializable data class Reader(
        val bookId: String,
        val chapterIndex: Int? = null,
        val sentenceIndex: Int? = null
    ) : Screen
    @Serializable data object VoiceSelection : Screen
    @Serializable data object Bookmarks : Screen
    @Serializable data object Search : Screen
    @Serializable data object Settings : Screen
    @Serializable data object Stats : Screen
    @Serializable data object About : Screen
}
