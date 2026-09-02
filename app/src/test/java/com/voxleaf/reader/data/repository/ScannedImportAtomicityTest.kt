package com.voxleaf.reader.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.voxleaf.reader.R
import com.voxleaf.reader.data.local.AppDatabase
import com.voxleaf.reader.domain.usecase.ImportState
import com.voxleaf.reader.domain.usecase.ScannedPageReference
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScannedImportAtomicityTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `unreadable OCR result performs no repository writes and releases cover`() = runTest {
        val context = mockk<Context>()
        val database = mockk<AppDatabase>(relaxed = true)
        val processor = mockk<ScannedPageProcessor>()
        val cover = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888)
        coEvery { processor.process(any(), any()) } returns ScannedPageProcessingResult.Success(
            pages = listOf(ProcessedScannedPage(1, "")),
            coverBitmap = cover
        )
        every { context.getString(R.string.scan_error_no_readable_text) } returns "No readable text"
        val importer = TextBookImporterImpl(context, database, mockk(), processor)
        val file = temporaryFolder.newFile("page.jpg").apply { writeText("image") }

        val states = importer(
            listOf(ScannedPageReference(file.absolutePath, file.length())),
            "Scan"
        ).toList()

        assertEquals(ImportState.Error("No readable text"), states.last())
        verify(exactly = 0) { database.bookDao() }
        assertEquals(true, cover.isRecycled)
    }
}
