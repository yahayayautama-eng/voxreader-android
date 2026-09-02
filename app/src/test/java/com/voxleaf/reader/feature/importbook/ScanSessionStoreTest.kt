package com.voxleaf.reader.feature.importbook

import com.voxleaf.reader.domain.usecase.ScannedPageReference
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ScanSessionStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private var now = 1_000L

    @Test
    fun `manifest round trip preserves capture order and byte totals`() {
        val root = temporaryFolder.newFolder("scans")
        val store = store(root)
        capture(store, "first-page")
        capture(store, "second-page-longer")

        val restored = store(root).restoreActiveSession()

        assertNull(restored.issue)
        assertEquals(listOf("first-page", "second-page-longer"), restored.snapshot!!.pages.map {
            File(it.absolutePath).readText()
        })
        assertEquals(28L, restored.snapshot.totalBytes)
    }

    @Test
    fun `process recreation promotes a camera written pending image immediately`() {
        val root = temporaryFolder.newFolder("pending-restore")
        val firstStore = store(root)
        val pending = (firstStore.prepareCapture() as ScanCapturePreparation.Ready).file
        pending.writeText("camera-jpeg")

        val restored = store(root).restoreActiveSession()

        assertNull(restored.issue)
        assertEquals(1, restored.snapshot!!.pages.size)
        assertEquals("camera-jpeg", File(restored.snapshot.pages.single().absolutePath).readText())
    }

    @Test
    fun `empty restored pending capture is removed and reports a recoverable error`() {
        val root = temporaryFolder.newFolder("empty-restore")
        val firstStore = store(root)
        val pending = (firstStore.prepareCapture() as ScanCapturePreparation.Ready).file

        val restored = store(root).restoreActiveSession()

        assertEquals(ScanIssue.EMPTY_IMAGE, restored.issue)
        assertTrue(restored.snapshot!!.pages.isEmpty())
        assertFalse(pending.exists())
    }

    @Test
    fun `corrupt restored pending capture is removed and reports a recoverable error`() {
        val root = temporaryFolder.newFolder("corrupt-restore")
        val firstStore = store(root, validator = { it.readText() != "corrupt" })
        val pending = (firstStore.prepareCapture() as ScanCapturePreparation.Ready).file
        pending.writeText("corrupt")

        val restored = store(root, validator = { it.readText() != "corrupt" }).restoreActiveSession()

        assertEquals(ScanIssue.CORRUPT_IMAGE, restored.issue)
        assertTrue(restored.snapshot!!.pages.isEmpty())
        assertFalse(pending.exists())
    }

    @Test
    fun `stale active and abandoned sessions are cleaned`() {
        val root = temporaryFolder.newFolder("stale")
        val firstStore = store(root)
        capture(firstStore, "page")
        File(root, "abandoned").mkdirs()
        now += ScanSessionStore.STALE_AFTER_MILLIS + 1

        val restored = store(root).restoreActiveSession()

        assertNull(restored.snapshot)
        assertTrue(root.listFiles().orEmpty().none(File::isDirectory))
    }

    @Test
    fun `cancel removes only the active contained session`() {
        val root = temporaryFolder.newFolder("cancel")
        val outside = temporaryFolder.newFile("keep.jpg").apply { writeText("outside") }
        val store = store(root)
        capture(store, "page")

        store.cancelActiveSession()

        assertNull(store.restoreActiveSession().snapshot)
        assertEquals("outside", outside.readText())
        assertTrue(root.listFiles().orEmpty().none(File::isDirectory))
    }

    @Test
    fun `resolver rejects paths outside dedicated scan root`() {
        val root = temporaryFolder.newFolder("containment")
        val outside = temporaryFolder.newFile("outside.jpg").apply { writeText("image") }
        val store = store(root)

        val resolved = store.resolveContainedPage(ScannedPageReference(outside.absolutePath, outside.length()))

        assertNull(resolved)
    }

    @Test
    fun `admission checks page bytes and free space independently`() {
        assertEquals(
            ScanIssue.PAGE_LIMIT,
            ScanAdmissionPolicy.beforeCapture(ScanSessionStore.MAX_PAGE_COUNT, 0, Long.MAX_VALUE)
        )
        assertEquals(
            ScanIssue.BYTE_LIMIT,
            ScanAdmissionPolicy.beforeCapture(
                1,
                ScanSessionStore.MAX_SESSION_BYTES - ScanSessionStore.CAPTURE_RESERVATION_BYTES + 1,
                Long.MAX_VALUE
            )
        )
        assertEquals(
            ScanIssue.LOW_STORAGE,
            ScanAdmissionPolicy.beforeCapture(
                1,
                0,
                ScanSessionStore.MIN_FREE_BYTES + ScanSessionStore.CAPTURE_RESERVATION_BYTES - 1
            )
        )
        assertNull(
            ScanAdmissionPolicy.beforeCapture(
                199,
                ScanSessionStore.MAX_SESSION_BYTES - ScanSessionStore.CAPTURE_RESERVATION_BYTES,
                ScanSessionStore.MIN_FREE_BYTES + ScanSessionStore.CAPTURE_RESERVATION_BYTES
            )
        )
        assertEquals(
            ScanIssue.BYTE_LIMIT,
            ScanAdmissionPolicy.afterCapture(2, ScanSessionStore.MAX_SESSION_BYTES + 1, Long.MAX_VALUE)
        )
        assertEquals(
            ScanIssue.LOW_STORAGE,
            ScanAdmissionPolicy.afterCapture(2, 1, ScanSessionStore.MIN_FREE_BYTES - 1)
        )
    }

    @Test
    fun `reorder persists across recreation and rejects boundaries without mutation`() {
        val root = temporaryFolder.newFolder("reorder")
        val store = store(root)
        capture(store, "one")
        capture(store, "two")
        capture(store, "three")
        val initial = store.restoreActiveSession().snapshot!!

        val moved = store.movePage(initial.pages[1], ScanPageMove.EARLIER)
        val boundary = store.movePage(moved.snapshot!!.pages.first(), ScanPageMove.EARLIER)
        val laterBoundary = store.movePage(moved.snapshot.pages.last(), ScanPageMove.LATER)
        val restored = store(root).restoreActiveSession().snapshot!!

        assertNull(moved.issue)
        assertEquals(ScanIssue.MOVE_UNAVAILABLE, boundary.issue)
        assertEquals(ScanIssue.MOVE_UNAVAILABLE, laterBoundary.issue)
        assertEquals(listOf("two", "one", "three"), restored.pages.map { File(it.absolutePath).readText() })
    }

    @Test
    fun `remove commits manifest first and later cleanup removes an orphaned source`() {
        val root = temporaryFolder.newFolder("remove-crash-order")
        val deletionBlocked = store(root, deleteFile = { false })
        capture(deletionBlocked, "remove-me")
        capture(deletionBlocked, "keep-me")
        val before = deletionBlocked.restoreActiveSession().snapshot!!
        val removedFile = File(before.pages.first().absolutePath)

        val result = deletionBlocked.removePage(before.pages.first())

        assertNull(result.issue)
        assertEquals(listOf("keep-me"), result.snapshot!!.pages.map { File(it.absolutePath).readText() })
        assertTrue("simulated crash residue should remain", removedFile.exists())

        val restored = store(root).restoreActiveSession().snapshot!!
        assertEquals(listOf("keep-me"), restored.pages.map { File(it.absolutePath).readText() })
        assertFalse("restore should clean the now-unreferenced page", removedFile.exists())
    }

    @Test
    fun `orphan cleanup preserves referenced pages and a current pending capture`() {
        val root = temporaryFolder.newFolder("protected-cleanup")
        val store = store(root)
        capture(store, "keep-page")
        val current = store.restoreActiveSession().snapshot!!
        val pending = (store.prepareCapture() as ScanCapturePreparation.Ready).file.apply {
            writeText("pending-page")
        }
        val sessionDirectory = File(current.pages.single().absolutePath).parentFile!!
        val orphan = File(sessionDirectory, "orphan.removed").apply { writeText("orphan") }

        val result = store.movePage(current.pages.single(), ScanPageMove.EARLIER)

        assertEquals(ScanIssue.MOVE_UNAVAILABLE, result.issue)
        assertTrue(File(current.pages.single().absolutePath).exists())
        assertTrue(pending.exists())
        assertTrue(orphan.exists())

        val restored = store(root).restoreActiveSession().snapshot!!
        assertEquals(listOf("keep-page", "pending-page"), restored.pages.map { File(it.absolutePath).readText() })
        assertFalse(orphan.exists())
    }

    @Test
    fun `rotate replaces one page atomically and persists its position`() {
        val root = temporaryFolder.newFolder("rotate-success")
        val store = store(root, rotator = { source, destination ->
            destination.writeText("rotated-${source.readText()}")
            true
        })
        capture(store, "one")
        capture(store, "two")
        val before = store.restoreActiveSession().snapshot!!
        val original = File(before.pages.first().absolutePath)

        val result = store.rotatePage(before.pages.first())
        val restored = store(root).restoreActiveSession().snapshot!!

        assertNull(result.issue)
        assertEquals(listOf("rotated-one", "two"), restored.pages.map { File(it.absolutePath).readText() })
        assertNotEquals(before.pages.first().absolutePath, restored.pages.first().absolutePath)
        assertFalse(original.exists())
    }

    @Test
    fun `failed rotate preserves original page and manifest`() {
        val root = temporaryFolder.newFolder("rotate-failure")
        val store = store(root, rotator = { _, destination ->
            destination.writeText("partial")
            false
        })
        capture(store, "original")
        val before = store.restoreActiveSession().snapshot!!

        val result = store.rotatePage(before.pages.single())
        val restored = store(root).restoreActiveSession().snapshot!!

        assertEquals(ScanIssue.ROTATE_FAILED, result.issue)
        assertEquals(before.pages, restored.pages)
        assertEquals("original", File(restored.pages.single().absolutePath).readText())
        assertEquals(setOf(File(before.pages.single().absolutePath).name, "manifest.json"),
            File(before.pages.single().absolutePath).parentFile!!.listFiles()!!.map(File::getName).toSet())
    }

    @Test
    fun `restore removes old rotate and retake files left by failed cleanup`() {
        val root = temporaryFolder.newFolder("transform-orphans")
        val blockedCleanup = store(
            root,
            rotator = { source, destination ->
                destination.writeText("rotated-${source.readText()}")
                true
            },
            deleteFile = { false }
        )
        capture(blockedCleanup, "one")
        capture(blockedCleanup, "two")
        val initial = blockedCleanup.restoreActiveSession().snapshot!!
        val rotated = blockedCleanup.rotatePage(initial.pages.first()).snapshot!!
        val replacement = (blockedCleanup.prepareReplacement(rotated.pages[1]) as ScanCapturePreparation.Ready).file
        replacement.writeText("two-retaken")
        val retaken = blockedCleanup.completeCapture(true).snapshot!!
        val sessionDirectory = File(retaken.pages.first().absolutePath).parentFile!!

        assertEquals(4, sessionDirectory.listFiles()!!.count { it.extension == "jpg" })

        val restored = store(root).restoreActiveSession().snapshot!!
        assertEquals(listOf("rotated-one", "two-retaken"), restored.pages.map { File(it.absolutePath).readText() })
        assertEquals(2, sessionDirectory.listFiles()!!.count { it.extension == "jpg" })
    }

    @Test
    fun `retake replaces the target in place without changing page count`() {
        val root = temporaryFolder.newFolder("retake-success")
        val store = store(root)
        capture(store, "one")
        capture(store, "two")
        val before = store.restoreActiveSession().snapshot!!
        val original = File(before.pages.first().absolutePath)
        val replacement = (store.prepareReplacement(before.pages.first()) as ScanCapturePreparation.Ready).file
        replacement.writeText("one-retaken")

        val completion = store.completeCapture(true)

        assertNull(completion.issue)
        assertEquals(listOf("one-retaken", "two"), completion.snapshot!!.pages.map { File(it.absolutePath).readText() })
        assertEquals(2, completion.snapshot.pages.size)
        assertFalse(original.exists())
    }

    @Test
    fun `retake cancel and corrupt result preserve original page`() {
        val root = temporaryFolder.newFolder("retake-preserve")
        val store = store(root, validator = { it.readText() != "corrupt" })
        capture(store, "original")
        val original = store.restoreActiveSession().snapshot!!.pages.single()

        store.prepareReplacement(original)
        val cancelled = store.completeCapture(false)
        val corruptFile = (store.prepareReplacement(original) as ScanCapturePreparation.Ready).file
        corruptFile.writeText("corrupt")
        val corrupt = store.completeCapture(true)

        assertEquals(ScanIssue.RETAKE_CANCELLED, cancelled.issue)
        assertEquals(ScanIssue.RETAKE_CORRUPT_IMAGE, corrupt.issue)
        assertEquals("original", File(corrupt.snapshot!!.pages.single().absolutePath).readText())
        assertEquals(original.absolutePath, corrupt.snapshot.pages.single().absolutePath)
    }

    @Test
    fun `process recreation commits valid retake at original index`() {
        val root = temporaryFolder.newFolder("retake-recreation")
        val firstStore = store(root)
        capture(firstStore, "one")
        capture(firstStore, "two")
        val before = firstStore.restoreActiveSession().snapshot!!
        val pending = (firstStore.prepareReplacement(before.pages[1]) as ScanCapturePreparation.Ready).file
        pending.writeText("two-retaken")

        val restored = store(root).restoreActiveSession()

        assertNull(restored.issue)
        assertEquals(listOf("one", "two-retaken"), restored.snapshot!!.pages.map { File(it.absolutePath).readText() })
        assertEquals(2, restored.snapshot.pages.size)
    }

    @Test
    fun `process recreation rejects corrupt retake and preserves original position`() {
        val root = temporaryFolder.newFolder("retake-corrupt-recreation")
        val validator: (File) -> Boolean = { it.readText() != "corrupt" }
        val firstStore = store(root, validator = validator)
        capture(firstStore, "one")
        capture(firstStore, "two")
        val before = firstStore.restoreActiveSession().snapshot!!
        val pending = (firstStore.prepareReplacement(before.pages.first()) as ScanCapturePreparation.Ready).file
        pending.writeText("corrupt")

        val restored = store(root, validator = validator).restoreActiveSession()

        assertEquals(ScanIssue.RETAKE_CORRUPT_IMAGE, restored.issue)
        assertEquals(listOf("one", "two"), restored.snapshot!!.pages.map { File(it.absolutePath).readText() })
    }

    @Test
    fun `process recreation rejects empty retake and preserves original page`() {
        val root = temporaryFolder.newFolder("retake-empty-recreation")
        val firstStore = store(root)
        capture(firstStore, "original")
        val original = firstStore.restoreActiveSession().snapshot!!.pages.single()
        firstStore.prepareReplacement(original)

        val restored = store(root).restoreActiveSession()

        assertEquals(ScanIssue.RETAKE_EMPTY_IMAGE, restored.issue)
        assertEquals(original, restored.snapshot!!.pages.single())
        assertEquals("original", File(restored.snapshot.pages.single().absolutePath).readText())
    }

    @Test
    fun `retake preflight rejects low storage without changing page count`() {
        val root = temporaryFolder.newFolder("retake-storage")
        var usableBytes = Long.MAX_VALUE
        val store = store(root, usableBytes = { usableBytes })
        capture(store, "original")
        val original = store.restoreActiveSession().snapshot!!
        usableBytes = ScanSessionStore.MIN_FREE_BYTES + ScanSessionStore.CAPTURE_RESERVATION_BYTES - 1

        val preparation = store.prepareReplacement(original.pages.single())

        assertEquals(ScanCapturePreparation.Rejected(ScanIssue.RETAKE_LOW_STORAGE), preparation)
        assertEquals(original.pages, store.restoreActiveSession().snapshot!!.pages)
    }

    @Test
    fun `page operations reject references outside the active contained session`() {
        val root = temporaryFolder.newFolder("operation-containment")
        val outside = temporaryFolder.newFile("outside-operation.jpg").apply { writeText("outside") }
        val store = store(root)
        capture(store, "inside")
        val reference = ScannedPageReference(outside.absolutePath, outside.length())

        assertEquals(ScanIssue.PAGE_NOT_AVAILABLE, store.removePage(reference).issue)
        assertEquals(ScanIssue.PAGE_NOT_AVAILABLE, store.rotatePage(reference).issue)
        assertEquals(ScanIssue.PAGE_NOT_AVAILABLE, store.movePage(reference, ScanPageMove.EARLIER).issue)
        assertEquals("outside", outside.readText())
    }

    @Test
    fun `crop atomically replaces one page and failed crop preserves original`() {
        val root = temporaryFolder.newFolder("crop")
        var shouldSucceed = true
        val store = store(root, cropper = { source, destination, inset ->
            destination.writeText("crop-$inset-${source.readText()}")
            shouldSucceed
        })
        capture(store, "original")
        val original = store.restoreActiveSession().snapshot!!.pages.single()

        val cropped = store.cropPage(original, 8)
        val croppedPage = cropped.snapshot!!.pages.single()
        shouldSucceed = false
        val failed = store.cropPage(croppedPage, 10)

        assertNull(cropped.issue)
        assertEquals("crop-8-original", File(croppedPage.absolutePath).readText())
        assertEquals(ScanIssue.CROP_FAILED, failed.issue)
        assertEquals(croppedPage, failed.snapshot!!.pages.single())
        assertEquals("crop-8-original", File(croppedPage.absolutePath).readText())
    }

    @Test
    fun `reviewed OCR text persists and image transforms invalidate stale review`() {
        val root = temporaryFolder.newFolder("ocr-review")
        val store = store(root, rotator = { source, destination ->
            source.copyTo(destination, overwrite = true)
            true
        })
        capture(store, "image")
        val page = store.restoreActiveSession().snapshot!!.pages.single()

        val saved = store.saveReviewedText(page, "corrected text")
        val recreated = store(root).restoreActiveSession().snapshot!!.pages.single()

        assertNull(saved.issue)
        assertEquals("corrected text", File(recreated.reviewedTextPath!!).readText())
        assertEquals(File(recreated.reviewedTextPath!!).length(), recreated.reviewedTextByteSize)

        val rotated = store(root).rotatePage(recreated).snapshot!!.pages.single()
        assertNull(rotated.reviewedTextPath)
        assertFalse(File(checkNotNull(recreated.reviewedTextPath)).exists())
    }

    private fun capture(store: ScanSessionStore, content: String) {
        val file = (store.prepareCapture() as ScanCapturePreparation.Ready).file
        file.writeText(content)
        val completion = store.completeCapture(success = true)
        assertNull(completion.issue)
    }

    private fun store(
        root: File,
        validator: (File) -> Boolean = { true },
        rotator: (File, File) -> Boolean = { source, destination ->
            source.copyTo(destination, overwrite = true)
            true
        },
        cropper: (File, File, Int) -> Boolean = { source, destination, _ ->
            source.copyTo(destination, overwrite = true)
            true
        },
        deleteFile: (File) -> Boolean = File::delete,
        usableBytes: () -> Long = { Long.MAX_VALUE }
    ) = ScanSessionStore(
        rootDirectory = root,
        usableSpace = usableBytes,
        now = { now },
        isReadableImage = validator,
        rotateImage = rotator,
        cropImage = cropper,
        deleteFile = deleteFile
    )
}
