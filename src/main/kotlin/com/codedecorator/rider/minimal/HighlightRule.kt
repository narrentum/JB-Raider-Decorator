package com.codedecorator.rider.minimal

import java.awt.Font

data class HighlightRule(
    var id: String = "",
    var name: String = "", // Имя правила (для отображения)
    var targetWord: String = "", // Слово/паттерн для подсветки
    var isRegex: Boolean = false, // Является ли targetWord регулярным выражением
    var condition: String = "", // Условие (пустое = всегда)
    var exclusion: String = "", // Исключение - если найдено, НЕ подсвечивать
    var backgroundColor: String = "#FFFF00", // Цвет фона (hex)
    var foregroundColor: String = "#000000", // Цвет текста (hex)
    var fontStyle: FontStyle = FontStyle.NORMAL, // Стиль шрифта
    var textDecoration: TextDecoration = TextDecoration.NONE, // Оформление текста
    var enabled: Boolean = true // Включено ли правило
) {
    enum class FontStyle(val displayName: String, val value: Int) {
        NORMAL("Нормальный", Font.PLAIN),
        BOLD("Жирный", Font.BOLD),
        ITALIC("Курсив", Font.ITALIC),
        BOLD_ITALIC("Жирный курсив", Font.BOLD or Font.ITALIC);
        
        override fun toString(): String = displayName
    }
    
    enum class TextDecoration(val displayName: String) {
        NONE("Нет"),
        UNDERLINE("Подчеркивание"),
        STRIKETHROUGH("Зачеркивание");
        
        override fun toString(): String = displayName
    }
    
    companion object {
        fun createDefault(): List<HighlightRule> = listOf(
            HighlightRule(
                id = "rule_this",
                name = "_this highlighting",
                targetWord = "(?=(?:[^\"]*\"[^\"]*\")*[^\"]*\$)(?=(?:[^']*'[^']*')*[^']*\$)(?=(?:[^`]*`[^`]*`)*[^`]*\$)\\b_this\\b",
                isRegex = true,
                condition = "using _this",
                exclusion = "",
                backgroundColor = "rgba(0, 102, 255, 0.1)",
                foregroundColor = "#569CD6",
                fontStyle = FontStyle.NORMAL,
                textDecoration = TextDecoration.NONE,
                enabled = true
            ),
            HighlightRule(
                id = "rule_todo_fixed",
                name = "TODO [Fixed] - Зачеркнутый", 
                targetWord = "//\\s*TODO:.*?\\[Fixed\\].*",
                isRegex = true,
                condition = "",
                exclusion = "",
                backgroundColor = "",
                foregroundColor = "#888888",
                fontStyle = FontStyle.NORMAL,
                textDecoration = TextDecoration.STRIKETHROUGH,
                enabled = true
            ),
            HighlightRule(
                id = "rule_todo_qa",
                name = "TODO [QA] - Orange background",
                targetWord = "//\\s*TODO:.*?\\[QA\\].*",
                isRegex = true,
                condition = "",
                exclusion = "",
                backgroundColor = "rgba(255, 107, 53, 0.1)",
                foregroundColor = "#FF6B35",
                fontStyle = FontStyle.NORMAL,
                textDecoration = TextDecoration.NONE,
                enabled = true
            ),
            HighlightRule(
                id = "rule_todo_progress",
                name = "TODO [InProgress] - Blue background",
                targetWord = "//\\s*TODO:.*?\\[InProgress\\].*",
                isRegex = true,
                condition = "",
                exclusion = "",
                backgroundColor = "rgba(0, 123, 255, 0.1)",
                foregroundColor = "#007BFF",
                fontStyle = FontStyle.BOLD,
                textDecoration = TextDecoration.NONE,
                enabled = true
            ),
            HighlightRule(
                id = "rule_fixme_fixed",
                name = "FIXME [Fixed] - Зачеркнутый",
                targetWord = "//\\s*FIXME:.*?\\[Fixed\\].*",
                isRegex = true,
                condition = "",
                exclusion = "",
                backgroundColor = "",
                foregroundColor = "#888888",
                fontStyle = FontStyle.NORMAL,
                textDecoration = TextDecoration.STRIKETHROUGH,
                enabled = true
            ),
            HighlightRule(
                id = "rule_react",
                name = "React components",
                targetWord = "React",
                isRegex = false,
                condition = "import React",
                exclusion = "",
                backgroundColor = "rgba(97, 218, 251, 0.1)",
                foregroundColor = "#61DAFB",
                fontStyle = FontStyle.BOLD,
                textDecoration = TextDecoration.NONE,
                enabled = true
            ),
            HighlightRule(
                id = "rule_console_log",
                name = "console.log statements",
                targetWord = "console\\.log",
                isRegex = true,
                condition = "",
                exclusion = "",
                backgroundColor = "rgba(255, 193, 7, 0.1)",
                foregroundColor = "#FFC107",
                fontStyle = FontStyle.NORMAL,
                textDecoration = TextDecoration.NONE,
                enabled = true
            ),
            HighlightRule(
                id = "rule_critical",
                name = "[CRITICAL] - Подчеркнутый",
                targetWord = "//\\s*\\[CRITICAL\\].*",
                isRegex = true,
                condition = "",
                exclusion = "",
                backgroundColor = "rgba(220, 53, 69, 0.1)",
                foregroundColor = "#DC3545",
                fontStyle = FontStyle.BOLD,
                textDecoration = TextDecoration.UNDERLINE,
                enabled = true
            )
        )
    }
}
