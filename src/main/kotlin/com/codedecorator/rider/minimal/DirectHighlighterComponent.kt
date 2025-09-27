package com.codedecorator.rider.minimal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
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
import com.intellij.openapi.command.CommandListener
import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.TextRange
import java.awt.Color
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap
import com.intellij.util.concurrency.AppExecutorUtil
import javax.swing.*
import javax.swing.border.EmptyBorder
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent

@Service(Service.Level.PROJECT)
class DirectHighlighterComponent(private val project: Project) : Disposable {
    
    private val activeHighlighters = ConcurrentHashMap<Editor, MutableList<RangeHighlighter>>()
    private val lastHighlightTime = ConcurrentHashMap<Editor, Long>()

    // generation map per editor to detect stale results
    private val generationMap = ConcurrentHashMap<Editor, AtomicInteger>()
    // running futures per editor so we can cancel previous tasks
    private val runningFutures = ConcurrentHashMap<Editor, MutableList<Future<*>>>()
    // per-editor map taskId -> Future to allow cancelling specific named tasks and tracking
    private val runningTasks = ConcurrentHashMap<Editor, ConcurrentHashMap<String, Future<*>>>()
    // overlay panels per editor to show run statistics
    private val overlayPanels = ConcurrentHashMap<Editor, JPanel>()
    // overlay panels per editor per task (rule)
    private val overlayTaskPanels = ConcurrentHashMap<Editor, ConcurrentHashMap<String, JPanel>>()
    // overlay metadata
    private val overlayShownAt = ConcurrentHashMap<Editor, Long>()
    private val overlayStartTime = ConcurrentHashMap<Editor, Long>()
    private val overlayTotalRules = ConcurrentHashMap<Editor, Int>()
    private val overlayCompletedRules = ConcurrentHashMap<Editor, Int>()
    // recent keyboard edit timestamps per editor (used to allow only keyboard-triggered highlights)
    private val recentUserEditTimestamps = ConcurrentHashMap<Editor, Long>()
    // scheduled hide timers per editor so we can cancel/reschedule
    private val overlayHideTimers = ConcurrentHashMap<Editor, javax.swing.Timer>()
    // scheduled update timers per editor to refresh elapsed time every second
    private val overlayUpdateTimers = ConcurrentHashMap<Editor, javax.swing.Timer>()
    // per-editor per-task start times so we can show completion durations
    private val overlayTaskStartTimes = ConcurrentHashMap<Editor, ConcurrentHashMap<String, Long>>()
    // minimum time to show overlay for quick runs (ms)
    private val MIN_OVERLAY_MS = 5000L
    
    private val editorFactoryListener = object : EditorFactoryListener {
        override fun editorCreated(event: EditorFactoryEvent) {
            val editor = event.editor
            if (editor.project == project) {
                // Attach document listener for this editor
                attachDocumentListener(editor)
                // don't automatically highlight newly created editors; highlight on focus or change
            }
        }
        
        override fun editorReleased(event: EditorFactoryEvent) {
            val editor = event.editor
            clearHighlighters(editor)
        }
    }

