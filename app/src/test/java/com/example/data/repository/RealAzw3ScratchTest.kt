package com.example.data.repository

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Throwaway: runs the parser over a real Gutenberg AZW3 before device testing. Delete after. */
class RealAzw3ScratchTest {

    private val path =
        "C:\\Users\\Yayis\\AppData\\Local\\Temp\\claude\\C--Users-Yayis-Desktop-CODEX-Android-studio-voxleaf\\6d66782b-6158-494e-a76b-34e6003c8a49\\scratchpad\\test.azw3"

    @Test
    fun parseRealBook() {
        val file = File(path)
        assumeTrue(file.exists())

        val started = System.currentTimeMillis()
        val result = MobiParser.parse(file)
        val elapsed = System.currentTimeMillis() - started

        println("=== parsed in ${elapsed}ms")
        println("=== title:  ${result.title}")
        println("=== author: ${result.author}")
        println("=== cover:  ${result.coverBytes?.size ?: 0} bytes")
        println("=== chapters: ${result.chapters.size}")
        result.chapters.take(12).forEachIndexed { index, chapter ->
            val words = chapter.paragraphs.sumOf { it.text.split(" ").size }
            println("===  [$index] ${chapter.title}  (${chapter.paragraphs.size} paras, $words words)")
        }
        val body = result.chapters.getOrNull(3)?.paragraphs?.firstOrNull { !it.isHeading }?.text
        println("=== sample body: ${body?.take(180)}")
        result.chapters.getOrNull(6)?.paragraphs?.take(8)?.forEach {
            println("=== ch6 heading=${it.isHeading} :: ${it.text.take(90)}")
        }
        val total = result.chapters.sumOf { chapter -> chapter.paragraphs.sumOf { it.text.length } }
        println("=== total characters: $total")
    }
}
