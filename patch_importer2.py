with open("app/src/main/java/com/example/data/repository/TextBookImporterImpl.kt", "r") as f:
    lines = f.readlines()

start_index = -1
end_index = -1

for i, line in enumerate(lines):
    if "// 4. Detect encoding and normalize" in line:
        start_index = i
    if "emit(ImportState.Importing(0.8f))" in line:
        end_index = i
        break

new_logic = """            var title: String = "Unknown Title"
            var author: String = "Unknown Author"
            val sections = mutableListOf<SectionEntity>()
            val chunks = mutableListOf<TextChunkEntity>()
            var progress = 0.4f
            
            if (isDocx) {
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
            }
"""

if start_index != -1 and end_index != -1:
    lines[start_index:end_index] = [new_logic]

with open("app/src/main/java/com/example/data/repository/TextBookImporterImpl.kt", "w") as f:
    f.writelines(lines)
