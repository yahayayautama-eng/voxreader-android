package com.voxleaf.reader.core.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Thumbnails only ever render at a few dozen dp, so one downsampled size covers every list/grid row. */
private const val TARGET_COVER_PX = 240

private val coverCache = LruCache<String, ImageBitmap>(80)

@Composable
fun rememberBookCoverBitmap(path: String?): ImageBitmap? {
    val state = produceState<ImageBitmap?>(initialValue = path?.let { coverCache.get(it) }, path) {
        value = if (path == null) {
            null
        } else {
            coverCache.get(path) ?: withContext(Dispatchers.IO) {
                try {
                    decodeSampledBitmap(path, TARGET_COVER_PX)?.asImageBitmap()?.also {
                        coverCache.put(path, it)
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
    return state.value
}

private fun decodeSampledBitmap(path: String, targetPx: Int) =
    BitmapFactory.Options().run {
        inJustDecodeBounds = true
        BitmapFactory.decodeFile(path, this)
        inSampleSize = calculateInSampleSize(outWidth, outHeight, targetPx)
        inJustDecodeBounds = false
        BitmapFactory.decodeFile(path, this)
    }

private fun calculateInSampleSize(width: Int, height: Int, targetPx: Int): Int {
    var sampleSize = 1
    while (width / (sampleSize * 2) >= targetPx && height / (sampleSize * 2) >= targetPx) {
        sampleSize *= 2
    }
    return sampleSize
}
