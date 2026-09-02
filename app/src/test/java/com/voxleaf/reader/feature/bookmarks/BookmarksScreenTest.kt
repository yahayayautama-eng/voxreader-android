package com.voxleaf.reader.feature.bookmarks

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.voxleaf.reader.domain.repository.Highlight
import com.voxleaf.reader.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookmarksScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `tabs expose role and selected state without duplicate descriptions`() {
        val selected = mutableListOf<SavedItemsTab>()
        composeRule.setContent {
            MyApplicationTheme {
                SavedItemsTabs(SavedItemsTab.BOOKMARKS, selected::add)
            }
        }

        val bookmarks = composeRule.onNodeWithTag("saved_items_tab_bookmarks")
        val highlights = composeRule.onNodeWithTag("saved_items_tab_highlights")
        bookmarks.assertIsSelected()
        highlights.assertIsNotSelected()
        val config = bookmarks.fetchSemanticsNode().config
        assertEquals(Role.Tab, config[SemanticsProperties.Role])
        assertFalse(config.contains(SemanticsProperties.ContentDescription))

        highlights.performClick()
        composeRule.runOnIdle { assertEquals(listOf(SavedItemsTab.HIGHLIGHTS), selected) }
    }

    @Test
    fun `highlight export explicitly states all-items scope`() {
        val highlight = Highlight(
            id = "highlight-1",
            bookId = "book-1",
            chapterIndex = 0,
            sentenceIndex = 1,
            chapterTitle = "Chapter One",
            text = "A passage",
            colorIndex = 0,
            timestamp = 10L
        )
        val shelf = HighlightShelf("book-1", "A Book", listOf(highlight))
        composeRule.setContent {
            MyApplicationTheme {
                BookmarksScreenContent(
                    uiState = BookmarksUiState(
                        selectedTab = SavedItemsTab.HIGHLIGHTS,
                        allHighlightShelves = listOf(shelf),
                        highlightShelves = listOf(shelf)
                    ),
                    onTabSelect = {},
                    onQueryChange = {},
                    onSortSelect = {},
                    onNavigateToReader = {},
                    onDeleteBookmark = {},
                    onDeleteHighlight = {}
                )
            }
        }

        composeRule.onNodeWithText(
            "Exports all highlights as Markdown, regardless of the current search or sort."
        ).assertExists()
        composeRule.onNodeWithContentDescription("Export all highlights as Markdown").assertExists()
    }
}
