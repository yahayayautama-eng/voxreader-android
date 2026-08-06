package com.example.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.feature.about.AboutScreen
import com.example.feature.bookdetails.BookDetailsScreen
import com.example.feature.bookmarks.BookmarksScreen
import com.example.feature.importbook.ImportScreen
import com.example.feature.library.LibraryScreen
import com.example.feature.modelsetup.ModelSetupScreen
import com.example.feature.onboarding.OnboardingScreen
import com.example.feature.reader.ReaderScreen
import com.example.feature.search.SearchScreen
import com.example.feature.settings.SettingsScreen
import com.example.feature.splash.SplashScreen
import com.example.feature.voiceselection.VoiceSelectionScreen
import com.example.tts.TtsManager

@Composable
fun VoxLeafNavGraph(
    ttsManager: TtsManager,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: Screen = Screen.Splash
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable<Screen.Splash> {
            SplashScreen(
                onNavigateToNext = {
                    navController.navigate(Screen.Onboarding) {
                        popUpTo(Screen.Splash) { inclusive = true }
                    }
                }
            )
        }
        composable<Screen.Onboarding> {
            OnboardingScreen(
                onNavigateToNext = {
                    navController.navigate(Screen.Library) {
                        popUpTo(Screen.Onboarding) { inclusive = true }
                    }
                }
            )
        }
        composable<Screen.ModelSetup> {
            ModelSetupScreen(
                onNavigateToLibrary = {
                    navController.navigate(Screen.Library) {
                        popUpTo(Screen.ModelSetup) { inclusive = true }
                    }
                }
            )
        }
        composable<Screen.Library> {
            LibraryScreen(
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
                onNavigateToReader = { id ->
                    navController.navigate(Screen.Reader(id))
                },
                onNavigateToVoiceSelection = {
                    navController.navigate(Screen.VoiceSelection)
                }
            )
        }
        composable<Screen.Reader> { backStackEntry ->
            val reader: Screen.Reader = backStackEntry.toRoute()
            ReaderScreen(
                bookId = reader.bookId,
                onNavigateBack = { navController.navigateUp() }
            )
        }
        composable<Screen.VoiceSelection> {
            VoiceSelectionScreen()
        }
        composable<Screen.Bookmarks> {
            BookmarksScreen(
                onNavigateToReader = { bookId ->
                    navController.navigate(Screen.Reader(bookId))
                }
            )
        }
        composable<Screen.Search> {
            SearchScreen(
                onNavigateToBook = { bookId ->
                    navController.navigate(Screen.BookDetails(bookId))
                }
            )
        }
        composable<Screen.Settings> {
            SettingsScreen(
                onNavigateToVoiceSelection = { navController.navigate(Screen.VoiceSelection) },
                onNavigateToModelSetup = { navController.navigate(Screen.ModelSetup) },
                onNavigateToAbout = { navController.navigate(Screen.About) }
            )
        }
        composable<Screen.About> {
            AboutScreen()
        }
    }
}
