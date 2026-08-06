package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val description: String,
    val genre: String,
    val coverColorHex: String,
    val totalChapters: Int,
    val isFavorite: Boolean,
    val sourceFilePath: String? = null
)

@Entity(
    tableName = "sections",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId")]
)
data class SectionEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val chapterNumber: Int,
    val title: String,
    val estimatedMinutes: Int
)

@Entity(
    tableName = "text_chunks",
    foreignKeys = [
        ForeignKey(
            entity = SectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sectionId")]
)
data class TextChunkEntity(
    @PrimaryKey val id: String,
    val sectionId: String,
    val sequenceNumber: Int,
    val text: String,
    val audioFilePath: String? = null
)

@Entity(
    tableName = "reading_progress",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId")]
)
data class ReadingProgressEntity(
    @PrimaryKey val bookId: String,
    val currentChapterIndex: Int,
    val currentPosition: Int,
    val lastUpdatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId")]
)
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val textSnippet: String,
    val timestamp: Long,
    val note: String?
)

@Entity(tableName = "voice_models")
data class VoiceModelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val language: String,
    val filePath: String?
)