    // Remove overlays/highlighters/timers for all editors except the provided one
    private fun removeAllOverlaysExcept(keep: Editor?) {
        try {
            val editors = EditorFactory.getInstance().allEditors.filter { it.project == project }
            for (ed in editors) {
                if (keep != null && ed === keep) continue
                try {
                    // stop update timer
                    overlayUpdateTimers.remove(ed)?.let { try { it.stop() } catch (_: Exception) {} }
                    // stop hide timer
                    overlayHideTimers.remove(ed)?.let { try { it.stop() } catch (_: Exception) {} }
                    // remove overlay panels
                    overlayPanels[ed]?.let { panel ->
                        try {
                            val rootPane = javax.swing.SwingUtilities.getRootPane(ed.contentComponent)
                            val layered = rootPane?.layeredPane
                            if (layered != null && panel.parent === layered) {
                                layered.remove(panel); layered.repaint()
                            } else {
                                try { ed.contentComponent.remove(panel) } catch (_: Exception) {}
                                try { ed.contentComponent.revalidate(); ed.contentComponent.repaint() } catch (_: Exception) {}
                            }
                        } catch (_: Exception) {}
                    }
                    overlayPanels.remove(ed)
                    overlayShownAt.remove(ed)
                    // remove task overlays
                    overlayTaskPanels.remove(ed)?.forEach { (_id, panel) ->
                        try {
                            val rootPane = javax.swing.SwingUtilities.getRootPane(ed.contentComponent)
                            val layered = rootPane?.layeredPane
                            if (layered != null && panel.parent === layered) {
                                layered.remove(panel); layered.repaint()
                            } else {
                                try { ed.contentComponent.remove(panel) } catch (_: Exception) {}
                                try { ed.contentComponent.revalidate(); ed.contentComponent.repaint() } catch (_: Exception) {}
                            }
                        } catch (_: Exception) {}
                    }
                    overlayTaskStartTimes.remove(ed)
                    // clear highlighters
                    clearHighlighters(ed)
                    // cancel running futures and named running tasks
                    runningFutures.remove(ed)?.forEach { f -> try { f.cancel(true) } catch (_: Exception) {} }
                    runningTasks.remove(ed)?.values?.forEach { f -> try { f.cancel(true) } catch (_: Exception) {} }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }
    
    init {
        // Register listener scoped to project (project is Disposable)
        EditorFactory.getInstance().addEditorFactoryListener(editorFactoryListener, project)

        // Attach document listeners to editors that already exist at init
        try {
            EditorFactory.getInstance().allEditors.filter { it.project == project }.forEach { existing ->
                try { attachDocumentListener(existing) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // Listen to commandFinished events (cut/paste/undo/redo and other editor commands often execute as commands)
        try {
            val connection = ApplicationManager.getApplication().messageBus.connect(project)
            connection.subscribe(CommandListener.TOPIC, object : CommandListener {
                override fun commandFinished(event: CommandEvent) {
                    try {
                        // Only trigger command-based highlight if a recent keyboard event was recorded
                        ApplicationManager.getApplication().invokeLater {
                            try {
                                val fem = FileEditorManager.getInstance(project)
                                val editor = fem.selectedTextEditor
                                if (editor != null && editor.project == project) {
                                    val ts = recentUserEditTimestamps[editor] ?: 0L
                                    if (System.currentTimeMillis() - ts <= 1000L) {
                                        highlightEditorThrottled(editor)
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}
                }
            })
        } catch (_: Exception) {}

        // Highlight only the currently focused (selected) editor at startup
        ApplicationManager.getApplication().invokeLater {
            try {
                val fem = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
                val selected = fem.selectedTextEditor
                if (selected != null) {
                    highlightEditor(selected)
                }
                // Listen to selection changes and highlight only the focused editor
                fem.addFileEditorManagerListener(object : com.intellij.openapi.fileEditor.FileEditorManagerListener {
                    override fun selectionChanged(event: com.intellij.openapi.fileEditor.FileEditorManagerEvent) {
                        try {
                            val editor = fem.selectedTextEditor
                            // Remove overlays/highlighters from all other editors when focus changes
                            removeAllOverlaysExcept(editor)
                            if (editor != null && editor.project == project) {
                                highlightEditor(editor)
                            }
                        } catch (_: Exception) {}
                    }
                })
            } catch (_: Exception) {
                // fallback: no focused editor available
            }
        }
    }
    
    // Публичный метод для обновления подсветки во всех редакторах
    fun refreshAllHighlighting() {
        // println("[DirectHighlighter] Manual refresh requested")
        ApplicationManager.getApplication().invokeLater {
            val editors = EditorFactory.getInstance().allEditors.filter { it.project == project }
            // println("[DirectHighlighter] Found ${editors.size} editors to refresh")
            
            editors.forEach { editor ->
                // println("[DirectHighlighter] Refreshing editor: ${editor.document.hashCode()}")
                // Для настроек принудительно обновляем без throttling
                highlightEditor(editor)
            }
        }
    }

    // Cancel all running tasks and remove overlays for a specific editor
    fun cancelTasksForEditor(editor: Editor) {
        try {
            runningFutures.remove(editor)?.forEach { f -> try { f.cancel(true) } catch (_: Exception) {} }
            runningTasks.remove(editor)?.values?.forEach { f -> try { f.cancel(true) } catch (_: Exception) {} }
            // remove overlays immediately
            ApplicationManager.getApplication().invokeLater {
                try { removeOverlayImmediate(editor) } catch (_: Exception) {}
                try { clearHighlighters(editor) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    // Cancel a named task for an editor (if present) and hide its overlay
    fun cancelTaskById(editor: Editor, taskId: String) {
        try {
            val map = runningTasks[editor] ?: return
            val fut = map.remove(taskId)
            try { fut?.cancel(true) } catch (_: Exception) {}
            // hide overlay for that task
            ApplicationManager.getApplication().invokeLater {
                try { hideTaskOverlay(editor, taskId) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    // Cancel everything across all editors
    fun cancelAllTasks() {
        try {
            val editors = runningTasks.keys.toList()
            for (e in editors) {
                cancelTasksForEditor(e)
            }
            // also clear global overlays
            ApplicationManager.getApplication().invokeLater {
                try { removeAllOverlaysExcept(null) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    override fun dispose() {
        // Cleanup when project/service disposed
        try {
            EditorFactory.getInstance().removeEditorFactoryListener(editorFactoryListener)
        } catch (_: Exception) {
        }
        activeHighlighters.clear()
        lastHighlightTime.clear()
    }

    companion object {
        fun getInstance(project: Project): DirectHighlighterComponent = project.getService(DirectHighlighterComponent::class.java)
    }

    // Throttled highlighting - ограничиваем до 1 раза в N миллисекунд на редактор
    private fun highlightEditorThrottled(editor: Editor) {
        val settings = SimpleSettings.getInstance()
        val throttleDelayMs = settings.throttleDelayMs.toLong()
        val currentTime = System.currentTimeMillis()
        val lastTime = lastHighlightTime[editor] ?: 0
        
        if (currentTime - lastTime >= throttleDelayMs) {
            // println("[DirectHighlighter] Throttled highlight for editor ${editor.document.hashCode()}")
            lastHighlightTime[editor] = currentTime
            highlightEditor(editor)
        } else {
            val remainingTime = throttleDelayMs - (currentTime - lastTime)
            // Show a lightweight overlay immediately so user sees update is pending
            try {
                ApplicationManager.getApplication().invokeLater {
                    try {
                        overlayStartTime[editor] = System.currentTimeMillis()
                        val enabledRules = settings.getEnabledRules()
                        overlayTotalRules[editor] = enabledRules.size
                        overlayCompletedRules[editor] = 0
                        showOrUpdateOverlay(editor, overlayText(editor, inProgress = true))
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}

            // Schedule actual highlight after remaining throttle time
            try {
                val timer = javax.swing.Timer(remainingTime.toInt()) {
                    try {
                        // ensure highlighting runs on EDT-safe path
                        ApplicationManager.getApplication().invokeLater {
                            try {
                                lastHighlightTime[editor] = System.currentTimeMillis()
                                highlightEditor(editor)
                            } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}
                }
                timer.isRepeats = false
                timer.start()
            } catch (_: Exception) {}
        }
    }

    private fun highlightEditor(editor: Editor) {
        try {
            // If a previous run's overlay exists for this editor, remove it immediately
            removeOverlayImmediate(editor)

            // Очищаем старые подсветки (на EDT)
            clearHighlighters(editor)

            // Initialize and show overlay immediately for every start so it updates each run
            try {
                ApplicationManager.getApplication().invokeLater {
                    overlayStartTime[editor] = System.currentTimeMillis()
                    val settings = SimpleSettings.getInstance()
                    val enabledRules = settings.getEnabledRules()
                    overlayTotalRules[editor] = enabledRules.size
                    overlayCompletedRules[editor] = 0
                    showOrUpdateOverlay(editor, overlayText(editor, inProgress = true))
                }
            } catch (_: Exception) {}

            val document = editor.document
            val settings = SimpleSettings.getInstance()

            // bump generation and cancel previous running futures for this editor
            val gen = generationMap.computeIfAbsent(editor) { AtomicInteger(0) }.incrementAndGet()
            // cancel previous running futures for this editor
            runningFutures[editor]?.forEach { future -> try { future.cancel(true) } catch (_: Exception) {} }
            runningFutures[editor] = mutableListOf()
            // cancel named running tasks for this editor
            runningTasks.remove(editor)?.values?.forEach { try { it.cancel(true) } catch (_: Exception) {} }
            runningTasks[editor] = ConcurrentHashMap()

            // Also cancel running tasks for other editors and clean up their overlays/highlighters
            try {
                val others = runningFutures.keys.toList()
                for (other in others) {
                    if (other === editor) continue
                    try {
                        runningFutures[other]?.forEach { f -> try { f.cancel(true) } catch (_: Exception) {} }
                        runningFutures.remove(other)
                        // bump generation for other to mark results stale
                        generationMap.computeIfAbsent(other) { AtomicInteger(0) }.incrementAndGet()
                        // remove overlay and clear visual highlighters for other editor
                        try { removeOverlayImmediate(other) } catch (_: Exception) {}
                        try { clearHighlighters(other) } catch (_: Exception) {}
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}

            // (Не трогаем timestamp throttle здесь — управляется отдельным методом)

            // Запускаем тяжёлую работу в пуле потоков: чтение текста и поиск совпадений
            AppExecutorUtil.getAppExecutorService().execute {
                try {
                    val startTime = System.currentTimeMillis()
                    // Read text and line count in one read action
                    val (text, lineCount) = ApplicationManager.getApplication().runReadAction<Pair<String, Int>> {
                        document.text to document.lineCount
                    }

                    // show overlay: start time and planned rules
                    val enabledRules = settings.getEnabledRules()
                    val totalRules = enabledRules.size
                    ApplicationManager.getApplication().invokeLater {
                        try {
                            overlayStartTime[editor] = System.currentTimeMillis()
                            overlayTotalRules[editor] = totalRules
                            overlayCompletedRules[editor] = 0
                            showOrUpdateOverlay(editor, overlayText(editor, inProgress = true))
                        } catch (_: Exception) {}
                    }

                    // Собираем пары (range, attributes) в фоновом треде
                    val results = mutableListOf<Pair<TextRange, TextAttributes>>()

                    // enabledRules available above

                    // Prepare a text window (first N lines) for condition checks if configured
                    val conditionLinesLimit = settings.conditionSearchLines
                    val conditionWindowText: String? = if (conditionLinesLimit <= 0) {
                        null // null means use whole text
                    } else {
                        substringByLines(text, conditionLinesLimit)
                    }

                    // SPECIAL CASE: if the document does not contain the required key "using _this",
                    // immediately cancel processing, show a red overlay for 5 seconds and finish.
                    try {
                        if (!text.contains("using _this")) {
                            // cancel any running futures / named tasks for this editor
                            runningFutures.remove(editor)?.forEach { f -> try { f.cancel(true) } catch (_: Exception) {} }
                            runningTasks.remove(editor)?.values?.forEach { f -> try { f.cancel(true) } catch (_: Exception) {} }

                            // show a prominent red overlay on EDT and schedule its removal
                            ApplicationManager.getApplication().invokeLater {
                                try {
                                    // show overlay text and tint it red
                                    showOrUpdateOverlay(editor, "Decorator: MISSING 'using _this' — cancelled", inProgress = false)
                                    try {
                                        val panel = overlayPanels[editor]
                                        val lbl = panel?.getClientProperty("overlayLabel") as? JLabel
                                        panel?.background = Color(200, 30, 30, 200)
                                        lbl?.foreground = Color(255, 255, 255)
                                        panel?.revalidate(); panel?.repaint()
                                    } catch (_: Exception) {}

                                    // schedule hide after 5 seconds and clear any highlighters
                                    javax.swing.Timer(5000) {
                                        try {
                                            try { hideOverlay(editor) } catch (_: Exception) {}
                                            try { clearHighlighters(editor) } catch (_: Exception) {}
                                        } catch (_: Exception) {}
                                    }.apply { isRepeats = false; start() }
                                } catch (_: Exception) {}
                            }

                            // stop further processing in this background task
                            return@execute
                        }
                    } catch (_: Exception) {}

                    if (lineCount <= 500) {
                        // Small file: process rules sequentially in this single background task
                        var runCount = 0
                        var matchesFound = 0
                        var ruleIndex = 0
                        for (rule in enabledRules) {
                            val taskId = rule.id.ifEmpty { "rule_${ruleIndex}" }
                            ruleIndex++
                            // show per-task overlay indicating start
                            ApplicationManager.getApplication().invokeLater {
                                try {
                                    showOrUpdateTaskOverlay(editor, taskId, "${rule.name.ifEmpty { rule.targetWord }} — starting")
                                    // record start time for this task
                                    val tm = overlayTaskStartTimes.computeIfAbsent(editor) { ConcurrentHashMap() }
                                    tm[taskId] = System.currentTimeMillis()
                                } catch (_: Exception) {}
                            }
                            val shouldHighlight = if (rule.condition.trim().isEmpty()) {
                                true
                            } else {
                                val hay = conditionWindowText ?: text
                                hay.contains(rule.condition.trim())
                            }

                            if (!shouldHighlight) continue

                            val matches = if (rule.exclusion.trim().isEmpty()) {
                                if (rule.isRegex) {
                                    if (rule.targetWord.startsWith("//")) {
                                        val commentRegex = try { Regex(rule.targetWord) } catch (_: Exception) { null }
                                        if (commentRegex != null) findCommentMatches(text, commentRegex) else emptyList()
                                    } else {
                                        val compiled = try { Regex(rule.targetWord) } catch (_: Exception) { null }
                                        if (compiled != null) findRegexMatches(text, compiled) else emptyList()
                                    }
                                } else {
                                    findAllMatches(text, rule.targetWord)
                                }
                            } else {
                                val allMatches = if (rule.isRegex) {
                                    if (rule.targetWord.startsWith("//")) {
                                        val commentRegex = try { Regex(rule.targetWord) } catch (_: Exception) { null }
                                        if (commentRegex != null) findCommentMatches(text, commentRegex) else emptyList()
                                    } else {
                                        val compiled = try { Regex(rule.targetWord) } catch (_: Exception) { null }
                                        if (compiled != null) findRegexMatches(text, compiled) else emptyList()
                                    }
                                } else {
                                    findAllMatches(text, rule.targetWord)
                                }
                                filterMatchesWithExclusion(text, allMatches, rule.exclusion.trim())
                            }

                            val attributes = createTextAttributes(rule)
                            val thisRuleResults = mutableListOf<Pair<TextRange, TextAttributes>>()
                            for (range in matches) {
                                thisRuleResults.add(range to attributes)
                                results.add(range to attributes)
                                matchesFound++
                            }
                            runCount++
                            val rCount = runCount
                            val mCount = matchesFound
                            // Apply this rule's results immediately on EDT if setting allows
                            ApplicationManager.getApplication().invokeLater {
                                try {
                                    if (SimpleSettings.getInstance().applyPartialOnRuleComplete) {
                                        applyPartialResults(editor, thisRuleResults)
                                    }
                                    overlayCompletedRules[editor] = rCount
                                    showOrUpdateOverlay(editor, overlayText(editor, inProgress = true))
                                    // hide per-task overlay for this rule
                                    try { hideTaskOverlay(editor, taskId) } catch (_: Exception) {}
                                } catch (_: Exception) {}
                            }
                        }
                    } else {
                        // Large file: create per-rule Callables and process in parallel
                        val completedRules = AtomicInteger(0)
                        val matchesFound = AtomicInteger(0)
                        var idx = 0
                        val callables = enabledRules.map { rule ->
                            val taskId = rule.id.ifEmpty { "rule_${idx}" }
                            idx++
                            java.util.concurrent.Callable<List<Pair<TextRange, TextAttributes>>> {
                                val localResults = mutableListOf<Pair<TextRange, TextAttributes>>()

                                val shouldHighlight = if (rule.condition.trim().isEmpty()) {
                                    true
                                } else {
                                    val hay = conditionWindowText ?: text
                                    hay.contains(rule.condition.trim())
                                }

                                if (!shouldHighlight) return@Callable localResults

                                val matches = if (rule.exclusion.trim().isEmpty()) {
                                    if (rule.isRegex) {
                                        if (rule.targetWord.startsWith("//")) {
                                            val commentRegex = try { Regex(rule.targetWord) } catch (_: Exception) { null }
                                            if (commentRegex != null) findCommentMatches(text, commentRegex) else emptyList()
                                        } else {
                                            val compiled = try { Regex(rule.targetWord) } catch (_: Exception) { null }
                                            if (compiled != null) findRegexMatches(text, compiled) else emptyList()
                                        }
                                    } else {
                                        findAllMatches(text, rule.targetWord)
                                    }
                                } else {
                                    val allMatches = if (rule.isRegex) {
                                        if (rule.targetWord.startsWith("//")) {
                                            val commentRegex = try { Regex(rule.targetWord) } catch (_: Exception) { null }
                                            if (commentRegex != null) findCommentMatches(text, commentRegex) else emptyList()
                                        } else {
                                            val compiled = try { Regex(rule.targetWord) } catch (_: Exception) { null }
                                            if (compiled != null) findRegexMatches(text, compiled) else emptyList()
                                        }
                                    } else {
                                        findAllMatches(text, rule.targetWord)
                                    }
                                    filterMatchesWithExclusion(text, allMatches, rule.exclusion.trim())
                                }

                                val attributes = createTextAttributes(rule)
                                for (range in matches) {
                                    localResults.add(range to attributes)
                                }

                                localResults
                                // no changes here; the task returns localResults
                            }
                        }

                        try {
                            // Submit each callable using a CompletionService so we can handle each task as it finishes
                            val executor = AppExecutorUtil.getAppExecutorService()
                            val completionService = java.util.concurrent.ExecutorCompletionService<List<Pair<TextRange, TextAttributes>>>(executor)
                            val futureToTask = ConcurrentHashMap<java.util.concurrent.Future<*>, String>()
                            val submittedFutures = mutableListOf<java.util.concurrent.Future<*>>()

                            // show per-task overlays for each rule (on EDT) before waiting and submit tasks
                            try {
                                var i = 0
                                enabledRules.forEach { r ->
                                    val tid = r.id.ifEmpty { "rule_${i}" }
                                    val callable = java.util.concurrent.Callable<List<Pair<TextRange, TextAttributes>>> {
                                        val localResults = mutableListOf<Pair<TextRange, TextAttributes>>()

                                        val shouldHighlight = if (r.condition.trim().isEmpty()) {
                                            true
                                        } else {
                                            val hay = conditionWindowText ?: text
                                            hay.contains(r.condition.trim())
                                        }

                                        if (!shouldHighlight) return@Callable localResults

                                        val matches = if (r.exclusion.trim().isEmpty()) {
                                            if (r.isRegex) {
                                                if (r.targetWord.startsWith("//")) {
                                                    val commentRegex = try { Regex(r.targetWord) } catch (_: Exception) { null }
                                                    if (commentRegex != null) findCommentMatches(text, commentRegex) else emptyList()
                                                } else {
                                                    val compiled = try { Regex(r.targetWord) } catch (_: Exception) { null }
                                                    if (compiled != null) findRegexMatches(text, compiled) else emptyList()
                                                }
                                            } else {
                                                findAllMatches(text, r.targetWord)
                                            }
                                        } else {
                                            val allMatches = if (r.isRegex) {
                                                if (r.targetWord.startsWith("//")) {
                                                    val commentRegex = try { Regex(r.targetWord) } catch (_: Exception) { null }
                                                    if (commentRegex != null) findCommentMatches(text, commentRegex) else emptyList()
                                                } else {
                                                    val compiled = try { Regex(r.targetWord) } catch (_: Exception) { null }
                                                    if (compiled != null) findRegexMatches(text, compiled) else emptyList()
                                                }
                                            } else {
                                                findAllMatches(text, r.targetWord)
                                            }
                                            filterMatchesWithExclusion(text, allMatches, r.exclusion.trim())
                                        }

                                        val attributes = createTextAttributes(r)
                                        for (range in matches) {
                                            localResults.add(range to attributes)
                                        }
                                        localResults
                                    }

                                    // show overlay and record start time on EDT
                                    try {
                                        ApplicationManager.getApplication().invokeLater {
                                            try {
                                                showOrUpdateTaskOverlay(editor, tid, "${r.name.ifEmpty { r.targetWord }} — running")
                                                val tm = overlayTaskStartTimes.computeIfAbsent(editor) { ConcurrentHashMap() }
                                                tm[tid] = System.currentTimeMillis()
                                            } catch (_: Exception) {}
                                        }
                                    } catch (_: Exception) {}

                                    val future = completionService.submit(callable)
                                    submittedFutures.add(future)
                                    futureToTask[future] = tid
                                    runningFutures.computeIfAbsent(editor) { mutableListOf() }.add(future)
                                    // store named future for cancellation by task id
                                    try {
                                        runningTasks.computeIfAbsent(editor) { ConcurrentHashMap() }[tid] = future
                                    } catch (_: Exception) {}
                                    i++
                                }
                            } catch (_: Exception) {}

                            // Process tasks as they complete
                            var processed = 0
                            while (processed < submittedFutures.size) {
                                try {
                                    val completedFuture = completionService.take()
                                    val taskId = futureToTask.remove(completedFuture) ?: "task_${processed}"
                                    val r = try { completedFuture.get() } catch (e: Exception) { null }

                                    // cleanup registry entries for this completed future
                                    try {
                                        // remove from named tasks map if present
                                        runningTasks[editor]?.remove(taskId)
                                    } catch (_: Exception) {}
                                    try {
                                        runningFutures[editor]?.remove(completedFuture)
                                    } catch (_: Exception) {}

                                    // Handle completion on EDT
                                    ApplicationManager.getApplication().invokeLater {
                                        try {
                                            if (r != null) {
                                                results.addAll(r)
                                                if (SimpleSettings.getInstance().applyPartialOnRuleComplete) {
                                                    applyPartialResults(editor, r)
                                                }
                                                matchesFound.addAndGet(r.size)
                                            }
                                            val done = completedRules.incrementAndGet()
                                            overlayCompletedRules[editor] = done
                                            overlayTotalRules[editor] = totalRules
                                            showOrUpdateOverlay(editor, overlayText(editor, inProgress = true))

                                            // Show completed state for this specific task
                                            try {
                                                val startMap = overlayTaskStartTimes[editor]
                                                val start = startMap?.remove(taskId) ?: 0L
                                                val elapsedSec = if (start > 0) ((System.currentTimeMillis() - start) / 1000) else -1L
                                                val labelText = if (elapsedSec >= 0) {
                                                    "${taskId}: completed — ${elapsedSec}s"
                                                } else {
                                                    "${taskId}: completed"
                                                }
                                                val panel = overlayTaskPanels[editor]?.get(taskId)
                                                if (panel != null) {
                                                    val lbl = panel.getClientProperty("overlayLabel") as? JLabel
                                                    lbl?.text = labelText
                                                    lbl?.foreground = Color(0, 200, 0)
                                                    panel.revalidate(); panel.repaint()
                                                }
                                            } catch (_: Exception) {}

                                            // schedule removal after MIN_OVERLAY_MS
                                            try {
                                                javax.swing.Timer(MIN_OVERLAY_MS.toInt()) {
                                                    try { hideTaskOverlay(editor, taskId) } catch (_: Exception) {}
                                                }.apply { isRepeats = false; start() }
                                            } catch (_: Exception) {}
                                        } catch (_: Exception) {}
                                    }
                                } catch (_: Exception) {
                                    // ignore and continue
                                } finally {
                                    processed++
                                }
                            }
                        } catch (_: Exception) {
                            // If invokeAll itself fails, fall back to sequential processing
                            var runCount = 0
                            var matchesFoundSequential = 0
                            for (rule in enabledRules) {
                                val shouldHighlight = if (rule.condition.trim().isEmpty()) {
                                    true
                                } else {
                                    text.contains(rule.condition.trim())
                                }
                                if (!shouldHighlight) continue
                                val matches = if (rule.isRegex) {
                                    val compiled = try { Regex(rule.targetWord) } catch (_: Exception) { null }
                                    if (compiled != null) findRegexMatches(text, compiled) else emptyList()
                                } else {
                                    findAllMatches(text, rule.targetWord)
                                }
                                val attributes = createTextAttributes(rule)
                                for (range in matches) {
                                    results.add(range to attributes)
                                    matchesFoundSequential++
                                }
                                runCount++
                                val rCount = runCount
                                val mCount = matchesFoundSequential
                                ApplicationManager.getApplication().invokeLater {
                                    try { showOrUpdateOverlay(editor, "Start: ${LocalTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME)} | rules run: $rCount/${totalRules} | matches: $mCount") } catch (_: Exception) {}
                                }
                            }
                        }
                    }

                    // Вернёмся на EDT и применим подсветки
                    val currentGen = generationMap[editor]?.get() ?: gen
                    ApplicationManager.getApplication().invokeLater {
                        try {
                            // If generation changed or editor closed, skip applying results
                            val latestGen = generationMap[editor]?.get() ?: -1
                            if (latestGen != currentGen) return@invokeLater
                            val stillOpen = EditorFactory.getInstance().allEditors.any { it === editor }
                            if (!stillOpen) return@invokeLater

                            // Ещё раз очистим старые подсветки и применим новые
                            clearHighlighters(editor)
                            val markupModel = editor.markupModel
                            val highlighters = mutableListOf<RangeHighlighter>()

                            for ((range, attributes) in results) {
                                val highlighter = markupModel.addRangeHighlighter(
                                    range.startOffset, range.endOffset,
                                    HighlighterLayer.SELECTION - 1,
                                    attributes,
                                    com.intellij.openapi.editor.markup.HighlighterTargetArea.EXACT_RANGE
                                )
                                highlighters.add(highlighter)
                            }

                            activeHighlighters[editor] = highlighters
                            // show duration and final stats
                            val durationMs = System.currentTimeMillis() - startTime
                            try {
                                overlayCompletedRules[editor] = overlayTotalRules[editor] ?: overlayCompletedRules[editor] ?: 0
                                showOrUpdateOverlay(editor, overlayText(editor, inProgress = false))
                            } catch (_: Exception) {}
                            // hide overlay after 5 seconds
                            javax.swing.Timer(5000) { hideOverlay(editor) }.apply { isRepeats = false; start() }
                        } catch (e: Exception) {
                            // suppressed
                        }
                    }

                } catch (e: Exception) {
                    // suppressed
                }
            }

        } catch (e: Exception) {
            // println("[DirectHighlighter] Error scheduling highlighting: ${e.message}")
        }
    }
    
    private fun createTextAttributes(rule: HighlightRule): TextAttributes {
        return TextAttributes().apply {
            // Обрабатываем цвет фона
            if (rule.backgroundColor.isNotEmpty()) {
                try {
                    backgroundColor = parseColor(rule.backgroundColor)
                    // println("[DirectHighlighter] Set background color '${rule.backgroundColor}' -> ${backgroundColor}")
                } catch (e: Exception) {
                    backgroundColor = null
                    // println("[DirectHighlighter] Invalid background color '${rule.backgroundColor}', using transparent")
                }
            }
            
            // Обрабатываем цвет текста
            if (rule.foregroundColor.isNotEmpty()) {
                try {
                    foregroundColor = parseColor(rule.foregroundColor)
                    // println("[DirectHighlighter] Set foreground color '${rule.foregroundColor}' -> ${foregroundColor}")
                } catch (e: Exception) {
                    foregroundColor = null
                    // println("[DirectHighlighter] Invalid foreground color '${rule.foregroundColor}', using default")
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
            // println("[DirectHighlighter] Failed to parse RGBA color '$rgba': ${e.message}")
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
            // println("[DirectHighlighter] Finding comments with pattern: '$commentPattern'")
            
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
                    // println("[DirectHighlighter] Found comment match: '$commentText' at $commentStart-$commentEnd")
                }
            }
            
            // println("[DirectHighlighter] Comment pattern '$commentPattern' found ${matches.size} matches")
        } catch (e: Exception) {
            // println("[DirectHighlighter] Invalid comment pattern '$commentPattern': ${e.message}")
            // e.printStackTrace()
        }
        return matches
    }

    // Overload: accept precompiled Regex for comment matching
    private fun findCommentMatches(text: String, commentRegex: Regex): List<TextRange> {
        val matches = mutableListOf<TextRange>()
        try {
            val searchResults = commentRegex.findAll(text)
            for (res in searchResults) {
                matches.add(TextRange(res.range.first, res.range.last + 1))
            }
        } catch (e: Exception) {
            // suppressed
        }
        return matches
    }

    private fun findRegexMatches(text: String, regexPattern: String): List<TextRange> {
        val matches = mutableListOf<TextRange>()
        try {
            // println("[DirectHighlighter] Testing regex pattern: '$regexPattern'")
            
            val regex = Regex(regexPattern)
            val matchResults = regex.findAll(text)
            
            for (matchResult in matchResults) {
                val matchText = text.substring(matchResult.range.first, matchResult.range.last + 1)
                matches.add(TextRange(matchResult.range.first, matchResult.range.last + 1))
                // println("[DirectHighlighter] Found match: '$matchText' at ${matchResult.range.first}-${matchResult.range.last + 1}")
            }
            
            // println("[DirectHighlighter] Regex '$regexPattern' found ${matches.size} matches")
        } catch (e: Exception) {
            // println("[DirectHighlighter] Invalid regex '$regexPattern': ${e.message}")
            // e.printStackTrace()
        }
        return matches
    }

    // Overload: accept precompiled Regex to avoid compiling repeatedly
    private fun findRegexMatches(text: String, regex: Regex): List<TextRange> {
        val matches = mutableListOf<TextRange>()
        try {
            val matchResults = regex.findAll(text)
            for (matchResult in matchResults) {
                matches.add(TextRange(matchResult.range.first, matchResult.range.last + 1))
            }
        } catch (e: Exception) {
            // suppressed
        }
        return matches
    }

    // Return substring consisting of first N lines (or whole text if N >= line count)
    private fun substringByLines(text: String, linesLimit: Int): String {
        if (linesLimit <= 0) return text
        var linesTaken = 0
        var pos = 0
        val len = text.length
        while (pos < len && linesTaken < linesLimit) {
            val next = text.indexOf('\n', pos)
            if (next == -1) {
                // remaining text is one line
                return text.substring(0, len)
            } else {
                pos = next + 1
                linesTaken++
            }
        }
        return if (pos >= len) text else text.substring(0, pos)
    }

    // --- Editor overlay helpers ---
    private fun showOrUpdateOverlay(editor: Editor, text: String, inProgress: Boolean = false) {
        try {
            // ensure metadata exists so overlayText can compute elapsed/time
            overlayStartTime.computeIfAbsent(editor) { System.currentTimeMillis() }
            overlayTotalRules.computeIfAbsent(editor) { 0 }
            overlayCompletedRules.computeIfAbsent(editor) { 0 }

            val existing = overlayPanels[editor]
            if (existing != null) {
                val label = existing.getClientProperty("overlayLabel") as? JLabel
                // always refresh label so new starts update elapsed/time
                label?.text = text
                existing.revalidate()
                existing.repaint()
                // (re)start or stop periodic update based on inProgress
                try {
                    overlayUpdateTimers.remove(editor)?.let { t -> try { t.stop() } catch (_: Exception) {} }
                    if (inProgress) {
                        val timer = javax.swing.Timer(1000) {
                            try {
                                ApplicationManager.getApplication().invokeLater {
                                    try {
                                        val lbl = existing.getClientProperty("overlayLabel") as? JLabel
                                        lbl?.text = overlayText(editor, inProgress = true)
                                    } catch (_: Exception) {}
                                }
                            } catch (_: Exception) {}
                        }
                        timer.isRepeats = true
                        timer.start()
                        overlayUpdateTimers[editor] = timer
                    }
                } catch (_: Exception) {}
                return
            }

            // create a small semi-transparent panel with text
            val panel = JPanel()
            panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
            panel.isOpaque = true
            panel.background = Color(0, 0, 0, 160)
            panel.border = EmptyBorder(6, 10, 6, 10)
            val label = JLabel(text)
            label.foreground = Color(230, 230, 230)
            panel.add(label)
            panel.putClientProperty("overlayLabel", label)

            // try to attach to the window layered pane so overlay floats above editor
            try {
                val editorComp = editor.contentComponent
                val rootPane = javax.swing.SwingUtilities.getRootPane(editorComp)
                val layered = rootPane?.layeredPane
                    if (layered != null) {
                    // compute position: top-right of editor component (10px padding)
                    val editorPos = javax.swing.SwingUtilities.convertPoint(editorComp, 0, 0, layered)
                    // prefer preferred size for panel
                    panel.doLayout()
                    val pref = panel.preferredSize
                    // shift left by ~100px and down by ~30px as requested
                    var x = editorPos.x + editorComp.width - pref.width - 10 - 100
                    if (x < 0) x = 0
                    val y = editorPos.y + 10 + 30
                    panel.setBounds(x, y, pref.width, pref.height)
                    layered.add(panel, javax.swing.JLayeredPane.POPUP_LAYER)
                    layered.repaint()
                    overlayPanels[editor] = panel
                    overlayShownAt[editor] = System.currentTimeMillis()
                    // start periodic update if requested (inProgress); caller controls this via inProgress flag
                    try {
                        overlayUpdateTimers.remove(editor)?.let { t -> try { t.stop() } catch (_: Exception) {} }
                        if (inProgress) {
                            val timer = javax.swing.Timer(1000) {
                                try {
                                    ApplicationManager.getApplication().invokeLater {
                                        try {
                                            val p = overlayPanels[editor]
                                            val lbl = p?.getClientProperty("overlayLabel") as? JLabel
                                            lbl?.text = overlayText(editor, inProgress = true)
                                        } catch (_: Exception) {}
                                    }
                                } catch (_: Exception) {}
                            }
                            timer.isRepeats = true
                            timer.start()
                            overlayUpdateTimers[editor] = timer
                        }
                    } catch (_: Exception) {}
                    return
                }
            } catch (e: Exception) {
                // fallback to attaching directly to editor component below
            }

            // fallback: attach directly to editor's content component
            val comp = editor.contentComponent
            comp.add(panel)
            comp.revalidate()
            comp.repaint()
            overlayPanels[editor] = panel
            overlayShownAt[editor] = System.currentTimeMillis()
        } catch (e: Exception) {
            // ignore overlay failures
        }
    }

    // Show or update a small per-task overlay (identified by taskId)
    private fun showOrUpdateTaskOverlay(editor: Editor, taskId: String, text: String) {
        try {
            val taskMap = overlayTaskPanels.computeIfAbsent(editor) { ConcurrentHashMap() }
            val existing = taskMap[taskId]
            if (existing != null) {
                val label = existing.getClientProperty("overlayLabel") as? JLabel
                label?.text = text
                existing.revalidate(); existing.repaint()
                return
            }

            val panel = JPanel()
            panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
            panel.isOpaque = true
            panel.background = Color(0, 0, 0, 160)
            panel.border = EmptyBorder(4, 8, 4, 8)
            val label = JLabel(text)
            label.foreground = Color(230, 230, 230)
            panel.add(label)
            panel.putClientProperty("overlayLabel", label)

            try {
                val editorComp = editor.contentComponent
                val rootPane = javax.swing.SwingUtilities.getRootPane(editorComp)
                val layered = rootPane?.layeredPane
                if (layered != null) {
                    // stack per-task overlays in top-right, offset by count
                    val existingCount = taskMap.size
                    val editorPos = javax.swing.SwingUtilities.convertPoint(editorComp, 0, 0, layered)
                    panel.doLayout()
                    val pref = panel.preferredSize
                    val x = editorPos.x + editorComp.width - pref.width - 10
                    val y = editorPos.y + 10 + existingCount * (pref.height + 6)
                            panel.setBounds(x, y, pref.width, pref.height)
                    layered.add(panel, javax.swing.JLayeredPane.POPUP_LAYER)
                    layered.repaint()
                    taskMap[taskId] = panel
                    return
                }
            } catch (_: Exception) {}

            // fallback: attach to editor component
            val comp = editor.contentComponent
            // attach with shifted position on fallback: add to comp but set location
            try {
                panel.doLayout()
                val pref = panel.preferredSize
                val x = (comp.width - pref.width - 10 - 100).coerceAtLeast(0)
                val y = 10 + 30 + taskMap.size * (pref.height + 6)
                panel.setBounds(x, y, pref.width, pref.height)
                comp.add(panel)
                comp.revalidate(); comp.repaint()
            } catch (_: Exception) {
                comp.add(panel); comp.revalidate(); comp.repaint()
            }
            taskMap[taskId] = panel
        } catch (_: Exception) {}
    }

    private fun hideTaskOverlay(editor: Editor, taskId: String) {
        try {
            val taskMap = overlayTaskPanels[editor] ?: return
            val panel = taskMap.remove(taskId) ?: return
            try {
                val rootPane = javax.swing.SwingUtilities.getRootPane(editor.contentComponent)
                val layered = rootPane?.layeredPane
                if (layered != null && panel.parent === layered) {
                    layered.remove(panel); layered.repaint()
                } else {
                    try { editor.contentComponent.remove(panel) } catch (_: Exception) {}
                    try { editor.contentComponent.revalidate(); editor.contentComponent.repaint() } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    private fun hideOverlay(editor: Editor) {
        try {
            // Cancel any previously scheduled hide timer
            overlayHideTimers.remove(editor)?.let { t ->
                try { t.stop() } catch (_: Exception) {}
            }

            val panel = overlayPanels[editor]
            if (panel == null) return

            val shownAt = overlayShownAt[editor] ?: 0L
            val elapsed = System.currentTimeMillis() - shownAt
            if (elapsed < MIN_OVERLAY_MS) {
                // schedule hide for remaining time
                val remaining = (MIN_OVERLAY_MS - elapsed).coerceAtLeast(0L).toInt()
                val timer = javax.swing.Timer(remaining) {
                    try {
                        // remove now on EDT
                        ApplicationManager.getApplication().invokeLater {
                            try {
                                // remove from layered pane if attached, else from editor component
                                val rootPane = javax.swing.SwingUtilities.getRootPane(editor.contentComponent)
                                val layered = rootPane?.layeredPane
                                if (layered != null && panel.parent === layered) {
                                    layered.remove(panel)
                                    layered.repaint()
                                } else {
                                    try { editor.contentComponent.remove(panel) } catch (_: Exception) {}
                                    try { editor.contentComponent.revalidate(); editor.contentComponent.repaint() } catch (_: Exception) {}
                                }
                                overlayPanels.remove(editor)
                                overlayShownAt.remove(editor)
                                overlayHideTimers.remove(editor)
                            } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}
                }
                timer.isRepeats = false
                overlayHideTimers[editor] = timer
                timer.start()
                return
            }

            // Enough time elapsed — remove immediately
            try {
                // stop periodic update timer if present
                overlayUpdateTimers.remove(editor)?.let { try { it.stop() } catch (_: Exception) {} }
                val rootPane = javax.swing.SwingUtilities.getRootPane(editor.contentComponent)
                val layered = rootPane?.layeredPane
                if (layered != null && panel.parent === layered) {
                    layered.remove(panel)
                    layered.repaint()
                } else {
                    editor.contentComponent.remove(panel)
                    editor.contentComponent.revalidate(); editor.contentComponent.repaint()
                }
            } catch (_: Exception) {}
            overlayPanels.remove(editor)
            overlayShownAt.remove(editor)
        } catch (_: Exception) {
        }
    }

    // Remove overlay for editor immediately (no min-time checks)
    private fun removeOverlayImmediate(editor: Editor) {
        try {
            overlayHideTimers.remove(editor)?.let { t -> try { t.stop() } catch (_: Exception) {} }
            overlayUpdateTimers.remove(editor)?.let { t -> try { t.stop() } catch (_: Exception) {} }
            val panel = overlayPanels[editor]
            if (panel != null) {
                try {
                    val rootPane = javax.swing.SwingUtilities.getRootPane(editor.contentComponent)
                    val layered = rootPane?.layeredPane
                    if (layered != null && panel.parent === layered) {
                        layered.remove(panel)
                        layered.repaint()
                    } else {
                        try { editor.contentComponent.remove(panel) } catch (_: Exception) {}
                        try { editor.contentComponent.revalidate(); editor.contentComponent.repaint() } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
            }
            overlayPanels.remove(editor)
            overlayShownAt.remove(editor)
            // also remove task overlays and stop their timers
            overlayTaskPanels.remove(editor)?.forEach { (_id, panel) ->
                try {
                    val rootPane = javax.swing.SwingUtilities.getRootPane(editor.contentComponent)
                    val layered = rootPane?.layeredPane
                    if (layered != null && panel.parent === layered) {
                        layered.remove(panel); layered.repaint()
                    } else {
                        try { editor.contentComponent.remove(panel) } catch (_: Exception) {}
                        try { editor.contentComponent.revalidate(); editor.contentComponent.repaint() } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
            }
            overlayTaskStartTimes.remove(editor)
        } catch (_: Exception) {}
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
                // println("[DirectHighlighter] Excluded match at ${match.startOffset} because line contains '$exclusion'")
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

    // Attach a DocumentListener to editor's document in an idempotent way
    private fun attachDocumentListener(editor: Editor) {
        try {
            val doc = editor.document
            // Use a single listener instance per document (store as user data?)
            // Simpler: just add a listener that triggers throttled highlighting
            doc.addDocumentListener(object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    try {
                        // For many edit types (typing, paste, delete) this will fire.
                        // Optional debug: log when document changes to help verify paste triggers
                        try {
                            val dbg = SimpleSettings.getInstance().debugEvents
                            if (dbg) println("[DirectHighlighter] documentChanged for editor ${editor.document.hashCode()}")
                        } catch (_: Exception) {}

                        // Use invokeLater so highlighting scheduling runs on EDT-safe context.
                        // Only trigger highlight if a recent user edit (keyboard) was recorded
                        ApplicationManager.getApplication().invokeLater {
                            try {
                                val ts = recentUserEditTimestamps[editor] ?: 0L
                                if (System.currentTimeMillis() - ts <= 1000L) {
                                    highlightEditorThrottled(editor)
                                } else {
                                    // ignore document changes not caused by relevant keys/mouse
                                }
                            } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}
                }
            })

            // Attach KeyAdapter to capture relevant key presses (del, backspace, enter, tab, ctrl+v, ctrl+x, ctrl+z, ctrl+y)
            try {
                val comp = editor.contentComponent
                // Avoid adding multiple listeners
                val existing = comp.getClientProperty("__decorator_key_listener_added__") as? Boolean
                if (existing != true) {
                    comp.putClientProperty("__decorator_key_listener_added__", true)
                    comp.addKeyListener(object : KeyAdapter() {
                        override fun keyPressed(e: KeyEvent) {
                            try {
                                val isCtrl = (e.modifiersEx and KeyEvent.CTRL_DOWN_MASK) != 0
                                val relevant = when (e.keyCode) {
                                    KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE, KeyEvent.VK_ENTER, KeyEvent.VK_TAB -> true
                                    KeyEvent.VK_V, KeyEvent.VK_X, KeyEvent.VK_Z, KeyEvent.VK_Y -> isCtrl
                                    else -> false
                                }
                                if (relevant) {
                                    recentUserEditTimestamps[editor] = System.currentTimeMillis()
                                }
                            } catch (_: Exception) {}
                        }
                    })
                }
            } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    // Apply a set of partial results immediately (EDT)
    private fun applyPartialResults(editor: Editor, partial: List<Pair<TextRange, TextAttributes>>) {
        try {
            if (partial.isEmpty()) return
            val markupModel = editor.markupModel
            val highlighters = activeHighlighters.computeIfAbsent(editor) { mutableListOf() }
            for ((range, attributes) in partial) {
                try {
                    val highlighter = markupModel.addRangeHighlighter(
                        range.startOffset, range.endOffset,
                        HighlighterLayer.SELECTION - 1,
                        attributes,
                        com.intellij.openapi.editor.markup.HighlighterTargetArea.EXACT_RANGE
                    )
                    highlighters.add(highlighter)
                } catch (_: Exception) {
                    // skip individual add failures
                }
            }
            activeHighlighters[editor] = highlighters
        } catch (_: Exception) {
        }
    }

    private fun overlayText(editor: Editor, inProgress: Boolean): String {
        val start = overlayStartTime[editor] ?: System.currentTimeMillis()
        val elapsedMs = System.currentTimeMillis() - start
        val elapsed = "${elapsedMs}ms"
        val total = overlayTotalRules[editor] ?: 0
        val done = overlayCompletedRules[editor] ?: 0
        val state = if (inProgress) "InProgress" else "Finished"
        return "Decorator: $state | Time: $elapsed | Task $done/$total ${if (inProgress) "[InProgress]" else "[Completed]"}"
    }
}