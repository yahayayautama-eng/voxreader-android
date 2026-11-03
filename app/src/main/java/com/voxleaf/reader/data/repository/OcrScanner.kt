package com.voxleaf.reader.data.repository

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject

class OcrScanner @Inject constructor() {
    // The Chinese model reads Latin as well, but the Latin one is more accurate and faster for
    // everyone else, so the choice follows the device language.
    private val recognizer = if (Locale.getDefault().language == Locale.CHINESE.language) {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    } else {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognize(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result -> continuation.resume(result.text) }
            .addOnFailureListener { error -> continuation.resumeWithException(error) }
    }
}
