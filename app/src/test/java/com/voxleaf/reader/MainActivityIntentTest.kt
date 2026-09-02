package com.voxleaf.reader

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityIntentTest {
    @Test
    fun `send view and send multiple expose only distinct document uris`() {
        val one = Uri.parse("content://documents/one.pdf")
        val two = Uri.parse("content://documents/two.epub")
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        val multiple = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(one, two, one))
            clipData = ClipData.newUri(resolver, "shared", two)
        }

        assertEquals(listOf(one), extractIncomingDocumentUris(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, one)))
        assertEquals(listOf(two), extractIncomingDocumentUris(Intent(Intent.ACTION_VIEW, two)))
        assertEquals(listOf(two, one), extractIncomingDocumentUris(multiple))
    }

    @Test
    fun `non document actions and text without a stream are ignored`() {
        assertTrue(extractIncomingDocumentUris(Intent(Intent.ACTION_MAIN)).isEmpty())
        assertTrue(
            extractIncomingDocumentUris(
                Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "not silently imported")
            ).isEmpty()
        )
    }
}
