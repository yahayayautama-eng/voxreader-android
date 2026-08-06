package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.data.local.dao.BookDao
import com.example.data.local.dao.BookmarkDao
import com.example.data.local.dao.VoiceModelDao
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.BookmarkEntity
import com.example.data.local.entity.ReadingProgressEntity
import com.example.data.local.entity.SectionEntity
import com.example.data.local.entity.TextChunkEntity
import com.example.data.local.entity.VoiceModelEntity

@Database(
    entities = [
        BookEntity::class,
        SectionEntity::class,
        TextChunkEntity::class,
        ReadingProgressEntity::class,
        BookmarkEntity::class,
        VoiceModelEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun voiceModelDao(): VoiceModelDao
}
