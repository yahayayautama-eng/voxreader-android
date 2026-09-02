package com.voxleaf.reader.ui.theme

import com.voxleaf.reader.feature.reader.ReaderFontFamily
import com.voxleaf.reader.feature.reader.fontFamilyForReader
import org.junit.Assert.assertEquals
import org.junit.Test

class TypographyTest {

    @Test
    fun materialUiRoles_useInter() {
        assertEquals(UiSans, Typography.bodyLarge.fontFamily)
        assertEquals(UiSans, Typography.titleMedium.fontFamily)
        assertEquals(UiSans, Typography.labelLarge.fontFamily)
    }

    @Test
    fun readerChoices_useBundledFamilies() {
        assertEquals(ReaderSerif, fontFamilyForReader(ReaderFontFamily.SERIF))
        assertEquals(UiSans, fontFamilyForReader(ReaderFontFamily.SANS_SERIF))
        assertEquals(UtilityMono, fontFamilyForReader(ReaderFontFamily.MONOSPACE))
    }
}
