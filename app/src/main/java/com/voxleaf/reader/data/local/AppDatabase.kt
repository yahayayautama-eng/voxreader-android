package com.voxleaf.reader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.voxleaf.reader.data.local.dao.BookDao
import com.voxleaf.reader.data.local.dao.BookmarkDao
import com.voxleaf.reader.data.local.dao.HighlightDao
import com.voxleaf.reader.data.local.dao.ListeningDao
import com.voxleaf.reader.data.local.entity.BookEntity
import com.voxleaf.reader.data.local.entity.BookmarkEntity
import com.voxleaf.reader.data.local.entity.HighlightEntity
import com.voxleaf.reader.data.local.entity.ListeningDayEntity
import com.voxleaf.reader.data.local.entity.ReadingProgressEntity
import com.voxleaf.reader.data.local.entity.SectionEntity
import com.voxleaf.reader.data.local.entity.TextChunkEntity

@Database(
    entities = [
        BookEntity::class,
        SectionEntity::class,
        TextChunkEntity::class,
        ReadingProgressEntity::class,
        BookmarkEntity::class,
        HighlightEntity::class,
        ListeningDayEntity::class
    ],
    version = 9,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun highlightDao(): HighlightDao
    abstract fun listeningDao(): ListeningDao
}
