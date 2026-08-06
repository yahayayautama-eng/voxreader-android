package com.example.core.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.example.feature.library.LibraryScreenContent
import com.example.feature.library.LibraryUiState
import com.example.tts.TtsManager
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun appNavigatesToOnboardingFromSplash() {
        lateinit var navController: TestNavHostController
        val ttsManager = TtsManager(ApplicationProvider.getApplicationContext())

        composeTestRule.setContent {
            navController = TestNavHostController(ApplicationProvider.getApplicationContext())
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            VoxLeafNavGraph(ttsManager = ttsManager, navController = navController)
        }

        composeTestRule.waitForIdle()

        // Splash screen is start destination
        assertEquals(Screen.Splash::class.qualifiedName, navController.currentDestination?.route)
    }

    @Test
    fun libraryScreen_rendersLoadingState() {
        composeTestRule.setContent {
            LibraryScreenContent(
                uiState = LibraryUiState.Loading,
                onAction = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Loading").assertExists()
    }

    @Test
    fun libraryScreen_rendersEmptyState() {
        composeTestRule.setContent {
            LibraryScreenContent(
                uiState = LibraryUiState.Empty,
                onAction = {}
            )
        }

        composeTestRule.onNodeWithText("Library is empty").assertExists()
    }
}
