package com.voxleaf.reader.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.core.app.ApplicationProvider
import com.voxleaf.reader.R
import com.voxleaf.reader.domain.repository.Book
import com.voxleaf.reader.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val books = listOf(
        Book("book-1", "A Test Book", "An Author", totalChapters = 4, currentChapterIndex = 2),
        Book("book-2", "Another Book", "Another Author")
    )

    @Test
    fun `long press selects and never requests deletion`() {
        val actions = mutableListOf<LibraryUiAction>()
        setLibraryContent(actions = actions)

        composeRule.onNodeWithTag("book_card_book-1")
            .performTouchInput { longClick() }

        composeRule.runOnIdle {
            assertEquals(listOf(LibraryUiAction.OnSelectBook("book-1")), actions)
            assertFalse(actions.any { it is LibraryUiAction.OnRequestDelete })
            assertFalse(actions.any { it is LibraryUiAction.OnConfirmDelete })
        }
    }

    @Test
    fun `selection context exposes count actions and selected semantics`() {
        setLibraryContent(selectedBookIds = setOf("book-1"))

        composeRule.onNodeWithText("1 selected").assertExists()
        composeRule.onNodeWithText("Close").assertExists()
        composeRule.onNodeWithText("Select all").assertExists()
        composeRule.onNodeWithText("Delete").assertExists()
        composeRule.onNodeWithTag("book_card_book-1").assertIsSelected()
        composeRule.onNodeWithContentDescription("More options for A Test Book").assertExists()
    }

    @Test
    fun `normal browsing card does not expose selected semantics`() {
        setLibraryContent()

        val semantics = composeRule.onNodeWithTag("book_card_book-1")
            .fetchSemanticsNode().config
        assertFalse(semantics.contains(SemanticsProperties.Selected))
    }

    @Test
    fun `card overflow provides explicit select action`() {
        val actions = mutableListOf<LibraryUiAction>()
        setLibraryContent(actions = actions)

        composeRule.onNodeWithContentDescription("More options for A Test Book").performClick()
        composeRule.onNodeWithText("Select").performClick()

        composeRule.runOnIdle {
            assertTrue(actions.contains(LibraryUiAction.OnSelectBook("book-1")))
        }
    }

    @Test
    fun `card overflow preserves the favorite action`() {
        val actions = mutableListOf<LibraryUiAction>()
        setLibraryContent(actions = actions)

        composeRule.onNodeWithContentDescription("More options for A Test Book").performClick()
        composeRule.onNodeWithText("Add to favorites").performClick()

        composeRule.runOnIdle {
            assertTrue(actions.contains(LibraryUiAction.OnToggleFavorite("book-1")))
        }
    }

    @Test
    fun `library card visibly bounds title author and progress`() {
        setLibraryContent()

        composeRule.onNodeWithText("A Test Book", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("An Author", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("50%", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `escape and system back exit selection before navigation`() {
        val actions = mutableListOf<LibraryUiAction>()
        setLibraryContent(selectedBookIds = setOf("book-1"), actions = actions)

        composeRule.onNodeWithTag("book_card_book-1")
            .requestFocus()
            .performKeyInput { pressKey(Key.Escape) }
        composeRule.runOnIdle {
            assertTrue(actions.contains(LibraryUiAction.OnClearSelection))
            actions.clear()
        }

        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.runOnIdle {
            assertEquals(listOf(LibraryUiAction.OnClearSelection), actions)
        }
    }

    @Test
    fun `delete confirmation uses exact plural count and complete consequence copy`() {
        setLibraryContent(
            selectedBookIds = setOf("book-1", "book-2"),
            showDeleteConfirmation = true
        )

        composeRule.onNodeWithText("Delete 2 books?").assertExists()
        composeRule.onNodeWithText(
            "This permanently removes 2 books, their reading progress, bookmarks, highlights, and generated audio from this device."
        ).assertExists()

        val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        assertEquals(
            "Delete 1 book?",
            resources.getQuantityString(R.plurals.library_delete_title, 1, 1)
        )
    }

    private fun setLibraryContent(
        selectedBookIds: Set<String> = emptySet(),
        showDeleteConfirmation: Boolean = false,
        actions: MutableList<LibraryUiAction> = mutableListOf()
    ) {
        composeRule.setContent {
            MyApplicationTheme {
                LibraryScreenContent(
                    uiState = LibraryUiState.Success(
                        allBooks = books,
                        selectedBookIds = selectedBookIds,
                        showDeleteConfirmation = showDeleteConfirmation
                    ),
                    onAction = actions::add
                )
            }
        }
    }
}
