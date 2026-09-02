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

/**
 * Columns are derived from a target cover width rather than from fixed counts per breakpoint, so a
 * cover occupies roughly the same physical size on a phone and on a tablet instead of shrinking as
 * the window grows.
 *
 * Deliberately not capped by how many books there are: capping columns to the shelf size makes a
 * two-book library render two covers the width of the screen, which is worse than the empty space
 * it was meant to fix. A small shelf simply occupies the first row.
 */
internal fun libraryColumnCount(availableWidth: Dp): Int {
    val fits = (availableWidth.value / TargetCoverWidth.value).toInt()
    return fits.coerceIn(MinLibraryColumns, MaxLibraryColumns)
}

// 132dp keeps a phone at three covers per row and a tablet around eight, so the shelf reads as a
// shelf on both rather than as a few oversized tiles.
private val TargetCoverWidth = 132.dp
internal const val MinLibraryColumns = 2
internal const val MaxLibraryColumns = 8

/**
 * One measure for the whole page. The header used to be capped narrower than the grid, which left a
 * visible step between the search field and the books beneath it.
 */
internal val LibraryMaxContentWidth = 1400.dp
internal val BookDetailsMaxContentWidth = 720.dp
internal val ShellPlayerMaxWidth = 840.dp

/** Below this the reader stacks; at or above it chapters sit beside the text. */
internal val ReaderTwoPaneMinWidth = 840.dp
internal val ReaderChapterPaneWidth = 320.dp
internal val ReaderTextMaxWidth = 720.dp
