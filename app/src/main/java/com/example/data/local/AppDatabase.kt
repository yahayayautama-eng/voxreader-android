package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.data.local.dao.BookDao
import com.example.data.local.dao.BookmarkDao
import com.example.data.local.dao.HighlightDao
import com.example.data.local.dao.ListeningDao
import com.example.data.local.dao.AudiobookDao
import com.example.data.local.entity.AudioCueEntity
import com.example.data.local.entity.AudiobookGenerationEntity
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.BookmarkEntity
import com.example.data.local.entity.ChapterAudioEntity
import com.example.data.local.entity.HighlightEntity
import com.example.data.local.entity.ListeningDayEntity
import com.example.data.local.entity.ReadingProgressEntity
import com.example.data.local.entity.SectionEntity
import com.example.data.local.entity.TextChunkEntity

@Database(
    entities = [
        BookEntity::class,
        SectionEntity::class,
        TextChunkEntity::class,
        ReadingProgressEntity::class,
        BookmarkEntity::class,
        HighlightEntity::class,
        ListeningDayEntity::class,
        AudiobookGenerationEntity::class,
        ChapterAudioEntity::class,
        AudioCueEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun highlightDao(): HighlightDao
    abstract fun listeningDao(): ListeningDao
    abstract fun audiobookDao(): AudiobookDao
}
