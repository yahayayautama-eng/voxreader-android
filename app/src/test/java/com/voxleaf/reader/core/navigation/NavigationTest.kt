package com.voxleaf.reader.core.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.voxleaf.reader.data.local.datastore.AppSettingsManager
import com.voxleaf.reader.feature.library.LibraryScreenContent
import com.voxleaf.reader.feature.library.LibraryUiState
import com.voxleaf.reader.tts.EdgeTtsEngine
import com.voxleaf.reader.tts.SherpaTtsEngine
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
        val unusedNativeEngine = object : Lazy<SherpaTtsEngine> {
            override fun get(): SherpaTtsEngine = error("This navigation test does not synthesize speech")
        }
        val unusedEdgeEngine = object : Lazy<EdgeTtsEngine> {
            override fun get(): EdgeTtsEngine = error("This navigation test does not synthesize speech")
        }
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
}
