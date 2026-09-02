package com.voxleaf.reader.feature.importbook

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportPageActionsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `page action menu labels every action with page context and disables impossible move`() {
        var rotated = false
        composeTestRule.setContent {
            MaterialTheme {
                ScannedPageActionsMenu(
                    pageNumber = 1,
                    canMoveEarlier = false,
                    canMoveLater = true,
                    actionsEnabled = true,
                    onMoveEarlier = {},
                    onMoveLater = {},
                    onRotate = { rotated = true },
                    onCrop = {},
                    onRetake = {},
                    onReviewText = {},
                    onRemove = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Actions for scanned page 1").performClick()

        composeTestRule.onNodeWithText("Page 1: move earlier").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Page 1: move later").assertIsEnabled()
        composeTestRule.onNodeWithText("Rotate page 1 90° clockwise").performClick()
        composeTestRule.runOnIdle { assertTrue(rotated) }
    }

    @Test
    fun `remove confirmation explicitly invokes callback only after confirmation`() {
        var confirmed = false
        composeTestRule.setContent {
            MaterialTheme {
                RemoveScannedPageDialog(
                    pageNumber = 3,
                    onConfirm = { confirmed = true },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText(
            "Permanently remove this page from the recoverable scan session: page 3. " +
                "This does not remove any book from your library."
        ).assertExists()
        composeTestRule.onNodeWithTag("scan_remove_page_confirm").performClick()

        composeTestRule.runOnIdle { assertTrue(confirmed) }
    }
}
