package com.voxleaf.reader.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.voxleaf.reader.domain.usecase.ScannedPageReference
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

internal data class ProcessedScannedPage(val pageNumber: Int, val text: String)

internal sealed interface ScannedPageRecognitionResult {
    data class Success(val text: String) : ScannedPageRecognitionResult
    data class Error(val reason: ScannedPageProcessingResult.Reason) : ScannedPageRecognitionResult
}

internal sealed interface ScannedPageProcessingResult {
    data class Success(
        val pages: List<ProcessedScannedPage>,
        val coverBitmap: Bitmap
    ) : ScannedPageProcessingResult

    data class Error(val reason: Reason, val pageNumber: Int? = null) : ScannedPageProcessingResult

    enum class Reason {
        NO_PAGES,
        INVALID_PATH,
        EMPTY_IMAGE,
        CORRUPT_IMAGE,
        OCR_FAILED
    }
}

class ScannedPageProcessor private constructor(
    private val scanRoot: File,
    private val decode: (File) -> Bitmap?,
    private val recognize: suspend (Bitmap) -> String,
    private val createCover: (Bitmap) -> Bitmap
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        ocrScanner: OcrScanner
    ) : this(
        scanRoot = File(context.cacheDir, SCAN_ROOT_PATH),
        decode = ::decodeOcrBitmap,
        recognize = ocrScanner::recognize,
        createCover = ::copyCoverBitmap
    )

    internal constructor(
        scanRoot: File,
        decode: (File) -> Bitmap?,
        recognize: suspend (Bitmap) -> String
    ) : this(scanRoot, decode, recognize, ::copyCoverBitmap)

    internal suspend fun process(
        references: List<ScannedPageReference>,
        onProgress: suspend (completed: Int, total: Int) -> Unit
    ): ScannedPageProcessingResult {
        if (references.isEmpty()) return ScannedPageProcessingResult.Error(ScannedPageProcessingResult.Reason.NO_PAGES)

        val processed = ArrayList<ProcessedScannedPage>(references.size)
        var cover: Bitmap? = null
        references.forEachIndexed { index, reference ->
            val pageNumber = index + 1
            val file = resolveContainedPage(reference)
                ?: return failureWithCover(cover, ScannedPageProcessingResult.Reason.INVALID_PATH, pageNumber)
            if (file.length() == 0L) {
                return failureWithCover(cover, ScannedPageProcessingResult.Reason.EMPTY_IMAGE, pageNumber)
            }
            val reviewedText = readReviewedText(reference)
            val bitmap = decode(file)
                ?: return failureWithCover(cover, ScannedPageProcessingResult.Reason.CORRUPT_IMAGE, pageNumber)
            try {
                if (cover == null) cover = createCover(bitmap)
                val text = reviewedText ?: try {
                    recognize(bitmap)
                } catch (exception: CancellationException) {
                    cover?.recycle()
                    throw exception
                } catch (_: Exception) {
                    return failureWithCover(cover, ScannedPageProcessingResult.Reason.OCR_FAILED, pageNumber)
                }
                processed += ProcessedScannedPage(pageNumber, text.trim())
            } finally {
                bitmap.recycle()
            }
            onProgress(pageNumber, references.size)
        }

        return ScannedPageProcessingResult.Success(processed, checkNotNull(cover))
    }

    internal suspend fun recognizePage(reference: ScannedPageReference): ScannedPageRecognitionResult {
        val existing = readReviewedText(reference)
        if (existing != null) return ScannedPageRecognitionResult.Success(existing)
        val file = resolveContainedPage(reference)
            ?: return ScannedPageRecognitionResult.Error(ScannedPageProcessingResult.Reason.INVALID_PATH)
        if (file.length() == 0L) {
            return ScannedPageRecognitionResult.Error(ScannedPageProcessingResult.Reason.EMPTY_IMAGE)
        }
        val bitmap = decode(file)
            ?: return ScannedPageRecognitionResult.Error(ScannedPageProcessingResult.Reason.CORRUPT_IMAGE)
        return try {
            ScannedPageRecognitionResult.Success(recognize(bitmap).trim())
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            ScannedPageRecognitionResult.Error(ScannedPageProcessingResult.Reason.OCR_FAILED)
        } finally {
            bitmap.recycle()
        }
    }

    internal fun resolveContainedPage(reference: ScannedPageReference): File? {
        val root = runCatching { scanRoot.canonicalFile }.getOrNull() ?: return null
        val candidate = runCatching { File(reference.absolutePath).canonicalFile }.getOrNull() ?: return null
        return candidate.takeIf {
            it.isFile && it.length() == reference.byteSize && it.toPath().startsWith(root.toPath())
        }
    }

    private fun readReviewedText(reference: ScannedPageReference): String? {
        val path = reference.reviewedTextPath ?: return null
        if (reference.reviewedTextByteSize !in 0..MAX_REVIEW_TEXT_BYTES) return null
        val root = runCatching { scanRoot.canonicalFile }.getOrNull() ?: return null
        val candidate = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        if (
            !candidate.isFile ||
            candidate.length() != reference.reviewedTextByteSize ||
            !candidate.toPath().startsWith(root.toPath())
        ) return null
        return runCatching { candidate.readText() }.getOrNull()
    }

    private fun failureWithCover(
        cover: Bitmap?,
        reason: ScannedPageProcessingResult.Reason,
        pageNumber: Int
    ): ScannedPageProcessingResult.Error {
        cover?.recycle()
        return ScannedPageProcessingResult.Error(reason, pageNumber)
    }

    companion object {
        internal const val MAX_OCR_DIMENSION = 2400
        internal const val COVER_DIMENSION = 480
        internal const val MAX_REVIEW_TEXT_BYTES = 4L * 1024L * 1024L
        private const val SCAN_ROOT_PATH = "scans/sessions"

        private fun decodeOcrBitmap(file: File): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sampleSize = 1
            while (
                bounds.outWidth / sampleSize > MAX_OCR_DIMENSION ||
                bounds.outHeight / sampleSize > MAX_OCR_DIMENSION
            ) {
                sampleSize *= 2
            }
            return BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sampleSize }
            )
        }

        private fun copyCoverBitmap(source: Bitmap): Bitmap {
            val scale = COVER_DIMENSION.toFloat() / maxOf(source.width, source.height)
            val width = if (scale < 1f) (source.width * scale).toInt().coerceAtLeast(1) else source.width
            val height = if (scale < 1f) (source.height * scale).toInt().coerceAtLeast(1) else source.height
            val copy = source.copy(Bitmap.Config.ARGB_8888, false)
            if (copy.width == width && copy.height == height) return copy
            return Bitmap.createScaledBitmap(copy, width, height, true).also { copy.recycle() }
        }
    }
}
