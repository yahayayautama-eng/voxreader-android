package com.voxleaf.reader.tts

import javax.inject.Qualifier

/**
 * Distinguishes the two [TtsEngine] implementations at injection sites.
 *
 * [TtsManager] only ever calls them through the interface, so it depends on [TtsEngine] rather than
 * on the concrete classes; that keeps the manager's behaviour testable with fake engines instead of
 * requiring a real model file or a network round trip.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OfflineEngine

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OnlineEngine
