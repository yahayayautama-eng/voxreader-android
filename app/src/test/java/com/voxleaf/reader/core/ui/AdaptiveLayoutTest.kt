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

    @Test
    fun `library columns adapt at width boundaries and remain bounded when expanded`() {
        assertEquals(3, libraryColumnCount(599.dp))
        assertEquals(5, libraryColumnCount(600.dp))
        assertEquals(5, libraryColumnCount(839.dp))
        assertEquals(7, libraryColumnCount(840.dp))
        assertEquals(7, libraryColumnCount(LibraryMaxContentWidth))
        assertEquals(7, libraryColumnCount(2000.dp))
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
