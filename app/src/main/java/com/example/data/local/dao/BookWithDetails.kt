package com.example.data.local.dao

import androidx.room.Embedded
import androidx.room.Relation
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.ReadingProgressEntity
import com.example.data.local.entity.SectionEntity
import com.example.data.local.entity.TextChunkEntity

data class BookWithDetails(
    @Embedded val book: BookEntity,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "bookId"
    )
    val progress: ReadingProgressEntity?,

    @Relation(
        entity = SectionEntity::class,
        parentColumn = "id",
        entityColumn = "bookId"
    )
    val sections: List<SectionWithChunks>
)

data class SectionWithChunks(
    @Embedded val section: SectionEntity,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "sectionId"
    )
    val chunks: List<TextChunkEntity>
)

data class SearchResultSnippet(
    val chapterIndex: Int,
    val chapterTitle: String,
    val chunkIndex: Int,
    val snippet: String
)
