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
    val sourceFilePath: String? = null,
    val coverImagePath: String? = null
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

/**
 * A marked passage. Anchored to a sentence rather than to character offsets: the reader already
 * addresses everything by sentence index, so this survives the same re-parse a bookmark does and
 * needs no second coordinate system.
 */
@Entity(
    tableName = "highlights",
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
data class HighlightEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val sentenceIndex: Int,
    val chapterTitle: String,
    val text: String,
    val colorIndex: Int,
    val note: String?,
    val timestamp: Long
)

/**
 * Seconds listened per book per calendar day. Deliberately has no foreign key to books: deleting a
 * book should not rewrite your reading history, and the stats screen tolerates unknown book ids.
 */
@Entity(tableName = "listening_days", primaryKeys = ["date", "bookId"])
data class ListeningDayEntity(
    /** Local calendar date as ISO yyyy-MM-dd — the unit a heatmap and a streak are both counted in. */
    val date: String,
    val bookId: String,
    val seconds: Int
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
    val sentenceIndex: Int,
    val chapterTitle: String,
    val textSnippet: String,
    val timestamp: Long,
    val note: String?
)
