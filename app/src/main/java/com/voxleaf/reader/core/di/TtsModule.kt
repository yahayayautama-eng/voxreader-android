package com.voxleaf.reader.core.di

import com.voxleaf.reader.tts.EdgeTtsEngine
import com.voxleaf.reader.tts.OfflineEngine
import com.voxleaf.reader.tts.OnlineEngine
import com.voxleaf.reader.tts.SherpaTtsEngine
import com.voxleaf.reader.tts.TtsEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TtsModule {
    @Binds
    @OfflineEngine
    abstract fun bindOfflineEngine(engine: SherpaTtsEngine): TtsEngine

    @Binds
    @OnlineEngine
    abstract fun bindOnlineEngine(engine: EdgeTtsEngine): TtsEngine
}
