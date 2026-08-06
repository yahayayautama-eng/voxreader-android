package com.example.core.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.core.navigation.Screen
import com.example.core.navigation.VoxLeafNavGraph
import com.example.tts.TtsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoxLeafApp(
    ttsManager: TtsManager
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    val isTopLevelDestination = currentRoute?.contains(Screen.Library::class.qualifiedName!!) == true ||
            currentRoute?.contains(Screen.Bookmarks::class.qualifiedName!!) == true ||
            currentRoute?.contains(Screen.Search::class.qualifiedName!!) == true ||
            currentRoute?.contains(Screen.Settings::class.qualifiedName!!) == true

    val showTopBar = currentRoute?.contains(Screen.Splash::class.qualifiedName!!) != true &&
            currentRoute?.contains(Screen.Onboarding::class.qualifiedName!!) != true &&
            currentRoute?.contains(Screen.Reader::class.qualifiedName!!) != true

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = {
                        Text(
                            text = getTitleForRoute(currentRoute),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        if (!isTopLevelDestination) {
                            IconButton(onClick = { navController.navigateUp() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Navigate up"
                                )
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (isTopLevelDestination) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute?.contains(Screen.Library::class.qualifiedName!!) == true,
                        onClick = {
                            navController.navigate(Screen.Library) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null) },
                        label = { Text(stringResource(R.string.title_library)) }
                    )
                    NavigationBarItem(
                        selected = currentRoute?.contains(Screen.Bookmarks::class.qualifiedName!!) == true,
                        onClick = {
                            navController.navigate(Screen.Bookmarks) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Bookmarks, contentDescription = null) },
                        label = { Text(stringResource(R.string.title_bookmarks)) }
                    )
                    NavigationBarItem(
                        selected = currentRoute?.contains(Screen.Search::class.qualifiedName!!) == true,
                        onClick = {
                            navController.navigate(Screen.Search) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text(stringResource(R.string.title_search)) }
                    )
                    NavigationBarItem(
                        selected = currentRoute?.contains(Screen.Settings::class.qualifiedName!!) == true,
                        onClick = {
                            navController.navigate(Screen.Settings) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.title_settings)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        VoxLeafNavGraph(
            ttsManager = ttsManager,
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
fun getTitleForRoute(route: String?): String {
    if (route == null) return stringResource(R.string.app_name)
    return when {
        route.contains(Screen.Library::class.qualifiedName!!) -> stringResource(R.string.title_library)
        route.contains(Screen.Bookmarks::class.qualifiedName!!) -> stringResource(R.string.title_bookmarks)
        route.contains(Screen.Search::class.qualifiedName!!) -> stringResource(R.string.title_search)
        route.contains(Screen.Settings::class.qualifiedName!!) -> stringResource(R.string.title_settings)
        route.contains(Screen.ModelSetup::class.qualifiedName!!) -> stringResource(R.string.title_model_setup)
        route.contains(Screen.Import::class.qualifiedName!!) -> stringResource(R.string.title_import)
        route.contains(Screen.BookDetails::class.qualifiedName!!) -> stringResource(R.string.title_book_details)
        route.contains(Screen.Reader::class.qualifiedName!!) -> stringResource(R.string.title_reader)
        route.contains(Screen.VoiceSelection::class.qualifiedName!!) -> stringResource(R.string.title_voice_selection)
        route.contains(Screen.About::class.qualifiedName!!) -> stringResource(R.string.title_about)
        else -> stringResource(R.string.app_name)
    }
}
