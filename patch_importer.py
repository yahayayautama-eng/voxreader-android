with open("app/src/main/java/com/example/data/repository/TextBookImporterImpl.kt", "r") as f:
    content = f.read()

import re

# Update mime type check
old_mime_check = """            if (mimeType != "text/plain" && !displayName.endsWith(".txt", ignoreCase = true)) {
                emit(ImportState.Error("Invalid file type. Only .txt files are supported."))
                return@flow
            }"""

new_mime_check = """            val isTxt = mimeType == "text/plain" || displayName.endsWith(".txt", ignoreCase = true)
            val isDocx = mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" || displayName.endsWith(".docx", ignoreCase = true)
            
            if (!isTxt && !isDocx) {
                emit(ImportState.Error("Invalid file type. Only .txt and .docx files are supported."))
                return@flow
            }"""

content = content.replace(old_mime_check, new_mime_check)

# Update destFile extension
content = content.replace('val destFile = File(importDir, "${bookId}.txt")', 'val extension = if (isDocx) ".docx" else ".txt"\n            val destFile = File(importDir, "${bookId}${extension}")')


# Update parsing logic
old_parsing = """            // 4. Detect encoding and normalize
            val (content, encoding) = readWithEncodingFallback(destFile)
            if (content.isBlank()) {
                destFile.delete()
                emit(ImportState.Error("File contains no readable text."))
                return@flow
            }
            // Extract title metadata
            val title = extractTitle(content, displayName)
            val author = "Unknown Author"
            emit(ImportState.Importing(0.4f))

            // 5. Break into sections and chunks
            val sections = mutableListOf<SectionEntity>()
            val chunks = mutableListOf<TextChunkEntity>()
            // Simple splitting: split by double newline
            val rawSections = content.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }
            
            var progress = 0.4f
            val progressStep = 0.4f / max(rawSections.size, 1)

            rawSections.forEachIndexed { index, sectionText ->
                if (!coroutineContext.isActive) {
                    destFile.delete()
                    throw kotlinx.coroutines.CancellationException("Import cancelled")
                }
                
                val sectionId = UUID.randomUUID().toString()
                sections.add(
                    SectionEntity(
                        id = sectionId,
                        bookId = bookId,
                        chapterNumber = index + 1,
                        title = "Chapter ${index + 1}",
                        estimatedMinutes = max(1, sectionText.length / 1000)
                    )
                )

                // Chunking by sentences or lines
                val sentences = sectionText.split(Regex("(?<=[.!?])\\\\s+")).filter { it.isNotBlank() }
                sentences.forEachIndexed { sIndex, sentence ->
                    chunks.add(
                        TextChunkEntity(
                            id = UUID.randomUUID().toString(),
                            sectionId = sectionId,
                            sequenceNumber = sIndex,
                            text = sentence
                        )
                    )
                }

                progress += progressStep
                if (index % 10 == 0) {
                    emit(ImportState.Importing(progress))
                    yield() // Check cancellation
                }
            }"""

