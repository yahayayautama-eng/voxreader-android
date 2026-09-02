package com.voxleaf.reader.core.ui

import androidx.compose.ui.unit.dp
import com.voxleaf.reader.core.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutTest {

    @Test
    fun `compact medium and expanded boundaries select the expected navigation chrome`() {
        assertEquals(VoxWindowWidthClass.COMPACT, windowWidthClassFor(599.dp))
        assertEquals(NavigationChrome.BOTTOM_BAR, navigationChromeFor(599.dp))

        assertEquals(VoxWindowWidthClass.MEDIUM, windowWidthClassFor(600.dp))
        assertEquals(NavigationChrome.RAIL, navigationChromeFor(600.dp))
        assertEquals(VoxWindowWidthClass.MEDIUM, windowWidthClassFor(839.dp))

        assertEquals(VoxWindowWidthClass.EXPANDED, windowWidthClassFor(840.dp))
        assertEquals(NavigationChrome.RAIL, navigationChromeFor(840.dp))
    }

    /**
     * Columns follow a target cover width, so a cover stays about the same physical size whether the
     * window is a phone or a tablet, rather than the tiles shrinking as the window grows.
     */
    @Test
    fun `library columns follow the available width`() {
        assertEquals(MinLibraryColumns, libraryColumnCount(260.dp))
        assertEquals(3, libraryColumnCount(411.dp))
        assertEquals(4, libraryColumnCount(599.dp))
        assertEquals(5, libraryColumnCount(720.dp))
        assertEquals(MaxLibraryColumns, libraryColumnCount(LibraryMaxContentWidth))
        assertEquals(MaxLibraryColumns, libraryColumnCount(4000.dp))
    }

    @Test
    fun `supporting destinations keep their owning rail tab selected`() {
        assertTrue(
            routeBelongsToTab(Screen.BookDetails::class.qualifiedName, Screen.Library)
        )
        assertTrue(routeBelongsToTab(Screen.Import::class.qualifiedName, Screen.Library))
        assertTrue(routeBelongsToTab(Screen.Stats::class.qualifiedName, Screen.Settings))
        assertTrue(routeBelongsToTab(Screen.About::class.qualifiedName, Screen.Settings))
        assertFalse(routeBelongsToTab(Screen.BookDetails::class.qualifiedName, Screen.Settings))
    }
}
