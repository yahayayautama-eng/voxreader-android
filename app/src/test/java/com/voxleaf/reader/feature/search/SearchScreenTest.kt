package com.voxleaf.reader.feature.search

import androidx.activity.ComponentActivity
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.voxleaf.reader.domain.repository.Book
import com.voxleaf.reader.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalSharedTransitionApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `search uses truthful metadata terminology and preserves input tag`() {
        setContent(
            SearchUiState.Ready(
                query = "",
                availableGenres = listOf("History"),
                selectedGenre = null,
                selectedSort = SearchSort.RELEVANCE,
                libraryIsEmpty = false,
                results = emptyList()
            )
        )

        composeRule.onNodeWithTag("search_screen_input").assertIsDisplayed()
        composeRule.onNodeWithText("Search library").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Searches title, author, description, and genre metadata. Book text and pages are not searched."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("History").assertIsDisplayed()
        composeRule.onNodeWithText("Classic").assertDoesNotExist()
        composeRule.onNodeWithText("Sort · Relevance").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Sort search results, Relevance").assertIsDisplayed()
    }

    @Test
    fun `result count is accurate and selecting a row returns the stable book id`() {
        val opened = mutableListOf<String>()
        val book = Book("book-1", "Dune", "Frank Herbert", genre = "Science Fiction")
        setContent(
            SearchUiState.Ready(
                query = "Dune",
                availableGenres = listOf("Science Fiction"),
                selectedGenre = null,
                selectedSort = SearchSort.RELEVANCE,
                libraryIsEmpty = false,
                results = listOf(SearchResultItem(book, SearchMatch.TITLE))
            ),
            onNavigateToBook = opened::add
        )

        composeRule.onNodeWithText("1 result").assertIsDisplayed()
        composeRule.onNodeWithText("Matches title").assertIsDisplayed()
        composeRule.onNodeWithTag("search_result_book-1").performClick()
        composeRule.runOnIdle { assertEquals(listOf("book-1"), opened) }
    }

    private fun setContent(
        state: SearchUiState,
        onNavigateToBook: (String) -> Unit = {}
    ) {
        composeRule.setContent {
            MyApplicationTheme {
                SearchScreenContent(
                    uiState = state,
                    onQueryChange = {},
                    onGenreSelect = {},
                    onSortSelect = {},
                    onNavigateToBook = onNavigateToBook
                )
            }
        }
    }
}