new_parsing = """            var title: String = "Unknown Title"
            var author: String = "Unknown Author"
            val sections = mutableListOf<SectionEntity>()
            val chunks = mutableListOf<TextChunkEntity>()
            var progress = 0.4f
            
            if (isDocx) {
                try {
                    val result = DocxParser.parse(destFile)
                    title = result.title ?: displayName.removeSuffix(".docx")
                    author = result.author ?: "Unknown Author"
                    
                    var currentSectionId = UUID.randomUUID().toString()
                    var currentChapterNumber = 1
                    var currentSectionTitle = title
                    var currentSectionTextLength = 0
                    var currentSectionSentences = mutableListOf<String>()
                    
                    val progressStep = 0.4f / max(result.paragraphs.size, 1)
                    
                    result.paragraphs.forEachIndexed { index, paragraph ->
                        if (!coroutineContext.isActive) {
                            destFile.delete()
                            throw kotlinx.coroutines.CancellationException("Import cancelled")
                        }
                        
                        if (paragraph.isHeading && currentSectionSentences.isNotEmpty()) {
                            // Finish current section
                            sections.add(SectionEntity(currentSectionId, bookId, currentChapterNumber, currentSectionTitle, max(1, currentSectionTextLength / 1000)))
                            currentSectionSentences.forEachIndexed { sIndex, sentence ->
                                chunks.add(TextChunkEntity(UUID.randomUUID().toString(), currentSectionId, sIndex, sentence))
                            }
                            
                            currentSectionId = UUID.randomUUID().toString()
                            currentChapterNumber++
                            currentSectionTitle = paragraph.text
                            currentSectionTextLength = 0
                            currentSectionSentences = mutableListOf()
                        } else if (paragraph.isHeading && currentSectionSentences.isEmpty()) {
                            currentSectionTitle = paragraph.text
                        }
                        
                        // We do not treat heading itself as body text, but for simple parsing we can just add it
                        val sentences = paragraph.text.split(Regex("(?<=[.!?])\\\\s+")).filter { it.isNotBlank() }
                        currentSectionSentences.addAll(sentences)
                        currentSectionTextLength += paragraph.text.length
                        
                        progress += progressStep
                        if (index % 100 == 0) {
                            emit(ImportState.Importing(progress))
                            yield()
                        }
                    }
                    
                    if (currentSectionSentences.isNotEmpty()) {
                        sections.add(SectionEntity(currentSectionId, bookId, currentChapterNumber, currentSectionTitle, max(1, currentSectionTextLength / 1000)))
                        currentSectionSentences.forEachIndexed { sIndex, sentence ->
                            chunks.add(TextChunkEntity(UUID.randomUUID().toString(), currentSectionId, sIndex, sentence))
                        }
                    }
                } catch (e: Exception) {
                    destFile.delete()
                    emit(ImportState.Error("Failed to parse DOCX: ${e.message}"))
                    return@flow
                }
            } else {
                val (fileContent, encoding) = readWithEncodingFallback(destFile)
                if (fileContent.isBlank()) {
                    destFile.delete()
                    emit(ImportState.Error("File contains no readable text."))
                    return@flow
                }
                title = extractTitle(fileContent, displayName)
                author = "Unknown Author"
                
                val rawSections = fileContent.split(Regex("\\\\n\\\\s*\\\\n")).filter { it.isNotBlank() }
                val progressStep = 0.4f / max(rawSections.size, 1)

                rawSections.forEachIndexed { index, sectionText ->
                    if (!coroutineContext.isActive) {
                        destFile.delete()
                        throw kotlinx.coroutines.CancellationException("Import cancelled")
                    }
                    
                    val sectionId = UUID.randomUUID().toString()
                    sections.add(
                        SectionEntity(
                            id = sectionId,
                            bookId = bookId,
                            chapterNumber = index + 1,
                            title = "Chapter ${index + 1}",
                            estimatedMinutes = max(1, sectionText.length / 1000)
                        )
                    )

                    val sentences = sectionText.split(Regex("(?<=[.!?])\\\\s+")).filter { it.isNotBlank() }
                    sentences.forEachIndexed { sIndex, sentence ->
                        chunks.add(
                            TextChunkEntity(
                                id = UUID.randomUUID().toString(),
                                sectionId = sectionId,
                                sequenceNumber = sIndex,
                                text = sentence
                            )
                        )
                    }

                    progress += progressStep
                    if (index % 10 == 0) {
                        emit(ImportState.Importing(progress))
                        yield()
                    }
                }
            }"""

if old_parsing in content:
    content = content.replace(old_parsing, new_parsing)
else:
    print("WARNING: Could not find old parsing logic.")

with open("app/src/main/java/com/example/data/repository/TextBookImporterImpl.kt", "w") as f:
    f.write(content)
