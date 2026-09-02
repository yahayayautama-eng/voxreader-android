package com.voxleaf.reader.feature.importbook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.StatFs
import com.voxleaf.reader.domain.usecase.ScannedPageReference
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class ScanSessionSnapshot(
    val id: String,
    val title: String,
    val pages: List<ScannedPageReference>
) {
    val totalBytes: Long = pages.sumOf(ScannedPageReference::byteSize)
}

enum class ScanIssue {
    PAGE_LIMIT,
    BYTE_LIMIT,
    LOW_STORAGE,
    CAMERA_UNAVAILABLE,
    CAMERA_FAILED,
    EMPTY_IMAGE,
    CORRUPT_IMAGE,
    STORAGE_WRITE_FAILED,
    PAGE_NOT_AVAILABLE,
    MOVE_UNAVAILABLE,
    REORDER_FAILED,
    REMOVE_FAILED,
    ROTATE_FAILED,
    ROTATE_BYTE_LIMIT,
    ROTATE_LOW_STORAGE,
    CROP_FAILED,
    CROP_INVALID,
    OCR_REVIEW_TOO_LARGE,
    OCR_REVIEW_SAVE_FAILED,
    RETAKE_CAMERA_FAILED,
    RETAKE_CANCELLED,
    RETAKE_EMPTY_IMAGE,
    RETAKE_CORRUPT_IMAGE,
    RETAKE_BYTE_LIMIT,
    RETAKE_LOW_STORAGE
}

enum class ScanPageMove { EARLIER, LATER }

sealed interface ScanCapturePreparation {
    data class Ready(val file: File) : ScanCapturePreparation
    data class Rejected(val issue: ScanIssue) : ScanCapturePreparation
}

data class ScanCaptureCompletion(
    val snapshot: ScanSessionSnapshot?,
    val issue: ScanIssue? = null
)

data class ScanRestoreResult(
    val snapshot: ScanSessionSnapshot?,
    val issue: ScanIssue? = null
)

data class ScanOperationResult(
    val snapshot: ScanSessionSnapshot?,
    val issue: ScanIssue? = null
)

internal object ScanAdmissionPolicy {
    fun beforeCapture(pageCount: Int, totalBytes: Long, usableBytes: Long): ScanIssue? = when {
        pageCount >= ScanSessionStore.MAX_PAGE_COUNT -> ScanIssue.PAGE_LIMIT
        totalBytes + ScanSessionStore.CAPTURE_RESERVATION_BYTES > ScanSessionStore.MAX_SESSION_BYTES ->
            ScanIssue.BYTE_LIMIT
        usableBytes < ScanSessionStore.MIN_FREE_BYTES + ScanSessionStore.CAPTURE_RESERVATION_BYTES ->
            ScanIssue.LOW_STORAGE
        else -> null
    }

    fun afterCapture(pageCount: Int, totalBytes: Long, usableBytes: Long): ScanIssue? = when {
        pageCount > ScanSessionStore.MAX_PAGE_COUNT -> ScanIssue.PAGE_LIMIT
        totalBytes > ScanSessionStore.MAX_SESSION_BYTES -> ScanIssue.BYTE_LIMIT
        usableBytes < ScanSessionStore.MIN_FREE_BYTES -> ScanIssue.LOW_STORAGE
        else -> null
    }

    fun beforeReplacement(totalBytes: Long, originalBytes: Long, usableBytes: Long): ScanIssue? = when {
        totalBytes - originalBytes + ScanSessionStore.CAPTURE_RESERVATION_BYTES >
            ScanSessionStore.MAX_SESSION_BYTES -> ScanIssue.RETAKE_BYTE_LIMIT
        usableBytes < ScanSessionStore.MIN_FREE_BYTES + ScanSessionStore.CAPTURE_RESERVATION_BYTES ->
            ScanIssue.RETAKE_LOW_STORAGE
        else -> null
    }
}

