package com.voxleaf.reader.data.local.entity

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
    val audioPositionMs: Long = 0L,
    val lastUpdatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "audiobook_generations",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AudiobookGenerationEntity(
    @PrimaryKey val bookId: String,
    val status: String,
    val completedChapters: Int = 0,
    val totalChapters: Int = 0,
    val progressPercent: Int = 0,
    val voiceId: String,
    val modelVersion: String,
    val generationSpeed: Float = 1f,
    val estimatedBytes: Long = 0L,
    val generatedBytes: Long = 0L,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chapter_audio",
    primaryKeys = ["bookId", "chapterIndex"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId"), Index("status")]
)
data class ChapterAudioEntity(
    val bookId: String,
    val chapterIndex: Int,
    val status: String,
    val filePath: String? = null,
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val checksum: String? = null,
    val segmentCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "audio_cues",
    primaryKeys = ["bookId", "chapterIndex", "sentenceIndex"],
    foreignKeys = [
        ForeignKey(
            entity = ChapterAudioEntity::class,
            parentColumns = ["bookId", "chapterIndex"],
            childColumns = ["bookId", "chapterIndex"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId", "chapterIndex")]
)
data class AudioCueEntity(
    val bookId: String,
    val chapterIndex: Int,
    val sentenceIndex: Int,
    val startMs: Long,
    val endMs: Long
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
