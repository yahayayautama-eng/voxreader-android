package com.example.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.entity.AudioCueEntity
import com.example.data.local.entity.AudiobookGenerationEntity
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.ChapterAudioEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AudiobookDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDb() = db.close()

    @Test
    fun generationChapterAudioAndCues_roundTrip() = runTest {
        db.bookDao().insertBook(BookEntity("book", "Title", "Author", "", "", "#000000", 1, false))
        db.audiobookDao().upsertGeneration(
            AudiobookGenerationEntity(
                bookId = "book",
                status = "CONVERTING",
                totalChapters = 1,
                voiceId = "voices/kitten/en-US-bella.bin",
                modelVersion = "test"
            )
        )
        db.audiobookDao().upsertChapterAudio(
            ChapterAudioEntity("book", 0, "READY", "/files/chapter-000.m4a", 1200, 42, "sha", 2)
        )
        db.audiobookDao().insertCues(
            listOf(AudioCueEntity("book", 0, 0, 0, 600), AudioCueEntity("book", 0, 1, 600, 1200))
        )

        assertEquals("CONVERTING", db.audiobookDao().getGeneration("book")?.status)
        assertEquals(1, db.audiobookDao().getChapterAudio("book").size)
        assertEquals(2, db.audiobookDao().getCues("book", 0).size)
        assertNotNull(db.audiobookDao().observeGeneration("book").first())
    }
}
