package com.example.core.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.core.ui.backEnter
import com.example.core.ui.backExit
import com.example.core.ui.fadeThroughEnter
import com.example.core.ui.fadeThroughExit
import com.example.core.ui.forwardEnter
import com.example.core.ui.forwardExit
import com.example.feature.about.AboutScreen
import com.example.feature.bookdetails.BookDetailsScreen
import com.example.feature.bookmarks.BookmarksScreen
import com.example.feature.importbook.ImportScreen
import com.example.feature.library.LibraryScreen
import com.example.feature.reader.ReaderScreen
import com.example.feature.search.SearchScreen
import com.example.feature.settings.SettingsScreen
import com.example.feature.splash.SplashScreen
import com.example.feature.stats.StatsScreen
import com.example.feature.voiceselection.VoiceSelectionScreen
import com.example.tts.TtsManager

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun VoxLeafNavGraph(
    ttsManager: TtsManager,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: Screen = Screen.Splash
) {
    // Hosts the spine -> cover container transform; the scope has to outlive both destinations.
    SharedTransitionLayout {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = modifier,
            enterTransition = { forwardEnter() },
            exitTransition = { forwardExit() },
            popEnterTransition = { backEnter() },
            popExitTransition = { backExit() }
        ) {
            composable<Screen.Splash>(
                exitTransition = { fadeOut(tween(220)) }
            ) {
                SplashScreen(
                    onNavigateToNext = {
                        navController.navigate(Screen.Library) {
                            popUpTo(Screen.Splash) { inclusive = true }
                        }
                    }
                )
            }
            // Peer tabs have no hierarchy between them, so they cross-fade rather than slide.
            composable<Screen.Library>(
                enterTransition = { fadeThroughEnter() },
                popEnterTransition = { fadeThroughEnter() },
                exitTransition = { fadeThroughExit() }
            ) {
                LibraryScreen(
                    sharedScope = this@SharedTransitionLayout,
                    animatedScope = this,
                    onNavigateToBook = { bookId ->
                        navController.navigate(Screen.BookDetails(bookId))
                    },
                    onNavigateToImport = {
                        navController.navigate(Screen.Import)
                    }
                )
            }
            composable<Screen.Import> {
                ImportScreen(
                    onNavigateToBookDetails = { bookId ->
                        navController.navigate(Screen.BookDetails(bookId)) {
                            popUpTo(Screen.Import) { inclusive = true }
                        }
                    }
                )
            }
            composable<Screen.BookDetails> { backStackEntry ->
                val bookDetails: Screen.BookDetails = backStackEntry.toRoute()
                BookDetailsScreen(
                    bookId = bookDetails.bookId,
                    sharedScope = this@SharedTransitionLayout,
                    animatedScope = this,
                    onNavigateToReader = { id, chapterIndex ->
                        navController.navigate(Screen.Reader(bookId = id, chapterIndex = chapterIndex))
                    },
                    onNavigateToBookmarks = {
                        navController.navigate(Screen.Bookmarks)
                    },
                    onNavigateToVoiceSelection = {
                        navController.navigate(Screen.VoiceSelection)
                    },
                    onBookDeleted = {
                        navController.popBackStack(Screen.Library, inclusive = false)
                    }
                )
            }
            // The reader is an immersive surface: it rises into place rather than sliding in from the side.
            composable<Screen.Reader>(
                enterTransition = { fadeIn(tween(280)) },
                popExitTransition = { fadeOut(tween(200)) }
            ) { backStackEntry ->
                val reader: Screen.Reader = backStackEntry.toRoute()
                ReaderScreen(
                    bookId = reader.bookId,
                    bookmarkChapterIndex = reader.chapterIndex,
                    bookmarkSentenceIndex = reader.sentenceIndex,
                    onNavigateBack = { navController.navigateUp() }
                )
            }
            composable<Screen.VoiceSelection> {
                VoiceSelectionScreen()
            }
            composable<Screen.Bookmarks>(
                enterTransition = { fadeThroughEnter() },
                popEnterTransition = { fadeThroughEnter() },
                exitTransition = { fadeThroughExit() }
            ) {
                BookmarksScreen(
                    onNavigateToReader = { bookmark ->
                        navController.navigate(
                            Screen.Reader(bookmark.bookId, bookmark.chapterIndex, bookmark.sentenceIndex)
                        )
                    }
                )
            }
            composable<Screen.Search>(
                enterTransition = { fadeThroughEnter() },
                popEnterTransition = { fadeThroughEnter() },
                exitTransition = { fadeThroughExit() }
            ) {
                SearchScreen(
                    onNavigateToBook = { bookId ->
                        navController.navigate(Screen.BookDetails(bookId))
                    }
                )
            }
            composable<Screen.Settings>(
                enterTransition = { fadeThroughEnter() },
                popEnterTransition = { fadeThroughEnter() },
                exitTransition = { fadeThroughExit() }
            ) {
                SettingsScreen(
                    onNavigateToVoiceSelection = { navController.navigate(Screen.VoiceSelection) },
                    onNavigateToAbout = { navController.navigate(Screen.About) },
                    onNavigateToStats = { navController.navigate(Screen.Stats) }
                )
            }
            composable<Screen.Stats> {
                StatsScreen()
            }
            composable<Screen.About> {
                AboutScreen()
            }
        }
    }
}