@Singleton
class ScanSessionStore internal constructor(
    private val rootDirectory: File,
    private val usableSpace: () -> Long,
    private val now: () -> Long,
    private val isReadableImage: (File) -> Boolean,
    private val rotateImage: (source: File, destination: File) -> Boolean = ::rotateJpegClockwise,
    private val cropImage: (source: File, destination: File, insetPercent: Int) -> Boolean = ::cropJpeg,
    private val deleteFile: (File) -> Boolean = File::delete
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        rootDirectory = File(context.cacheDir, ROOT_PATH),
        usableSpace = { StatFs(File(context.cacheDir, ROOT_PATH).absolutePath).availableBytes },
        now = System::currentTimeMillis,
        isReadableImage = ::hasImageDimensions,
        rotateImage = ::rotateJpegClockwise,
        cropImage = ::cropJpeg,
        deleteFile = File::delete
    )

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val lock = Any()

    fun restoreActiveSession(): ScanRestoreResult = synchronized(lock) {
        ensureRoot()
        cleanupStaleSessionsLocked()
        val manifest = readActiveManifest() ?: return@synchronized ScanRestoreResult(null)
        val pendingCompletion = reconcilePendingCapture(manifest)
        val pendingReconciled = readActiveManifest() ?: manifest
        val reconciled = reconcileMissingPages(pendingReconciled)
        if (reconciled != pendingReconciled) writeManifest(reconciled)
        cleanupUnreferencedFiles(reconciled)
        ScanRestoreResult(reconciled.toSnapshot(), pendingCompletion.issue)
    }

    fun prepareCapture(): ScanCapturePreparation = synchronized(lock) {
        try {
            ensureRoot()
            cleanupStaleSessionsLocked()
            var manifest = readActiveManifest() ?: createSession()
            reconcilePendingCapture(manifest)
            manifest = readActiveManifest() ?: manifest
            val issue = ScanAdmissionPolicy.beforeCapture(
                pageCount = manifest.pages.size,
                totalBytes = manifest.pages.sumOf(ManifestPage::byteSize),
                usableBytes = usableSpace()
            )
            if (issue != null) return@synchronized ScanCapturePreparation.Rejected(issue)

            val fileName = "${UUID.randomUUID()}.jpg"
            val captureFile = resolveSessionChild(manifest.id, fileName)
                ?: return@synchronized ScanCapturePreparation.Rejected(ScanIssue.STORAGE_WRITE_FAILED)
            captureFile.parentFile?.mkdirs()
            if (!captureFile.createNewFile()) {
                return@synchronized ScanCapturePreparation.Rejected(ScanIssue.STORAGE_WRITE_FAILED)
            }
            manifest = manifest.copy(
                pendingFileName = fileName,
                pendingReplacementPageFileName = null,
                updatedAtEpochMs = now()
            )
            writeManifest(manifest)
            ScanCapturePreparation.Ready(captureFile)
        } catch (_: Exception) {
            ScanCapturePreparation.Rejected(ScanIssue.STORAGE_WRITE_FAILED)
        }
    }

    fun prepareReplacement(reference: ScannedPageReference): ScanCapturePreparation = synchronized(lock) {
        try {
            ensureRoot()
            var manifest = readActiveManifest()
                ?: return@synchronized ScanCapturePreparation.Rejected(ScanIssue.PAGE_NOT_AVAILABLE)
            reconcilePendingCapture(manifest)
            manifest = readActiveManifest() ?: manifest
            val pageIndex = findPageIndex(manifest, reference)
                ?: return@synchronized ScanCapturePreparation.Rejected(ScanIssue.PAGE_NOT_AVAILABLE)
            val original = manifest.pages[pageIndex]
            val issue = ScanAdmissionPolicy.beforeReplacement(
                totalBytes = manifest.pages.sumOf(ManifestPage::byteSize),
                originalBytes = original.byteSize,
                usableBytes = usableSpace()
            )
            if (issue != null) return@synchronized ScanCapturePreparation.Rejected(issue)

            val fileName = "${UUID.randomUUID()}.jpg"
            val captureFile = resolveSessionChild(manifest.id, fileName)
                ?: return@synchronized ScanCapturePreparation.Rejected(ScanIssue.STORAGE_WRITE_FAILED)
            captureFile.parentFile?.mkdirs()
            if (!captureFile.createNewFile()) {
                return@synchronized ScanCapturePreparation.Rejected(ScanIssue.STORAGE_WRITE_FAILED)
            }
            writeManifest(
                manifest.copy(
                    pendingFileName = fileName,
                    pendingReplacementPageFileName = original.fileName,
                    updatedAtEpochMs = now()
                )
            )
            ScanCapturePreparation.Ready(captureFile)
        } catch (_: Exception) {
            ScanCapturePreparation.Rejected(ScanIssue.STORAGE_WRITE_FAILED)
        }
    }

    fun completeCapture(success: Boolean): ScanCaptureCompletion = synchronized(lock) {
        val manifest = readActiveManifest() ?: return@synchronized ScanCaptureCompletion(null, ScanIssue.CAMERA_FAILED)
        val pendingName = manifest.pendingFileName
            ?: return@synchronized ScanCaptureCompletion(manifest.toSnapshot(), ScanIssue.CAMERA_FAILED)
        val pendingFile = resolveSessionChild(manifest.id, pendingName)
            ?: return@synchronized ScanCaptureCompletion(manifest.toSnapshot(), ScanIssue.STORAGE_WRITE_FAILED)

        if (!success) {
            pendingFile.delete()
            if (manifest.pendingReplacementPageFileName != null) {
                val updated = manifest.copy(
                    pendingFileName = null,
                    pendingReplacementPageFileName = null,
                    updatedAtEpochMs = now()
                )
                writeManifest(updated)
                return@synchronized ScanCaptureCompletion(updated.toSnapshot(), ScanIssue.RETAKE_CANCELLED)
            }
            if (manifest.pages.isEmpty()) {
                deleteSessionDirectory(manifest.id)
                activePointer().delete()
                return@synchronized ScanCaptureCompletion(null, ScanIssue.CAMERA_FAILED)
            }
            val updated = manifest.copy(
                pendingFileName = null,
                pendingReplacementPageFileName = null,
                updatedAtEpochMs = now()
            )
            writeManifest(updated)
            return@synchronized ScanCaptureCompletion(updated.toSnapshot(), ScanIssue.CAMERA_FAILED)
        }

        if (manifest.pendingReplacementPageFileName != null) {
            commitPendingReplacement(manifest, pendingFile, manifest.pendingReplacementPageFileName)
        } else {
            commitPendingCapture(manifest, pendingFile)
        }
    }

    fun discardPendingCapture(): ScanCaptureCompletion = synchronized(lock) {
        val manifest = readActiveManifest() ?: return@synchronized ScanCaptureCompletion(null, ScanIssue.CAMERA_UNAVAILABLE)
        manifest.pendingFileName?.let { name -> resolveSessionChild(manifest.id, name)?.delete() }
        if (manifest.pendingReplacementPageFileName != null) {
            val updated = manifest.copy(
                pendingFileName = null,
                pendingReplacementPageFileName = null,
                updatedAtEpochMs = now()
            )
            writeManifest(updated)
            return@synchronized ScanCaptureCompletion(updated.toSnapshot(), ScanIssue.RETAKE_CAMERA_FAILED)
        }
        if (manifest.pages.isEmpty()) {
            deleteSessionDirectory(manifest.id)
            activePointer().delete()
            return@synchronized ScanCaptureCompletion(null, ScanIssue.CAMERA_UNAVAILABLE)
        }
        val updated = manifest.copy(
            pendingFileName = null,
            pendingReplacementPageFileName = null,
            updatedAtEpochMs = now()
        )
        writeManifest(updated)
        ScanCaptureCompletion(updated.toSnapshot(), ScanIssue.CAMERA_UNAVAILABLE)
    }

    fun movePage(reference: ScannedPageReference, move: ScanPageMove): ScanOperationResult = synchronized(lock) {
        val manifest = readActiveManifest()
            ?: return@synchronized ScanOperationResult(null, ScanIssue.PAGE_NOT_AVAILABLE)
        val sourceIndex = findPageIndex(manifest, reference)
            ?: return@synchronized ScanOperationResult(manifest.toSnapshot(), ScanIssue.PAGE_NOT_AVAILABLE)
        val destinationIndex = when (move) {
            ScanPageMove.EARLIER -> sourceIndex - 1
            ScanPageMove.LATER -> sourceIndex + 1
        }
        if (destinationIndex !in manifest.pages.indices) {
            return@synchronized ScanOperationResult(manifest.toSnapshot(), ScanIssue.MOVE_UNAVAILABLE)
        }
        val reordered = manifest.pages.toMutableList().apply {
            val page = removeAt(sourceIndex)
            add(destinationIndex, page)
        }
        runCatching {
            val updated = manifest.copy(pages = reordered, updatedAtEpochMs = now())
            writeManifest(updated)
            ScanOperationResult(updated.toSnapshot())
        }.getOrElse { ScanOperationResult(manifest.toSnapshot(), ScanIssue.REORDER_FAILED) }
    }

    fun removePage(reference: ScannedPageReference): ScanOperationResult = synchronized(lock) {
        val manifest = readActiveManifest()
            ?: return@synchronized ScanOperationResult(null, ScanIssue.PAGE_NOT_AVAILABLE)
        val pageIndex = findPageIndex(manifest, reference)
            ?: return@synchronized ScanOperationResult(manifest.toSnapshot(), ScanIssue.PAGE_NOT_AVAILABLE)
        val source = resolveSessionChild(manifest.id, manifest.pages[pageIndex].fileName)
            ?: return@synchronized ScanOperationResult(manifest.toSnapshot(), ScanIssue.PAGE_NOT_AVAILABLE)
        val updated = manifest.copy(
            pages = manifest.pages.toMutableList().apply { removeAt(pageIndex) },
            updatedAtEpochMs = now()
        )
        try {
            writeManifest(updated)
        } catch (_: Exception) {
            return@synchronized ScanOperationResult(manifest.toSnapshot(), ScanIssue.REMOVE_FAILED)
        }
        safeDelete(source)
        cleanupUnreferencedFiles(updated)
        ScanOperationResult(updated.toSnapshot())
    }

    fun rotatePage(reference: ScannedPageReference): ScanOperationResult = synchronized(lock) {
        val manifest = readActiveManifest()
            ?: return@synchronized ScanOperationResult(null, ScanIssue.PAGE_NOT_AVAILABLE)
        val pageIndex = findPageIndex(manifest, reference)
            ?: return@synchronized ScanOperationResult(manifest.toSnapshot(), ScanIssue.PAGE_NOT_AVAILABLE)
        val source = resolveSessionChild(manifest.id, manifest.pages[pageIndex].fileName)
            ?: return@synchronized ScanOperationResult(manifest.toSnapshot(), ScanIssue.PAGE_NOT_AVAILABLE)
        val destination = resolveSessionChild(manifest.id, "${UUID.randomUUID()}.jpg")
            ?: return@synchronized ScanOperationResult(manifest.toSnapshot(), ScanIssue.ROTATE_FAILED)
        val transformed = runCatching { rotateImage(source, destination) }.getOrDefault(false)
        if (!transformed || !destination.isFile || destination.length() == 0L || !isReadableImage(destination)) {
            destination.delete()
            return@synchronized ScanOperationResult(manifest.toSnapshot(), ScanIssue.ROTATE_FAILED)
        }
        val totalBytes = manifest.pages.sumOf(ManifestPage::byteSize) -
            manifest.pages[pageIndex].byteSize + destination.length()
        val issue = when {
            totalBytes > MAX_SESSION_BYTES -> ScanIssue.ROTATE_BYTE_LIMIT
            usableSpace() < MIN_FREE_BYTES -> ScanIssue.ROTATE_LOW_STORAGE
            else -> null
        }
        if (issue != null) {
            destination.delete()
            return@synchronized ScanOperationResult(manifest.toSnapshot(), issue)
        }
        val updatedPages = manifest.pages.toMutableList().apply {
            this[pageIndex] = ManifestPage(destination.name, destination.length(), now())
        }
        val updated = manifest.copy(pages = updatedPages, updatedAtEpochMs = now())
        try {
            writeManifest(updated)
        } catch (_: Exception) {
            destination.delete()
            return@synchronized ScanOperationResult(manifest.toSnapshot(), ScanIssue.ROTATE_FAILED)
        }
        safeDelete(source)
        cleanupUnreferencedFiles(updated)
        ScanOperationResult(updated.toSnapshot())
    }

    fun cropPage(reference: ScannedPageReference, insetPercent: Int): ScanOperationResult = synchronized(lock) {
        if (insetPercent !in MIN_CROP_INSET_PERCENT..MAX_CROP_INSET_PERCENT) {
            return@synchronized ScanOperationResult(restoreSnapshotLocked(), ScanIssue.CROP_INVALID)
        }
        val manifest = readActiveManifest()
            ?: return@synchronized ScanOperationResult(null, ScanIssue.PAGE_NOT_AVAILABLE)
        val pageIndex = findPageIndex(manifest, reference)
            ?: return@synchronized ScanOperationResult(manifest.toSnapshot(), ScanIssue.PAGE_NOT_AVAILABLE)
        val source = resolveSessionChild(manifest.id, manifest.pages[pageIndex].fileName)
            ?: return@synchronized ScanOperationResult(manifest.toSnapshot(), ScanIssue.PAGE_NOT_AVAILABLE)
        val destination = resolveSessionChild(manifest.id, "${UUID.randomUUID()}.jpg")
            ?: return@synchronized ScanOperationResult(manifest.toSnapshot(), ScanIssue.CROP_FAILED)
        val transformed = runCatching { cropImage(source, destination, insetPercent) }.getOrDefault(false)
        if (!transformed || !destination.isFile || destination.length() == 0L || !isReadableImage(destination)) {
            destination.delete()
            return@synchronized ScanOperationResult(manifest.toSnapshot(), ScanIssue.CROP_FAILED)
        }
        val totalBytes = manifest.pages.sumOf(ManifestPage::byteSize) -
            manifest.pages[pageIndex].byteSize + destination.length()
        if (totalBytes > MAX_SESSION_BYTES || usableSpace() < MIN_FREE_BYTES) {
            destination.delete()
            return@synchronized ScanOperationResult(manifest.toSnapshot(), ScanIssue.CROP_FAILED)
        }
        val oldPage = manifest.pages[pageIndex]
        val updatedPages = manifest.pages.toMutableList().apply {
            this[pageIndex] = ManifestPage(destination.name, destination.length(), now())
        }
        val updated = manifest.copy(pages = updatedPages, updatedAtEpochMs = now())
        try {
            writeManifest(updated)
        } catch (_: Exception) {
            destination.delete()
            return@synchronized ScanOperationResult(manifest.toSnapshot(), ScanIssue.CROP_FAILED)
        }
        safeDelete(source)
        oldPage.reviewedTextFileName?.let { resolveSessionChild(manifest.id, it)?.let(::safeDelete) }
        cleanupUnreferencedFiles(updated)
        ScanOperationResult(updated.toSnapshot())
    }

    fun saveReviewedText(reference: ScannedPageReference, text: String): ScanOperationResult = synchronized(lock) {
        if (text.length > MAX_REVIEW_TEXT_CHARS) {
            return@synchronized ScanOperationResult(restoreSnapshotLocked(), ScanIssue.OCR_REVIEW_TOO_LARGE)
        }
        val manifest = readActiveManifest()
            ?: return@synchronized ScanOperationResult(null, ScanIssue.PAGE_NOT_AVAILABLE)
        val pageIndex = findPageIndex(manifest, reference)
            ?: return@synchronized ScanOperationResult(manifest.toSnapshot(), ScanIssue.PAGE_NOT_AVAILABLE)
        val destination = resolveSessionChild(manifest.id, "${UUID.randomUUID()}.ocr.txt")
            ?: return@synchronized ScanOperationResult(manifest.toSnapshot(), ScanIssue.OCR_REVIEW_SAVE_FAILED)
        val previousReview = manifest.pages[pageIndex].reviewedTextFileName
        return@synchronized try {
            writeAtomically(destination, text)
            val updatedPages = manifest.pages.toMutableList().apply {
                this[pageIndex] = this[pageIndex].copy(
                    reviewedTextFileName = destination.name,
                    reviewedTextByteSize = destination.length(),
                    updatedAtEpochMs = now()
                )
            }
            val updated = manifest.copy(pages = updatedPages, updatedAtEpochMs = now())
            writeManifest(updated)
            previousReview?.let { resolveSessionChild(manifest.id, it)?.let(::safeDelete) }
            cleanupUnreferencedFiles(updated)
            ScanOperationResult(updated.toSnapshot())
        } catch (_: Exception) {
            safeDelete(destination)
            ScanOperationResult(manifest.toSnapshot(), ScanIssue.OCR_REVIEW_SAVE_FAILED)
        }
    }

    fun updateTitle(title: String) = synchronized(lock) {
        val manifest = readActiveManifest() ?: return@synchronized
        writeManifest(manifest.copy(title = title, updatedAtEpochMs = now()))
    }

    fun cancelActiveSession() = synchronized(lock) {
        val manifest = readActiveManifest()
        if (manifest != null) deleteSessionDirectory(manifest.id)
        activePointer().delete()
    }

    fun completeActiveSession() = cancelActiveSession()

    internal fun cleanupStaleSessions() = synchronized(lock) {
        ensureRoot()
        cleanupStaleSessionsLocked()
    }

    internal fun resolveContainedPage(reference: ScannedPageReference): File? {
        val candidate = runCatching { File(reference.absolutePath).canonicalFile }.getOrNull() ?: return null
        val root = runCatching { rootDirectory.canonicalFile }.getOrNull() ?: return null
        return candidate.takeIf { it.isFile && it.toPath().startsWith(root.toPath()) }
    }

    private fun createSession(): ScanManifest {
        rootDirectory.listFiles()?.forEach { child ->
            if (child.isDirectory && isContained(child)) child.deleteRecursively()
        }
        val id = UUID.randomUUID().toString()
        val timestamp = now()
        val manifest = ScanManifest(id = id, createdAtEpochMs = timestamp, updatedAtEpochMs = timestamp)
        sessionDirectory(id).mkdirs()
        writeManifest(manifest)
        writeAtomically(activePointer(), id)
        return manifest
    }

    private fun reconcilePendingCapture(manifest: ScanManifest): ScanCaptureCompletion {
        val pendingName = manifest.pendingFileName ?: return ScanCaptureCompletion(manifest.toSnapshot())
        val pendingFile = resolveSessionChild(manifest.id, pendingName)
        if (pendingFile == null) {
            val issue = if (manifest.pendingReplacementPageFileName != null) {
                ScanIssue.RETAKE_CAMERA_FAILED
            } else {
                ScanIssue.STORAGE_WRITE_FAILED
            }
            val updated = manifest.copy(
                pendingFileName = null,
                pendingReplacementPageFileName = null,
                updatedAtEpochMs = now()
            )
            writeManifest(updated)
            return ScanCaptureCompletion(updated.toSnapshot(), issue)
        }
        val replacementPage = manifest.pendingReplacementPageFileName
        return if (replacementPage != null) {
            commitPendingReplacement(manifest, pendingFile, replacementPage)
        } else {
            commitPendingCapture(manifest, pendingFile)
        }
    }

    private fun commitPendingCapture(manifest: ScanManifest, pendingFile: File): ScanCaptureCompletion {
        if (!pendingFile.exists() || pendingFile.length() == 0L) {
            pendingFile.delete()
            val updated = manifest.copy(
                pendingFileName = null,
                pendingReplacementPageFileName = null,
                updatedAtEpochMs = now()
            )
            writeManifest(updated)
            return ScanCaptureCompletion(updated.toSnapshot(), ScanIssue.EMPTY_IMAGE)
        }
        if (!isReadableImage(pendingFile)) {
            pendingFile.delete()
            val updated = manifest.copy(
                pendingFileName = null,
                pendingReplacementPageFileName = null,
                updatedAtEpochMs = now()
            )
            writeManifest(updated)
            return ScanCaptureCompletion(updated.toSnapshot(), ScanIssue.CORRUPT_IMAGE)
        }

        val newPage = ManifestPage(pendingFile.name, pendingFile.length(), now())
        val issue = ScanAdmissionPolicy.afterCapture(
            pageCount = manifest.pages.size + 1,
            totalBytes = manifest.pages.sumOf(ManifestPage::byteSize) + newPage.byteSize,
            usableBytes = usableSpace()
        )
        if (issue != null) {
            pendingFile.delete()
            val updated = manifest.copy(
                pendingFileName = null,
                pendingReplacementPageFileName = null,
                updatedAtEpochMs = now()
            )
            writeManifest(updated)
            return ScanCaptureCompletion(updated.toSnapshot(), issue)
        }

        val updated = manifest.copy(
            pages = manifest.pages + newPage,
            pendingFileName = null,
            pendingReplacementPageFileName = null,
            updatedAtEpochMs = now()
        )
        writeManifest(updated)
        return ScanCaptureCompletion(updated.toSnapshot())
    }

    private fun commitPendingReplacement(
        manifest: ScanManifest,
        pendingFile: File,
        replacementPageFileName: String
    ): ScanCaptureCompletion {
        val pageIndex = manifest.pages.indexOfFirst { it.fileName == replacementPageFileName }
        if (pageIndex < 0) {
            pendingFile.delete()
            val updated = manifest.copy(
                pendingFileName = null,
                pendingReplacementPageFileName = null,
                updatedAtEpochMs = now()
            )
            writeManifest(updated)
            return ScanCaptureCompletion(updated.toSnapshot(), ScanIssue.PAGE_NOT_AVAILABLE)
        }
        val originalPage = manifest.pages[pageIndex]
        val originalFile = resolveSessionChild(manifest.id, originalPage.fileName)
        if (originalFile == null || !originalFile.isFile || originalFile.length() != originalPage.byteSize) {
            pendingFile.delete()
            val updated = manifest.copy(
                pendingFileName = null,
                pendingReplacementPageFileName = null,
                updatedAtEpochMs = now()
            )
            writeManifest(updated)
            return ScanCaptureCompletion(updated.toSnapshot(), ScanIssue.PAGE_NOT_AVAILABLE)
        }
        if (!pendingFile.exists() || pendingFile.length() == 0L) {
            pendingFile.delete()
            val updated = manifest.copy(
                pendingFileName = null,
                pendingReplacementPageFileName = null,
                updatedAtEpochMs = now()
            )
            writeManifest(updated)
            return ScanCaptureCompletion(updated.toSnapshot(), ScanIssue.RETAKE_EMPTY_IMAGE)
        }
        if (!isReadableImage(pendingFile)) {
            pendingFile.delete()
            val updated = manifest.copy(
                pendingFileName = null,
                pendingReplacementPageFileName = null,
                updatedAtEpochMs = now()
            )
            writeManifest(updated)
            return ScanCaptureCompletion(updated.toSnapshot(), ScanIssue.RETAKE_CORRUPT_IMAGE)
        }
        val totalBytes = manifest.pages.sumOf(ManifestPage::byteSize) - originalPage.byteSize + pendingFile.length()
        val issue = when {
            totalBytes > MAX_SESSION_BYTES -> ScanIssue.RETAKE_BYTE_LIMIT
            usableSpace() < MIN_FREE_BYTES -> ScanIssue.RETAKE_LOW_STORAGE
            else -> null
        }
        if (issue != null) {
            pendingFile.delete()
            val updated = manifest.copy(
                pendingFileName = null,
                pendingReplacementPageFileName = null,
                updatedAtEpochMs = now()
            )
            writeManifest(updated)
            return ScanCaptureCompletion(updated.toSnapshot(), issue)
        }
        val updatedPages = manifest.pages.toMutableList().apply {
            this[pageIndex] = ManifestPage(pendingFile.name, pendingFile.length(), now())
        }
        val updated = manifest.copy(
            pages = updatedPages,
            pendingFileName = null,
            pendingReplacementPageFileName = null,
            updatedAtEpochMs = now()
        )
        try {
            writeManifest(updated)
        } catch (_: Exception) {
            pendingFile.delete()
            val preserved = manifest.copy(
                pendingFileName = null,
                pendingReplacementPageFileName = null,
                updatedAtEpochMs = now()
            )
            runCatching { writeManifest(preserved) }
            return ScanCaptureCompletion(preserved.toSnapshot(), ScanIssue.STORAGE_WRITE_FAILED)
        }
        safeDelete(originalFile)
        cleanupUnreferencedFiles(updated)
        return ScanCaptureCompletion(updated.toSnapshot())
    }

    private fun reconcileMissingPages(manifest: ScanManifest): ScanManifest {
        val validPages = manifest.pages.filter { page ->
            resolveSessionChild(manifest.id, page.fileName)?.let { it.isFile && it.length() == page.byteSize } == true
        }
        return if (validPages.size == manifest.pages.size) manifest else manifest.copy(
            pages = validPages,
            updatedAtEpochMs = now()
        )
    }

    private fun cleanupUnreferencedFiles(manifest: ScanManifest) {
        val directory = sessionDirectoryOrNull(manifest.id) ?: return
        val protectedNames = buildSet {
            add(MANIFEST_FILE)
            manifest.pages.forEach {
                add(it.fileName)
                it.reviewedTextFileName?.let(::add)
            }
            manifest.pendingFileName?.let(::add)
        }
        directory.listFiles()?.filter(File::isFile)?.forEach { file ->
            if (file.name !in protectedNames) safeDelete(file)
        }
    }

    private fun safeDelete(file: File): Boolean {
        if (!isContained(file) || !file.isFile) return false
        return runCatching { deleteFile(file) }.getOrDefault(false)
    }

    private fun findPageIndex(manifest: ScanManifest, reference: ScannedPageReference): Int? {
        val candidate = runCatching { File(reference.absolutePath).canonicalFile }.getOrNull() ?: return null
        val session = sessionDirectoryOrNull(manifest.id)?.canonicalFile ?: return null
        if (!candidate.toPath().startsWith(session.toPath()) || candidate.parentFile != session) return null
        val index = manifest.pages.indexOfFirst { page ->
            page.fileName == candidate.name && page.byteSize == reference.byteSize
        }
        if (index < 0 || !candidate.isFile || candidate.length() != manifest.pages[index].byteSize) return null
        return index
    }

    private fun restoreSnapshotLocked(): ScanSessionSnapshot? = readActiveManifest()?.toSnapshot()

    private fun cleanupStaleSessionsLocked() {
        val activeId = activePointer().takeIf(File::isFile)?.readText()?.trim().orEmpty()
        var activeStillExists = false
        rootDirectory.listFiles()?.filter(File::isDirectory)?.forEach { directory ->
            if (!isContained(directory)) return@forEach
            val manifest = readManifest(directory)
            val isActive = manifest?.id == activeId
            val isStale = manifest == null || now() - manifest.updatedAtEpochMs > STALE_AFTER_MILLIS
            if (!isActive || isStale) {
                directory.deleteRecursively()
            } else {
                activeStillExists = true
            }
        }
        if (!activeStillExists) activePointer().delete()
    }

    private fun readActiveManifest(): ScanManifest? {
        val id = activePointer().takeIf(File::isFile)?.readText()?.trim().orEmpty()
        if (id.isBlank()) return null
        val directory = sessionDirectoryOrNull(id) ?: return null
        val manifest = readManifest(directory)
        if (manifest?.id != id) return null
        return manifest
    }

    private fun readManifest(directory: File): ScanManifest? = runCatching {
        json.decodeFromString<ScanManifest>(File(directory, MANIFEST_FILE).readText())
    }.getOrNull()

    private fun writeManifest(manifest: ScanManifest) {
        val directory = sessionDirectoryOrNull(manifest.id)
            ?: throw IOException("Invalid scan session path")
        directory.mkdirs()
        writeAtomically(File(directory, MANIFEST_FILE), json.encodeToString(manifest))
    }

    private fun ScanManifest.toSnapshot(): ScanSessionSnapshot {
        val references = pages.mapNotNull { page ->
            resolveSessionChild(id, page.fileName)?.let { file ->
                val review = page.reviewedTextFileName?.let { resolveSessionChild(id, it) }
                    ?.takeIf { it.isFile && it.length() == page.reviewedTextByteSize }
                ScannedPageReference(
                    absolutePath = file.absolutePath,
                    byteSize = page.byteSize,
                    reviewedTextPath = review?.absolutePath,
                    reviewedTextByteSize = review?.length() ?: 0L
                )
            }
        }
        return ScanSessionSnapshot(id, title, references)
    }

    private fun ensureRoot() {
        if (!rootDirectory.exists() && !rootDirectory.mkdirs()) throw IOException("Could not create scan root")
        if (!rootDirectory.isDirectory) throw IOException("Scan root is not a directory")
    }

    private fun activePointer() = File(rootDirectory, ACTIVE_FILE)

    private fun sessionDirectory(id: String) = File(rootDirectory, id)

    private fun sessionDirectoryOrNull(id: String): File? {
        if (!SAFE_NAME.matches(id)) return null
        return sessionDirectory(id).takeIf(::isContained)
    }

    private fun resolveSessionChild(sessionId: String, fileName: String): File? {
        if (!SAFE_NAME.matches(fileName)) return null
        val directory = sessionDirectoryOrNull(sessionId) ?: return null
        return File(directory, fileName).takeIf(::isContained)
    }

    private fun deleteSessionDirectory(id: String) {
        sessionDirectoryOrNull(id)?.takeIf(File::exists)?.deleteRecursively()
    }

    private fun isContained(file: File): Boolean = runCatching {
        file.canonicalFile.toPath().startsWith(rootDirectory.canonicalFile.toPath()) &&
            file.canonicalFile != rootDirectory.canonicalFile
    }.getOrDefault(false)

    private fun writeAtomically(destination: File, content: String) {
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        temporary.writeText(content)
        try {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun moveAtomically(source: File, destination: File): Boolean = runCatching {
        if (!isContained(source) || !isContained(destination)) return@runCatching false
        destination.parentFile?.mkdirs()
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        true
    }.getOrDefault(false)

    companion object {
        const val MAX_PAGE_COUNT = 200
        const val MAX_SESSION_BYTES = 512L * 1024L * 1024L
        const val CAPTURE_RESERVATION_BYTES = 20L * 1024L * 1024L
        const val MIN_FREE_BYTES = 256L * 1024L * 1024L
        const val STALE_AFTER_MILLIS = 7L * 24L * 60L * 60L * 1000L
        const val MIN_CROP_INSET_PERCENT = 1
        const val MAX_CROP_INSET_PERCENT = 35
        const val MAX_REVIEW_TEXT_CHARS = 2_000_000

        private const val ROOT_PATH = "scans/sessions"
        private const val ACTIVE_FILE = "active-session"
        private const val MANIFEST_FILE = "manifest.json"
        private val SAFE_NAME = Regex("[A-Za-z0-9._-]+")

        private fun hasImageDimensions(file: File): Boolean {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            return options.outWidth > 0 && options.outHeight > 0
        }
    }
}

@Serializable
private data class ScanManifest(
    val version: Int = 1,
    val id: String,
    val title: String = "",
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val pages: List<ManifestPage> = emptyList(),
    val pendingFileName: String? = null,
    val pendingReplacementPageFileName: String? = null
)

@Serializable
private data class ManifestPage(
    val fileName: String,
    val byteSize: Long,
    val updatedAtEpochMs: Long = 0L,
    val reviewedTextFileName: String? = null,
    val reviewedTextByteSize: Long = 0L
)

private fun rotateJpegClockwise(source: File, destination: File): Boolean {
    var decoded: Bitmap? = null
    var rotated: Bitmap? = null
    val temporary = File(destination.parentFile, "${destination.name}.rotate.tmp")
    return try {
        decoded = BitmapFactory.decodeFile(source.absolutePath) ?: return false
        val matrix = Matrix().apply { postRotate(90f) }
        rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        FileOutputStream(temporary).use { output ->
            if (rotated.compress(Bitmap.CompressFormat.JPEG, 92, output).not()) {
                throw IOException("Could not encode rotated scan page")
            }
            output.fd.sync()
        }
        try {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        true
    } catch (_: Exception) {
        temporary.delete()
        destination.delete()
        false
    } finally {
        if (rotated !== decoded) rotated?.recycle()
        decoded?.recycle()
    }
}

private fun cropJpeg(source: File, destination: File, insetPercent: Int): Boolean {
    var decoded: Bitmap? = null
    var cropped: Bitmap? = null
    val temporary = File(destination.parentFile, "${destination.name}.crop.tmp")
    return try {
        decoded = BitmapFactory.decodeFile(source.absolutePath) ?: return false
        val insetX = (decoded.width * insetPercent / 100f).toInt()
        val insetY = (decoded.height * insetPercent / 100f).toInt()
        val width = decoded.width - insetX * 2
        val height = decoded.height - insetY * 2
        if (width < 64 || height < 64) return false
        cropped = Bitmap.createBitmap(decoded, insetX, insetY, width, height)
        FileOutputStream(temporary).use { output ->
            if (cropped.compress(Bitmap.CompressFormat.JPEG, 92, output).not()) {
                throw IOException("Could not encode cropped scan page")
            }
            output.fd.sync()
        }
        try {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        true
    } catch (_: Exception) {
        temporary.delete()
        destination.delete()
        false
    } finally {
        if (cropped !== decoded) cropped?.recycle()
        decoded?.recycle()
    }
}
