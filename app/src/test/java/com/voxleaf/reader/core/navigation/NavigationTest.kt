package com.voxleaf.reader.core.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.graphics.Color
import com.voxleaf.reader.data.local.datastore.AppSettingsManager
import com.voxleaf.reader.feature.library.LibraryScreenContent
import com.voxleaf.reader.feature.library.LibraryUiState
import com.voxleaf.reader.domain.repository.Book
import com.voxleaf.reader.core.ui.getTitleForRoute
import com.voxleaf.reader.ui.theme.MyApplicationTheme
import com.voxleaf.reader.ui.theme.ObsidianDark
import com.voxleaf.reader.tts.TtsEngine
import com.voxleaf.reader.tts.TtsManager
import dagger.Lazy
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import io.mockk.mockk
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalSharedTransitionApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun appStartsAtSplash() {
        composeTestRule.mainClock.autoAdvance = false
        lateinit var navController: TestNavHostController
        val unusedNativeEngine = Lazy<TtsEngine> { error("This navigation test does not synthesize speech") }
        val unusedEdgeEngine = Lazy<TtsEngine> { error("This navigation test does not synthesize speech") }
        val appSettingsManager = AppSettingsManager(ApplicationProvider.getApplicationContext())
        val ttsManager = TtsManager(
            ApplicationProvider.getApplicationContext(),
            unusedNativeEngine,
            unusedEdgeEngine,
            appSettingsManager
        )

        composeTestRule.setContent {
            navController = TestNavHostController(ApplicationProvider.getApplicationContext())
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            VoxLeafNavGraph(ttsManager = ttsManager, navController = navController)
        }

        // Splash screen is start destination
        composeTestRule.onNodeWithTag("splash_screen").assertExists()
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

        composeTestRule.onNodeWithContentDescription("Loading your library").assertExists()
    }

    @Test
    fun libraryScreen_rendersEmptyState() {
        composeTestRule.setContent {
            LibraryScreenContent(
                uiState = LibraryUiState.Empty,
                onAction = {}
            )
        }

        composeTestRule.onNodeWithText("Build your shelf").assertExists()
    }

    @Test
    fun statsRoute_usesListeningStatsTitle() {
        composeTestRule.setContent {
            Text(getTitleForRoute(Screen.Stats::class.qualifiedName))
        }

        composeTestRule.onNodeWithText("Listening stats").assertExists()
    }

    @Test
    fun libraryBookCard_exposesProgressSemantics() {
        composeTestRule.setContent {
            LibraryScreenContent(
                uiState = LibraryUiState.Success(
                    allBooks = listOf(
                        Book(
                            id = "book-1",
                            title = "A Test Book",
                            author = "An Author",
                            totalChapters = 4,
                            currentChapterIndex = 2
                        )
                    )
                ),
                onAction = {}
            )
        }

        composeTestRule.onNodeWithContentDescription(
            "A Test Book, An Author, 50 percent complete"
        ).assertExists()
    }

    @Test
    fun libraryBookCard_omitsBlankAuthorFromDescription() {
        composeTestRule.setContent {
            LibraryScreenContent(
                uiState = LibraryUiState.Success(
                    allBooks = listOf(
                        Book(
                            id = "book-blank-author",
                            title = "A Test Book",
                            author = "",
                            totalChapters = 4,
                            currentChapterIndex = 2
                        )
                    )
                ),
                onAction = {}
            )
        }

        composeTestRule.onNodeWithContentDescription(
            "A Test Book, 50 percent complete"
        ).assertExists()
    }

    @Test
    fun themeUsesPassingInteractiveForegrounds() {
        var darkPrimaryForeground = Color.Unspecified
        var lightSecondaryForeground = Color.Unspecified

        composeTestRule.setContent {
            MyApplicationTheme {
                darkPrimaryForeground = MaterialTheme.colorScheme.onPrimary
                Text("dark")
            }
            MyApplicationTheme(darkTheme = false) {
                lightSecondaryForeground = MaterialTheme.colorScheme.onSecondary
                Text("light")
            }
        }
        composeTestRule.runOnIdle {
            assertEquals(ObsidianDark, darkPrimaryForeground)
            assertEquals(Color.White, lightSecondaryForeground)
        }
    }
}
