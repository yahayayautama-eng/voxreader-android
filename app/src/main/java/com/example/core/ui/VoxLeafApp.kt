package com.example.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.core.navigation.Screen
import com.example.core.navigation.VoxLeafNavGraph
import com.example.core.ui.player.GlobalPlayerBar
import com.example.tts.TtsManager
import com.example.ui.theme.SignalOrange

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

    val isReaderScreen = currentRoute?.contains(Screen.Reader::class.qualifiedName!!) == true

    // Top-level screens carry their own wordmark-style headings; a title bar would just repeat them.
    val showTopBar = currentRoute?.contains(Screen.Splash::class.qualifiedName!!) != true &&
            !isReaderScreen &&
            !isTopLevelDestination

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Material: bottom navigation bar on compact width, navigation rail once the window is wide.
        val useRail = maxWidth >= 600.dp
        val showRail = isTopLevelDestination && useRail

        Row(modifier = Modifier.fillMaxSize()) {
            if (showRail) {
                VoxLeafNavRail(navController = navController, currentRoute = currentRoute)
            }
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
                                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                            contentDescription = "Navigate up"
                                        )
                                    }
                                }
                            }
                        )
                    }
                },
                bottomBar = {
                    Column {
                        // Hidden on the Reader itself, which already has its own full player bar.
                        if (!isReaderScreen) {
                            GlobalPlayerBar(
                                onNavigateToReader = { bookId, chapterIndex, sentenceIndex ->
                                    navController.navigate(Screen.Reader(bookId, chapterIndex, sentenceIndex))
                                }
                            )
                        }
                        if (isTopLevelDestination && !useRail) {
                            Spacer(modifier = androidx.compose.ui.Modifier.height(4.dp))
                            VoxLeafBottomBar(navController = navController, currentRoute = currentRoute)
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
    }
}

private fun NavHostController.navigateToTab(screen: Screen) = navigate(screen) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun VoxLeafNavRail(navController: NavHostController, currentRoute: String?) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxHeight()
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        navTabs().forEach { tab ->
            val selected = currentRoute?.contains(tab.screen::class.qualifiedName!!) == true
            NavigationRailItem(
                selected = selected,
                onClick = { navController.navigateToTab(tab.screen) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.labelRes)) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = SignalOrange,
                    selectedTextColor = SignalOrange,
                    indicatorColor = SignalOrange.copy(alpha = 0.14f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

private data class NavTab(val screen: Screen, val icon: ImageVector, val labelRes: Int)

private fun navTabs() = listOf(
    NavTab(Screen.Library, Icons.AutoMirrored.Outlined.LibraryBooks, R.string.title_library),
    NavTab(Screen.Bookmarks, Icons.Outlined.BookmarkBorder, R.string.title_bookmarks),
    NavTab(Screen.Search, Icons.Outlined.Search, R.string.title_search),
    NavTab(Screen.Settings, Icons.Outlined.Tune, R.string.title_settings),
)

@Composable
private fun VoxLeafBottomBar(navController: NavHostController, currentRoute: String?) {
    val tabs = navTabs()

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute?.contains(tab.screen::class.qualifiedName!!) == true
                val tint = if (selected) SignalOrange else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) SignalOrange.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            navController.navigate(tab.screen) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(tab.icon, contentDescription = stringResource(tab.labelRes), tint = tint)
                    Text(
                        text = stringResource(tab.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = tint
                    )
                }
            }
        }
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
        route.contains(Screen.Import::class.qualifiedName!!) -> stringResource(R.string.title_import)
        route.contains(Screen.BookDetails::class.qualifiedName!!) -> stringResource(R.string.title_book_details)
        route.contains(Screen.Reader::class.qualifiedName!!) -> stringResource(R.string.title_reader)
        route.contains(Screen.VoiceSelection::class.qualifiedName!!) -> stringResource(R.string.title_voice_selection)
        route.contains(Screen.About::class.qualifiedName!!) -> stringResource(R.string.title_about)
        else -> stringResource(R.string.app_name)
    }
}
