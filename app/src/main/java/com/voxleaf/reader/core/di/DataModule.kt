package com.voxleaf.reader.core.di

import com.voxleaf.reader.data.repository.RoomBookRepository
import com.voxleaf.reader.data.repository.TextBookImporterImpl
import com.voxleaf.reader.domain.repository.BookRepository
import com.voxleaf.reader.domain.usecase.ImportScannedBookUseCase
import com.voxleaf.reader.domain.usecase.ImportTextBookUseCase
import com.voxleaf.reader.domain.usecase.RedetectChaptersUseCase
import com.voxleaf.reader.domain.usecase.SectionStructureUseCase
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun bindBookRepository(
        roomBookRepository: RoomBookRepository
    ): BookRepository

    @Binds
    abstract fun bindImportTextBookUseCase(
        importer: TextBookImporterImpl
    ): ImportTextBookUseCase

    @Binds
    abstract fun bindImportScannedBookUseCase(
        importer: TextBookImporterImpl
    ): ImportScannedBookUseCase

    @Binds
    abstract fun bindRedetectChaptersUseCase(
        importer: TextBookImporterImpl
    ): RedetectChaptersUseCase

    @Binds
    abstract fun bindSectionStructureUseCase(
        importer: TextBookImporterImpl
    ): SectionStructureUseCase

}
