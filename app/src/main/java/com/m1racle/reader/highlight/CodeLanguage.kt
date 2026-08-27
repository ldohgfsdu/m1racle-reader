package com.m1racle.reader.highlight

/**
 * Languages with lightweight syntax highlighting support.
 */
enum class CodeLanguage {
    KOTLIN,
    JAVA,
    PYTHON,
    JAVASCRIPT,
    JSON,
    XML,
    SHELL,
    MARKDOWN,
    PLAIN,
    ;

    companion object {
        fun from(language: String?): CodeLanguage {
            val key = language?.trim()?.lowercase().orEmpty()
            if (key.isEmpty()) return PLAIN
            return when (key) {
                "kotlin", "kt", "kts" -> KOTLIN
                "java" -> JAVA
                "python", "py" -> PYTHON
                "javascript", "js", "typescript", "ts" -> JAVASCRIPT
                "json" -> JSON
                "xml", "html", "htm" -> XML
                "shell", "bash", "sh", "zsh" -> SHELL
                "markdown", "md" -> MARKDOWN
                else -> PLAIN
            }
        }
    }
}
