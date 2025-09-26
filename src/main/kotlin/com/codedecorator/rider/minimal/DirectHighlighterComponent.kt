package com.codedecorator.rider.minimal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import java.awt.Color

class DirectHighlighterComponent(private val project: Project) : ProjectComponent {
    
    private val activeHighlighters = mutableMapOf<Editor, MutableList<RangeHighlighter>>()
    
    private val editorFactoryListener = object : EditorFactoryListener {
        override fun editorCreated(event: EditorFactoryEvent) {
            val editor = event.editor
            if (editor.project == project) {
                // Добавляем слушатель изменений документа
                editor.document.addDocumentListener(object : DocumentListener {
                    override fun documentChanged(event: DocumentEvent) {
                        ApplicationManager.getApplication().invokeLater {
                            highlightEditor(editor)
                        }
                    }
                })
                
                ApplicationManager.getApplication().invokeLater {
                    highlightEditor(editor)
                }
            }
        }
        
        override fun editorReleased(event: EditorFactoryEvent) {
            val editor = event.editor
            clearHighlighters(editor)
        }
    }
    
    override fun projectOpened() {
        println("====================================")
        println("[CodeDecorator] Plugin v1.1.1 HOTFIX initialized")
        println("[CodeDecorator] Project: ${project.name}")
        println("====================================")
        
        EditorFactory.getInstance().addEditorFactoryListener(editorFactoryListener, project)
        
        // Подсветим уже открытые редакторы
        ApplicationManager.getApplication().invokeLater {
            EditorFactory.getInstance().allEditors.forEach { editor ->
                if (editor.project == project) {
                    highlightEditor(editor)
                }
            }
        }
    }
    
    override fun projectClosed() {
        println("[DirectHighlighter] Project closed: ${project.name}")
        EditorFactory.getInstance().removeEditorFactoryListener(editorFactoryListener)
        activeHighlighters.clear()
    }
    
    // Публичный метод для обновления подсветки во всех редакторах
    fun refreshAllHighlighting() {
        println("[DirectHighlighter] Manual refresh requested")
        ApplicationManager.getApplication().invokeLater {
            val editors = EditorFactory.getInstance().allEditors.filter { it.project == project }
            println("[DirectHighlighter] Found ${editors.size} editors to refresh")
            
            editors.forEach { editor ->
                println("[DirectHighlighter] Refreshing editor: ${editor.document.hashCode()}")
                highlightEditor(editor)
            }
        }
    }

