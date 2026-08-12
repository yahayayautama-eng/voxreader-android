package com.example.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.AppDatabase
import com.example.data.local.dao.BookDao
import com.example.data.local.dao.BookmarkDao
import com.example.data.local.dao.HighlightDao
import com.example.data.local.dao.ListeningDao
import com.example.data.local.dao.AudiobookDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private val migration1To2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE reading_progress ADD COLUMN lastUpdatedAt INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    private val migration2To3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE bookmarks ADD COLUMN sentenceIndex INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    private val migration3To4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DROP TABLE IF EXISTS voice_models")
        }
    }

    private val migration4To5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE books ADD COLUMN coverImagePath TEXT")
        }
    }

    private val migration5To6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS highlights (
                    id TEXT NOT NULL PRIMARY KEY,
                    bookId TEXT NOT NULL,
                    chapterIndex INTEGER NOT NULL,
                    sentenceIndex INTEGER NOT NULL,
                    chapterTitle TEXT NOT NULL,
                    text TEXT NOT NULL,
                    colorIndex INTEGER NOT NULL,
                    note TEXT,
                    timestamp INTEGER NOT NULL,
                    FOREIGN KEY(bookId) REFERENCES books(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS index_highlights_bookId ON highlights(bookId)")
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS listening_days (
                    date TEXT NOT NULL,
                    bookId TEXT NOT NULL,
                    seconds INTEGER NOT NULL,
                    PRIMARY KEY(date, bookId)
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE reading_progress ADD COLUMN audioPositionMs INTEGER NOT NULL DEFAULT 0"
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS audiobook_generations (
                    bookId TEXT NOT NULL PRIMARY KEY,
                    status TEXT NOT NULL,
                    completedChapters INTEGER NOT NULL DEFAULT 0,
                    totalChapters INTEGER NOT NULL DEFAULT 0,
                    progressPercent INTEGER NOT NULL DEFAULT 0,
                    voiceId TEXT NOT NULL,
                    modelVersion TEXT NOT NULL,
                    generationSpeed REAL NOT NULL DEFAULT 1.0,
                    estimatedBytes INTEGER NOT NULL DEFAULT 0,
                    generatedBytes INTEGER NOT NULL DEFAULT 0,
                    errorCode TEXT,
                    errorMessage TEXT,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    FOREIGN KEY(bookId) REFERENCES books(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS chapter_audio (
                    bookId TEXT NOT NULL,
                    chapterIndex INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    filePath TEXT,
                    durationMs INTEGER NOT NULL DEFAULT 0,
                    fileSizeBytes INTEGER NOT NULL DEFAULT 0,
                    checksum TEXT,
                    segmentCount INTEGER NOT NULL DEFAULT 0,
                    updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(bookId, chapterIndex),
                    FOREIGN KEY(bookId) REFERENCES books(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS index_chapter_audio_bookId ON chapter_audio(bookId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_chapter_audio_status ON chapter_audio(status)")
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS audio_cues (
                    bookId TEXT NOT NULL,
                    chapterIndex INTEGER NOT NULL,
                    sentenceIndex INTEGER NOT NULL,
                    startMs INTEGER NOT NULL,
                    endMs INTEGER NOT NULL,
                    PRIMARY KEY(bookId, chapterIndex, sentenceIndex),
                    FOREIGN KEY(bookId, chapterIndex) REFERENCES chapter_audio(bookId, chapterIndex) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS index_audio_cues_bookId_chapterIndex ON audio_cues(bookId, chapterIndex)")
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "voxleaf_db"
        )
        .addMigrations(migration1To2, migration2To3, migration3To4, migration4To5, migration5To6, MIGRATION_6_7)
        .build()
    }

    @Provides
    fun provideBookDao(database: AppDatabase): BookDao = database.bookDao()

    @Provides
    fun provideBookmarkDao(database: AppDatabase): BookmarkDao = database.bookmarkDao()

    @Provides
    fun provideHighlightDao(database: AppDatabase): HighlightDao = database.highlightDao()

    @Provides
    fun provideListeningDao(database: AppDatabase): ListeningDao = database.listeningDao()

    @Provides
    fun provideAudiobookDao(database: AppDatabase): AudiobookDao = database.audiobookDao()
}
