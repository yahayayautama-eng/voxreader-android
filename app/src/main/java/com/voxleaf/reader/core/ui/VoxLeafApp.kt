package com.voxleaf.reader.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import com.voxleaf.reader.ui.theme.AzureContainer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.voxleaf.reader.R
import com.voxleaf.reader.core.navigation.Screen
import com.voxleaf.reader.core.navigation.VoxLeafNavGraph
import com.voxleaf.reader.core.ui.player.GlobalPlayerBar
import com.voxleaf.reader.tts.TtsManager
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoxLeafApp(
    ttsManager: TtsManager,
    incomingDocumentUris: StateFlow<List<String>>? = null,
    onIncomingDocumentsConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    val sharedUris by (incomingDocumentUris ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList()) })
        .collectAsState()

    LaunchedEffect(sharedUris) {
        if (sharedUris.isNotEmpty()) {
            navController.navigate(Screen.Import) { launchSingleTop = true }
        }
    }

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
        val navigationChrome = navigationChromeFor(maxWidth)
        val showRail = navigationChrome == NavigationChrome.RAIL &&
            currentRoute != null &&
            currentRoute?.contains(Screen.Splash::class.qualifiedName!!) != true &&
            !isReaderScreen

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
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                GlobalPlayerBar(
                                    onNavigateToReader = { bookId, chapterIndex, sentenceIndex ->
                                        navController.navigate(Screen.Reader(bookId, chapterIndex, sentenceIndex))
                                    },
                                    modifier = Modifier.widthIn(max = ShellPlayerMaxWidth)
                                )
                            }
                        }
                        if (isTopLevelDestination && navigationChrome == NavigationChrome.BOTTOM_BAR) {
                            Spacer(modifier = androidx.compose.ui.Modifier.height(4.dp))
                            VoxLeafBottomBar(navController = navController, currentRoute = currentRoute)
                        }
                    }
                }
            ) { innerPadding ->
                VoxLeafNavGraph(
                    ttsManager = ttsManager,
                    navController = navController,
                    modifier = Modifier.padding(innerPadding),
                    incomingDocumentUris = sharedUris,
                    onIncomingDocumentsConsumed = onIncomingDocumentsConsumed
                )
            }
        }
    }
}

internal fun NavHostController.navigateToTab(screen: Screen) = navigate(screen) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun VoxLeafNavRail(navController: NavHostController, currentRoute: String?) {
    val showLabels = LocalDensity.current.fontScale < 1.8f
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxHeight().testTag("navigation_rail")
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        navTabs().forEach { tab ->
            val selected = routeBelongsToTab(currentRoute, tab.screen)
            val label = stringResource(tab.labelRes)
            NavigationRailItem(
                selected = selected,
                onClick = { navController.navigateToTab(tab.screen) },
                icon = {
                    Icon(
                        tab.icon,
                        contentDescription = if (showLabels) null else label
                    )
                },
                label = if (showLabels) {
                    {
                        Text(
                            label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    null
                },
                alwaysShowLabel = showLabels,
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
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

internal fun routeBelongsToTab(currentRoute: String?, screen: Screen): Boolean = when (screen) {
    Screen.Library -> currentRoute?.contains(Screen.Library::class.qualifiedName!!) == true ||
        currentRoute?.contains(Screen.BookDetails::class.qualifiedName!!) == true ||
        currentRoute?.contains(Screen.Import::class.qualifiedName!!) == true
    Screen.Settings -> currentRoute?.contains(Screen.Settings::class.qualifiedName!!) == true ||
        currentRoute?.contains(Screen.Stats::class.qualifiedName!!) == true ||
        currentRoute?.contains(Screen.About::class.qualifiedName!!) == true ||
        currentRoute?.contains(Screen.VoiceSelection::class.qualifiedName!!) == true
    else -> currentRoute?.contains(screen::class.qualifiedName!!) == true
}

@Composable
private fun VoxLeafBottomBar(navController: NavHostController, currentRoute: String?) {
    val tabs = navTabs()

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.96f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bottom_navigation")
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute?.contains(tab.screen::class.qualifiedName!!) == true
                val activeColor = MaterialTheme.colorScheme.primary
                val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) AzureContainer.copy(alpha = 0.85f) else Color.Transparent)
                        .border(
                            width = if (selected) 1.dp else 0.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.Transparent,
                            shape = RoundedCornerShape(16.dp)
                        )
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
                        .semantics(mergeDescendants = true) {
                            role = Role.Tab
                            this.selected = selected
                        }
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = null,
                        tint = if (selected) activeColor else inactiveColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(tab.labelRes),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                        color = if (selected) MaterialTheme.colorScheme.primary else inactiveColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
        route.contains(Screen.Stats::class.qualifiedName!!) -> stringResource(R.string.title_stats)
        route.contains(Screen.Import::class.qualifiedName!!) -> stringResource(R.string.title_import)
        route.contains(Screen.BookDetails::class.qualifiedName!!) -> stringResource(R.string.title_book_details)
        route.contains(Screen.Reader::class.qualifiedName!!) -> stringResource(R.string.title_reader)
        route.contains(Screen.VoiceSelection::class.qualifiedName!!) -> stringResource(R.string.title_voice_selection)
        route.contains(Screen.About::class.qualifiedName!!) -> stringResource(R.string.title_about)
        else -> stringResource(R.string.app_name)
    }
}