    private fun highlightEditor(editor: Editor) {
        try {
            // Очищаем старые подсветки
            clearHighlighters(editor)
            
            val document = editor.document
            val text = document.text
            val markupModel = editor.markupModel
            val highlighters = mutableListOf<RangeHighlighter>()
            val settings = SimpleSettings.getInstance()
            
            println("[DirectHighlighter] === Starting highlighting ===")
            println("[DirectHighlighter] Total rules in settings: ${settings.rules.size}")
            settings.rules.forEach { rule ->
                println("  - '${rule.name}': bg='${rule.backgroundColor}', fg='${rule.foregroundColor}', enabled=${rule.enabled}")
            }
            println("[DirectHighlighter] Enabled rules: ${settings.getEnabledRules().size}")
            
            // Обрабатываем все включенные правила
            settings.getEnabledRules().forEach { rule ->
                println("---------------------------")
                println("[DirectHighlighter] Processing rule: '${rule.name}'")
                println("[DirectHighlighter] Pattern: '${rule.targetWord}' (regex: ${rule.isRegex})")
                
                val shouldHighlight = if (rule.condition.trim().isEmpty()) {
                    // Если условие пусто, всегда подсвечиваем
                    true
                } else {
                    // Если условие задано, проверяем есть ли оно в тексте
                    text.contains(rule.condition.trim())
                }
                
                if (shouldHighlight) {
                    val matches = if (rule.exclusion.trim().isEmpty()) {
                        // Если исключений нет, ищем все совпадения
                        if (rule.isRegex) {
                            if (rule.targetWord.startsWith("//")) {
                                // Специальная обработка для комментариев - ищем с отступами, но выделяем только комментарий
                                println("[DirectHighlighter] Using comment matching for pattern: '${rule.targetWord}'")
                                findCommentMatches(text, rule.targetWord)
                            } else {
                                println("[DirectHighlighter] Using standard regex matching")
                                findRegexMatches(text, rule.targetWord)
                            }
                        } else {
                            findAllMatches(text, rule.targetWord)
                        }
                    } else {
                        // Если есть исключения, фильтруем совпадения
                        val allMatches = if (rule.isRegex) {
                            if (rule.targetWord.startsWith("//")) {
                                // Специальная обработка для комментариев - ищем с отступами, но выделяем только комментарий
                                println("[DirectHighlighter] Using comment matching for exclusion pattern: '${rule.targetWord}'")
                                findCommentMatches(text, rule.targetWord)
                            } else {
                                println("[DirectHighlighter] Using standard regex matching for exclusion")
                                findRegexMatches(text, rule.targetWord)
                            }
                        } else {
                            findAllMatches(text, rule.targetWord)
                        }
                        filterMatchesWithExclusion(text, allMatches, rule.exclusion.trim())
                    }
                    
                    val attributes = createTextAttributes(rule)
                    
                    matches.forEach { range ->
                        val highlighter = markupModel.addRangeHighlighter(
                            range.startOffset, range.endOffset,
                            HighlighterLayer.SELECTION - 1,
                            attributes,
                            com.intellij.openapi.editor.markup.HighlighterTargetArea.EXACT_RANGE
                        )
                        highlighters.add(highlighter)
                    }
                    println("[DirectHighlighter] Rule '${rule.name}': added ${matches.size} highlights for '${rule.targetWord}' (regex: ${rule.isRegex}, condition: '${rule.condition.ifEmpty { "none" }}', exclusion: '${rule.exclusion.ifEmpty { "none" }}')")
                } else {
                    println("[DirectHighlighter] Rule '${rule.name}': condition '${rule.condition}' not found - no highlighting")
                }
            }
            
            activeHighlighters[editor] = highlighters
            println("[DirectHighlighter] Total highlights added: ${highlighters.size}")
            
        } catch (e: Exception) {
            println("[DirectHighlighter] Error highlighting: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun createTextAttributes(rule: HighlightRule): TextAttributes {
        return TextAttributes().apply {
            // Обрабатываем цвет фона
            if (rule.backgroundColor.isNotEmpty()) {
                try {
                    backgroundColor = parseColor(rule.backgroundColor)
                    println("[DirectHighlighter] Set background color '${rule.backgroundColor}' -> ${backgroundColor}")
                } catch (e: Exception) {
                    backgroundColor = null
                    println("[DirectHighlighter] Invalid background color '${rule.backgroundColor}', using transparent")
                }
            }
            
            // Обрабатываем цвет текста
            if (rule.foregroundColor.isNotEmpty()) {
                try {
                    foregroundColor = parseColor(rule.foregroundColor)
                    println("[DirectHighlighter] Set foreground color '${rule.foregroundColor}' -> ${foregroundColor}")
                } catch (e: Exception) {
                    foregroundColor = null
                    println("[DirectHighlighter] Invalid foreground color '${rule.foregroundColor}', using default")
                }
            }
            
            // Применяем стиль шрифта
            fontType = rule.fontStyle.value
            
            // Применяем оформление текста
            when (rule.textDecoration) {
                HighlightRule.TextDecoration.UNDERLINE -> {
                    effectType = com.intellij.openapi.editor.markup.EffectType.LINE_UNDERSCORE
                    effectColor = foregroundColor ?: java.awt.Color.BLACK
                }
                HighlightRule.TextDecoration.STRIKETHROUGH -> {
                    effectType = com.intellij.openapi.editor.markup.EffectType.STRIKEOUT
                    effectColor = foregroundColor ?: java.awt.Color.BLACK
                }
                HighlightRule.TextDecoration.NONE -> {
                    // Никакого эффекта
                }
            }
        }
    }
    
    private fun parseColor(colorString: String): java.awt.Color? {
        return when {
            colorString.isEmpty() -> null
            colorString.startsWith("rgba(") -> parseRgbaColor(colorString)
            colorString.startsWith("#") -> java.awt.Color.decode(colorString)
            else -> java.awt.Color.decode("#$colorString")
        }
    }
    
    private fun parseRgbaColor(rgba: String): java.awt.Color? {
        try {
            // rgba(255, 107, 53, 0.1) -> [255, 107, 53, 0.1]
            val values = rgba.removePrefix("rgba(").removeSuffix(")").split(",").map { it.trim() }
            if (values.size >= 3) {
                val r = values[0].toInt()
                val g = values[1].toInt()
                val b = values[2].toInt()
                val a = if (values.size > 3) (values[3].toFloat() * 255).toInt() else 255
                return java.awt.Color(r, g, b, a)
            }
        } catch (e: Exception) {
            println("[DirectHighlighter] Failed to parse RGBA color '$rgba': ${e.message}")
        }
        return null
    }
    
    private fun findAllMatches(text: String, target: String): List<TextRange> {
        val matches = mutableListOf<TextRange>()
        var index = 0
        while (true) {
            index = text.indexOf(target, index)
            if (index == -1) break
            matches.add(TextRange(index, index + target.length))
            index += target.length
        }
        return matches
    }
    
    private fun findCommentMatches(text: String, commentPattern: String): List<TextRange> {
        val matches = mutableListOf<TextRange>()
        try {
            println("[DirectHighlighter] Finding comments with pattern: '$commentPattern'")
            
            // Создаем расширенный паттерн для поиска с отступами
            val searchPattern = ".*$commentPattern"
            val searchRegex = Regex(searchPattern)
            val commentRegex = Regex(commentPattern)
            
            val searchResults = searchRegex.findAll(text)
            
            for (searchResult in searchResults) {
                val fullMatch = text.substring(searchResult.range.first, searchResult.range.last + 1)
                
                // Теперь найдем только комментарий внутри полного совпадения  
                val commentMatch = commentRegex.find(fullMatch)
                if (commentMatch != null) {
                    val commentStart = searchResult.range.first + commentMatch.range.first
                    val commentEnd = searchResult.range.first + commentMatch.range.last + 1
                    
                    matches.add(TextRange(commentStart, commentEnd))
                    
                    val commentText = text.substring(commentStart, commentEnd)
                    println("[DirectHighlighter] Found comment match: '$commentText' at $commentStart-$commentEnd")
                }
            }
            
            println("[DirectHighlighter] Comment pattern '$commentPattern' found ${matches.size} matches")
        } catch (e: Exception) {
            println("[DirectHighlighter] Invalid comment pattern '$commentPattern': ${e.message}")
            e.printStackTrace()
        }
        return matches
    }

    private fun findRegexMatches(text: String, regexPattern: String): List<TextRange> {
        val matches = mutableListOf<TextRange>()
        try {
            println("[DirectHighlighter] Testing regex pattern: '$regexPattern'")
            
            val regex = Regex(regexPattern)
            val matchResults = regex.findAll(text)
            
            for (matchResult in matchResults) {
                val matchText = text.substring(matchResult.range.first, matchResult.range.last + 1)
                matches.add(TextRange(matchResult.range.first, matchResult.range.last + 1))
                println("[DirectHighlighter] Found match: '$matchText' at ${matchResult.range.first}-${matchResult.range.last + 1}")
            }
            
            println("[DirectHighlighter] Regex '$regexPattern' found ${matches.size} matches")
        } catch (e: Exception) {
            println("[DirectHighlighter] Invalid regex '$regexPattern': ${e.message}")
            e.printStackTrace()
        }
        return matches
    }
    
    private fun filterMatchesWithExclusion(text: String, matches: List<TextRange>, exclusion: String): List<TextRange> {
        val filteredMatches = mutableListOf<TextRange>()
        
        for (match in matches) {
            // Получаем строку, содержащую найденное совпадение
            val lines = text.lines()
            var currentPos = 0
            var matchLine = ""
            
            // Находим строку с совпадением
            for (line in lines) {
                val lineEnd = currentPos + line.length
                if (match.startOffset >= currentPos && match.startOffset < lineEnd) {
                    matchLine = line
                    break
                }
                currentPos = lineEnd + 1 // +1 для символа новой строки
            }
            
            // Проверяем, содержит ли строка исключение
            if (!matchLine.contains(exclusion)) {
                filteredMatches.add(match)
            } else {
                println("[DirectHighlighter] Excluded match at ${match.startOffset} because line contains '$exclusion'")
            }
        }
        
        return filteredMatches
    }
    
    private fun clearHighlighters(editor: Editor) {
        activeHighlighters[editor]?.forEach { highlighter ->
            editor.markupModel.removeHighlighter(highlighter)
        }
        activeHighlighters.remove(editor)
    }
}