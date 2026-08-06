package com.example.core.di

import com.example.data.repository.RoomBookRepository
import com.example.data.repository.TextBookImporterImpl
import com.example.domain.repository.BookRepository
import com.example.domain.usecase.ImportTextBookUseCase
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
}
