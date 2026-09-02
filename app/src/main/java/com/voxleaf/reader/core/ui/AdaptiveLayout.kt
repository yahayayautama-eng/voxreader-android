package com.voxleaf.reader.core.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal enum class VoxWindowWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

internal enum class NavigationChrome {
    BOTTOM_BAR,
    RAIL
}

internal fun windowWidthClassFor(width: Dp): VoxWindowWidthClass = when {
    width < 600.dp -> VoxWindowWidthClass.COMPACT
    width < 840.dp -> VoxWindowWidthClass.MEDIUM
    else -> VoxWindowWidthClass.EXPANDED
}

internal fun navigationChromeFor(width: Dp): NavigationChrome =
    if (windowWidthClassFor(width) == VoxWindowWidthClass.COMPACT) {
        NavigationChrome.BOTTOM_BAR
    } else {
        NavigationChrome.RAIL
    }

internal fun libraryColumnCount(availableWidth: Dp): Int = when {
    availableWidth < 600.dp -> 3
    availableWidth < 840.dp -> 5
    else -> 7
}

internal val LibraryMaxContentWidth = 1200.dp
internal val LibraryHeaderMaxWidth = 720.dp
internal val BookDetailsMaxContentWidth = 720.dp
internal val ShellPlayerMaxWidth = 840.dp
