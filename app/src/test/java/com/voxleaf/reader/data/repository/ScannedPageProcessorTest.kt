package com.voxleaf.reader.data.repository

import android.graphics.Bitmap
import com.voxleaf.reader.domain.usecase.ScannedPageReference
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScannedPageProcessorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `processes one page at a time in order and recycles full size bitmaps`() = runTest {
        val root = temporaryFolder.newFolder("scan-root")
        val session = File(root, "session").apply { mkdirs() }
        val files = listOf("one", "two", "three").map { name ->
            File(session, "$name.jpg").apply { writeText(name) }
        }
        val decoded = mutableListOf<Bitmap>()
        val processor = ScannedPageProcessor(
            scanRoot = root,
            decode = { file ->
                assertTrue(decoded.lastOrNull()?.isRecycled != false)
                Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888).also(decoded::add)
            },
            recognize = { bitmap ->
                assertTrue(bitmap === decoded.last())
                files[decoded.lastIndex].readText()
            }
        )
        val progress = mutableListOf<Int>()

        val result = processor.process(
            files.map { ScannedPageReference(it.absolutePath, it.length()) }
        ) { completed, _ -> progress += completed }

        result as ScannedPageProcessingResult.Success
        assertEquals(listOf("one", "two", "three"), result.pages.map(ProcessedScannedPage::text))
        assertEquals(listOf(1, 2, 3), progress)
        assertTrue(decoded.all(Bitmap::isRecycled))
        assertTrue(!result.coverBitmap.isRecycled)
        result.coverBitmap.recycle()
    }

    @Test
    fun `independently rejects a supplied path outside scan root`() {
        val root = temporaryFolder.newFolder("contained-root")
        val outside = temporaryFolder.newFile("outside.jpg").apply { writeText("outside") }
        val processor = ScannedPageProcessor(
            scanRoot = root,
            decode = { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) },
            recognize = { "text" }
        )

        val resolved = processor.resolveContainedPage(
            ScannedPageReference(outside.absolutePath, outside.length())
        )

        assertNull(resolved)
    }

    @Test
    fun `persisted reviewed text bypasses OCR and is used during sequential processing`() = runTest {
        val root = temporaryFolder.newFolder("review-root")
        val session = File(root, "session").apply { mkdirs() }
        val image = File(session, "page.jpg").apply { writeText("image") }
        val review = File(session, "page.ocr.txt").apply { writeText("corrected review") }
        var recognitions = 0
        val processor = ScannedPageProcessor(
            scanRoot = root,
            decode = { Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888) },
            recognize = { recognitions++; "raw OCR" }
        )
        val reference = ScannedPageReference(
            image.absolutePath,
            image.length(),
            review.absolutePath,
            review.length()
        )

        val recognition = processor.recognizePage(reference)
        val processing = processor.process(listOf(reference)) { _, _ -> }

        assertEquals(ScannedPageRecognitionResult.Success("corrected review"), recognition)
        processing as ScannedPageProcessingResult.Success
        assertEquals("corrected review", processing.pages.single().text)
        assertEquals(0, recognitions)
        processing.coverBitmap.recycle()
    }
}
