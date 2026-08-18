package com.voxleaf.reader.core.navigation

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
import com.voxleaf.reader.core.ui.backEnter
import com.voxleaf.reader.core.ui.backExit
import com.voxleaf.reader.core.ui.fadeThroughEnter
import com.voxleaf.reader.core.ui.fadeThroughExit
import com.voxleaf.reader.core.ui.forwardEnter
import com.voxleaf.reader.core.ui.forwardExit
import com.voxleaf.reader.feature.about.AboutScreen
import com.voxleaf.reader.feature.bookdetails.BookDetailsScreen
import com.voxleaf.reader.feature.bookmarks.BookmarksScreen
import com.voxleaf.reader.feature.importbook.ImportScreen
import com.voxleaf.reader.feature.library.LibraryScreen
import com.voxleaf.reader.feature.reader.ReaderScreen
import com.voxleaf.reader.feature.search.SearchScreen
import com.voxleaf.reader.feature.settings.SettingsScreen
import com.voxleaf.reader.feature.splash.SplashScreen
import com.voxleaf.reader.feature.stats.StatsScreen
import com.voxleaf.reader.feature.voiceselection.VoiceSelectionScreen
import com.voxleaf.reader.tts.TtsManager

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
