package com.voxleaf.reader.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.voxleaf.reader.data.local.dao.BookDao
import com.voxleaf.reader.data.local.entity.BookEntity
import com.voxleaf.reader.data.local.entity.ReadingProgressEntity
import com.voxleaf.reader.data.local.entity.SectionEntity
import com.voxleaf.reader.data.local.entity.TextChunkEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class BookDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var bookDao: BookDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        bookDao = db.bookDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndReadBookWithDetails() = runTest {
        val book = BookEntity("1", "Title", "Author", "Desc", "Genre", "#000000", 1, false)
        val progress = ReadingProgressEntity("1", 0, 0)
        val section = SectionEntity("s1", "1", 1, "Chapter 1", 5)
        val chunk = TextChunkEntity("c1", "s1", 0, "Hello World")

        bookDao.insertBook(book)
        bookDao.insertReadingProgress(progress)
        bookDao.insertSections(listOf(section))
        bookDao.insertTextChunks(listOf(chunk))

        val loaded = bookDao.getBookById("1")
        assertNotNull(loaded)
        assertEquals("Title", loaded?.book?.title)
        assertEquals(0, loaded?.progress?.currentChapterIndex)
        assertEquals(1, loaded?.sections?.size)
        assertEquals("Chapter 1", loaded?.sections?.get(0)?.section?.title)
        assertEquals(1, loaded?.sections?.get(0)?.chunks?.size)
        assertEquals("Hello World", loaded?.sections?.get(0)?.chunks?.get(0)?.text)
    }

    @Test
    fun toggleFavorite() = runTest {
        val book = BookEntity("1", "Title", "Author", "Desc", "Genre", "#000000", 1, false)
        bookDao.insertBook(book)
        bookDao.toggleFavorite("1")
        val loaded = bookDao.getBookById("1")
        assertEquals(true, loaded?.book?.isFavorite)
        bookDao.toggleFavorite("1")
        val loaded2 = bookDao.getBookById("1")
        assertEquals(false, loaded2?.book?.isFavorite)
    }

    @Test
    fun cascadeDeleteBook_deletesSectionsAndChunks() = runTest {
        val book = BookEntity("2", "Title", "Author", "Desc", "Genre", "#000000", 1, false)
        val section = SectionEntity("s2", "2", 1, "Chapter", 5)
        val chunk = TextChunkEntity("c2", "s2", 0, "Hello")

        bookDao.insertBook(book)
        bookDao.insertSections(listOf(section))
        bookDao.insertTextChunks(listOf(chunk))

        val loaded = bookDao.getBookById("2")
        assertEquals(1, loaded?.sections?.size)
        
        bookDao.deleteBook("2")
        val afterDelete = bookDao.getBookById("2")
        assertNull(afterDelete)
        
        // Since we are doing a unit test, we should directly verify DB state using raw queries
        val cursor = db.query("SELECT * FROM text_chunks WHERE sectionId = 's2'", null)
        assertEquals(0, cursor.count)
        cursor.close()
    }
}
