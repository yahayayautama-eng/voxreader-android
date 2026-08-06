with open("app/src/main/java/com/example/data/repository/DocxParser.kt", "r") as f:
    content = f.read()

import re
content = content.replace("var isHeading = false", "var isHeading = false\n        var isList = false")
content = content.replace("isHeading = false", "isHeading = false\n                        isList = false")

list_handling = """                    } else if (name == "w:numPr") {
                        isList = true
                    }"""

content = content.replace("                    } else if (name == \"w:pStyle\") {", list_handling + "\n                    } else if (name == \"w:pStyle\") {")

text_format = """                        if (text.isNotEmpty()) {
                            val formattedText = if (isList) "- $text" else text
                            paragraphs.add(Paragraph(formattedText, isHeading))
                        }"""
content = content.replace("""                        if (text.isNotEmpty()) {
                            paragraphs.add(Paragraph(text, isHeading))
                        }""", text_format)

with open("app/src/main/java/com/example/data/repository/DocxParser.kt", "w") as f:
    f.write(content)
