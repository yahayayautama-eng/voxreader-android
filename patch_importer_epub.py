with open("app/src/main/java/com/example/data/repository/TextBookImporterImpl.kt", "r") as f:
    content = f.read()

import re

# Update mime type check
old_mime = """            val isTxt = mimeType == "text/plain" || displayName.endsWith(".txt", ignoreCase = true)
            val isDocx = mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" || displayName.endsWith(".docx", ignoreCase = true)
            
            if (!isTxt && !isDocx) {"""

new_mime = """            val isTxt = mimeType == "text/plain" || displayName.endsWith(".txt", ignoreCase = true)
            val isDocx = mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" || displayName.endsWith(".docx", ignoreCase = true)
            val isEpub = mimeType == "application/epub+zip" || displayName.endsWith(".epub", ignoreCase = true)
            
            if (!isTxt && !isDocx && !isEpub) {"""
content = content.replace(old_mime, new_mime)

# Update error message
content = content.replace('emit(ImportState.Error("Invalid file type. Only .txt and .docx files are supported."))', 'emit(ImportState.Error("Invalid file type. Only .txt, .docx, and .epub files are supported."))')

# Update extension logic
old_ext = 'val extension = if (isDocx) ".docx" else ".txt"'
new_ext = 'val extension = if (isDocx) ".docx" else if (isEpub) ".epub" else ".txt"'
content = content.replace(old_ext, new_ext)

# Update parsing logic
old_parsing = """            if (isDocx) {
                try {
                    val result = DocxParser.parse(destFile)
                    title = result.title ?: displayName.removeSuffix(".docx").removeSuffix(".DOCX")
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
            }"""

new_parsing = """            if (isDocx || isEpub) {
                try {
                    val paragraphs = mutableListOf<com.example.data.repository.DocxParser.Paragraph>()
                    
                    if (isDocx) {
                        val result = DocxParser.parse(destFile)
                        title = result.title ?: displayName.removeSuffix(".docx").removeSuffix(".DOCX")
                        author = result.author ?: "Unknown Author"
                        paragraphs.addAll(result.paragraphs)
                    } else {
                        val result = EpubParser.parse(destFile)
                        title = result.title ?: displayName.removeSuffix(".epub").removeSuffix(".EPUB")
                        author = result.author ?: "Unknown Author"
                        result.paragraphs.forEach { 
                            paragraphs.add(com.example.data.repository.DocxParser.Paragraph(it.text, it.isHeading)) 
                        }
                    }
                    
                    var currentSectionId = UUID.randomUUID().toString()
                    var currentChapterNumber = 1
                    var currentSectionTitle = title
                    var currentSectionTextLength = 0
                    var currentSectionSentences = mutableListOf<String>()
                    
                    val progressStep = 0.4f / max(paragraphs.size, 1)
                    
                    paragraphs.forEachIndexed { index, paragraph ->
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
                    emit(ImportState.Error("Failed to parse file: ${e.message}"))
                    return@flow
                }
            }"""

if old_parsing in content:
    content = content.replace(old_parsing, new_parsing)
else:
    print("WARNING: Could not find old parsing logic in patch_importer_epub.")

with open("app/src/main/java/com/example/data/repository/TextBookImporterImpl.kt", "w") as f:
    f.write(content)
