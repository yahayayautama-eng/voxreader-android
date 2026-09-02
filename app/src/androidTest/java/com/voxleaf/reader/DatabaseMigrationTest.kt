package com.voxleaf.reader

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.voxleaf.reader.core.di.DatabaseModule
import com.voxleaf.reader.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate6To7_preservesBookDataAndAddsAudiobookTables() {
        helper.createDatabase("migration-v6", 6).apply {
            execSQL("INSERT INTO books (id, title, author, description, genre, coverColorHex, totalChapters, isFavorite, sourceFilePath, coverImagePath) VALUES ('book', 'Title', 'Author', '', 'Fiction', '#000000', 1, 0, NULL, NULL)")
            execSQL("INSERT INTO reading_progress (bookId, currentChapterIndex, currentPosition, lastUpdatedAt) VALUES ('book', 0, 2, 10)")
            execSQL("INSERT INTO highlights (id, bookId, chapterIndex, sentenceIndex, chapterTitle, text, colorIndex, note, timestamp) VALUES ('h1', 'book', 0, 0, 'Chapter 1', 'text', 0, 'note', 10)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate("migration-v6", 7, true, DatabaseModule.MIGRATION_6_7)
        migrated.query("SELECT title FROM books WHERE id = 'book'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Title", cursor.getString(0))
        }
        migrated.query("SELECT audioPositionMs FROM reading_progress WHERE bookId = 'book'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0L, cursor.getInt(0).toLong())
        }
        migrated.query("SELECT COUNT(*) FROM audiobook_generations").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }

    /**
     * The batch renderer is gone, so its tables must be dropped — and a reader's books, progress and
     * highlights must survive that, since those are the only things in here anyone would miss.
     */
    @Test
    fun migrate7To8_dropsAudiobookTablesAndKeepsLibrary() {
        helper.createDatabase("migration-v7", 7).apply {
            execSQL("INSERT INTO books (id, title, author, description, genre, coverColorHex, totalChapters, isFavorite, sourceFilePath, coverImagePath) VALUES ('book', 'Title', 'Author', '', 'Fiction', '#000000', 1, 0, NULL, NULL)")
            execSQL("INSERT INTO reading_progress (bookId, currentChapterIndex, currentPosition, lastUpdatedAt, audioPositionMs) VALUES ('book', 3, 7, 10, 0)")
            execSQL("INSERT INTO highlights (id, bookId, chapterIndex, sentenceIndex, chapterTitle, text, colorIndex, note, timestamp) VALUES ('h1', 'book', 0, 0, 'Chapter 1', 'text', 0, 'note', 10)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate("migration-v7", 8, true, DatabaseModule.MIGRATION_7_8)
        migrated.query("SELECT currentChapterIndex FROM reading_progress WHERE bookId = 'book'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(3, cursor.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM highlights").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        migrated.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('audiobook_generations', 'chapter_audio', 'audio_cues')").use { cursor ->
            assertEquals(0, cursor.count)
        }
        migrated.close()
    }

    @Test
    fun migrate8To9_addsStructureMetadataWithoutChangingSections() {
        helper.createDatabase("migration-v8", 8).apply {
            execSQL("INSERT INTO books (id, title, author, description, genre, coverColorHex, totalChapters, isFavorite, sourceFilePath, coverImagePath) VALUES ('book', 'Title', 'Author', '', 'Fiction', '#000000', 1, 0, NULL, NULL)")
            execSQL("INSERT INTO sections (id, bookId, chapterNumber, title, estimatedMinutes) VALUES ('section', 'book', 1, 'Chapter One', 3)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate("migration-v8", 9, true, DatabaseModule.MIGRATION_8_9)
        migrated.query("SELECT title, detectionSource, detectionConfidence, detectionReason, startAnchor, endAnchor, isManuallyEdited FROM sections WHERE id = 'section'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Chapter One", cursor.getString(0))
            assertEquals("LEGACY", cursor.getString(1))
            assertEquals(0.5f, cursor.getFloat(2), 0.001f)
            assertTrue(cursor.getString(3).isNotBlank())
            assertEquals("", cursor.getString(4))
            assertEquals("", cursor.getString(5))
            assertEquals(0, cursor.getInt(6))
        }
        migrated.close()
    }
}
