package com.codedecorator.rider.minimal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import java.awt.Color
import javax.swing.*
import javax.swing.border.EmptyBorder
import java.util.concurrent.ConcurrentHashMap
// duplicate package declaration removed

class OverlayManager(private val project: com.intellij.openapi.project.Project) {
    private val MIN_OVERLAY_MS = 5000L
    private val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(OverlayManager::class.java)

    private val overlayPanels = ConcurrentHashMap<Editor, JPanel>()
    private val overlayTaskPanels = ConcurrentHashMap<Editor, ConcurrentHashMap<String, JPanel>>()
    private val overlayShownAt = ConcurrentHashMap<Editor, Long>()
    private val overlayStartTime = ConcurrentHashMap<Editor, Long>()
    private val overlayTotalRules = ConcurrentHashMap<Editor, Int>()
    private val overlayCompletedRules = ConcurrentHashMap<Editor, Int>()
    private val overlayHideTimers = ConcurrentHashMap<Editor, javax.swing.Timer>()
    private val overlayUpdateTimers = ConcurrentHashMap<Editor, javax.swing.Timer>()
    private val overlayTaskStartTimes = ConcurrentHashMap<Editor, ConcurrentHashMap<String, Long>>()

    fun prepareOverlay(editor: Editor) {
        val settings = SimpleSettings.getInstance()
        val enabledRules = settings.getEnabledRules()
        prepareOverlay(editor, enabledRules.size)
    }

    fun prepareOverlay(editor: Editor, totalRules: Int) {
        try {
            ApplicationManager.getApplication().invokeLater {
                try {
                    overlayStartTime[editor] = System.currentTimeMillis()
                    overlayTotalRules[editor] = totalRules
                    overlayCompletedRules[editor] = 0
                    showOrUpdateOverlay(editor, overlayText(editor, inProgress = true), inProgress = true)
                } catch (ex: Exception) { LOG.warn("OverlayManager: prepareOverlay.invokeLater inner", ex) }
            }
        } catch (ex: Exception) { LOG.warn("OverlayManager: prepareOverlay outer", ex) }
    }

    fun removeAllForEditor(editor: Editor) {
        try {
            overlayUpdateTimers.remove(editor)?.let { try { it.stop() } catch (ex: Exception) { LOG.warn("OverlayManager: stop update timer failed", ex) } }
            overlayHideTimers.remove(editor)?.let { try { it.stop() } catch (ex: Exception) { LOG.warn("OverlayManager: stop hide timer failed", ex) } }

            overlayPanels.remove(editor)?.let { panel -> try { _removePanel(panel, editor) } catch (ex: Exception) { LOG.warn("OverlayManager: remove panel failed", ex) } }
            overlayShownAt.remove(editor)

            overlayTaskPanels.remove(editor)?.forEach { entry -> try { _removePanel(entry.value, editor) } catch (ex: Exception) { LOG.warn("OverlayManager: remove task panel failed", ex) } }
            overlayTaskStartTimes.remove(editor)
        } catch (ex: Exception) { LOG.warn("OverlayManager: removeAllForEditor failed", ex) }
    }

    fun removeAllOverlaysExcept(editor: Editor?) {
        try {
            // stop update timer
            overlayUpdateTimers.remove(editor)?.let {
                try { it.stop() } catch (ex: Exception) { LOG.warn("OverlayManager: stop update timer failed", ex) }
            }

            // stop hide timer
            overlayHideTimers.remove(editor)?.let {
                try { it.stop() } catch (ex: Exception) { LOG.warn("OverlayManager: stop hide timer failed", ex) }
            }
        } catch (ex: Exception) { LOG.warn("OverlayManager: removeAllOverlaysExcept failed", ex) }
    }

    fun removeOverlayImmediate(editor: Editor) {
        try {
            overlayHideTimers.remove(editor)?.let { try { it.stop() } catch (ex: Exception) { LOG.warn("OverlayManager: stop hide timer failed", ex) } }
            overlayUpdateTimers.remove(editor)?.let { try { it.stop() } catch (ex: Exception) { LOG.warn("OverlayManager: stop update timer failed", ex) } }

            overlayPanels.remove(editor)?.let { panel -> try { _removePanel(panel, editor) } catch (ex: Exception) { LOG.warn("OverlayManager: remove panel failed", ex) } }
            overlayShownAt.remove(editor)

            overlayTaskPanels.remove(editor)?.forEach { entry -> try { _removePanel(entry.value, editor) } catch (ex: Exception) { LOG.warn("OverlayManager: remove task panel failed", ex) } }
            overlayTaskStartTimes.remove(editor)
        } catch (ex: Exception) { LOG.warn("OverlayManager: removeOverlayImmediate failed", ex) }
    }

    fun showOrUpdateTaskOverlay(editor: Editor, taskId: String, text: String) {
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
                    val tm = overlayTaskStartTimes.computeIfAbsent(editor) { ConcurrentHashMap() }
                    tm[taskId] = System.currentTimeMillis()
                    return
                }
            } catch (ex: Exception) { LOG.warn("OverlayManager: showOrUpdateTaskOverlay layered add failed", ex) }

            // fallback
            val comp = editor.contentComponent
            try {
                panel.doLayout()
                val pref = panel.preferredSize
                val x = (comp.width - pref.width - 10 - 100).coerceAtLeast(0)
                val y = 10 + 30 + taskMap.size * (pref.height + 6)
                panel.setBounds(x, y, pref.width, pref.height)
                comp.add(panel)
                comp.revalidate(); comp.repaint()
            } catch (ex: Exception) { LOG.warn("OverlayManager: showOrUpdateTaskOverlay fallback add failed, adding to comp", ex); comp.add(panel); comp.revalidate(); comp.repaint() }
            taskMap[taskId] = panel
            val tm = overlayTaskStartTimes.computeIfAbsent(editor) { ConcurrentHashMap() }
            tm[taskId] = System.currentTimeMillis()
        } catch (ex: Exception) { LOG.warn("OverlayManager: showOrUpdateTaskOverlay failed", ex) }
    }

    fun showOrUpdateOverlay(editor: Editor, text: String, inProgress: Boolean = false) {
        try {
            overlayStartTime.computeIfAbsent(editor) { System.currentTimeMillis() }
            overlayTotalRules.computeIfAbsent(editor) { 0 }
            overlayCompletedRules.computeIfAbsent(editor) { 0 }

            val existing = overlayPanels[editor]
            if (existing != null) {
                val label = existing.getClientProperty("overlayLabel") as? JLabel
                label?.text = text
                existing.revalidate(); existing.repaint()
                try {
                    overlayUpdateTimers.remove(editor)?.let { t -> try { t.stop() } catch (ex: Exception) { LOG.warn("OverlayManager: stop update timer failed", ex) } }
                    if (inProgress) {
                        val timer = javax.swing.Timer(1000) {
                            try {
                                ApplicationManager.getApplication().invokeLater {
                                    try {
                                        val p = overlayPanels[editor]
                                        val lbl = p?.getClientProperty("overlayLabel") as? JLabel
                                        lbl?.text = overlayText(editor, inProgress = true)
                                    } catch (ex: Exception) { LOG.warn("OverlayManager: update overlay label failed", ex) }
                                }
                            } catch (ex: Exception) { LOG.warn("OverlayManager: invokeLater failed when updating overlay label", ex) }
                        }
                        timer.isRepeats = true
                        timer.start()
                        overlayUpdateTimers[editor] = timer
                    }
                } catch (ex: Exception) { LOG.warn("OverlayManager: updating existing overlay failed", ex) }
                return
            }

            val panel = JPanel()
            panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
            panel.isOpaque = true
            panel.background = Color(0, 0, 0, 160)
            panel.border = EmptyBorder(6, 10, 6, 10)
            val label = JLabel(text)
            label.foreground = Color(230, 230, 230)
            panel.add(label)
            panel.putClientProperty("overlayLabel", label)

            try {
                val editorComp = editor.contentComponent
                val rootPane = javax.swing.SwingUtilities.getRootPane(editorComp)
                val layered = rootPane?.layeredPane
                if (layered != null) {
                    val editorPos = javax.swing.SwingUtilities.convertPoint(editorComp, 0, 0, layered)
                    panel.doLayout()
                    val pref = panel.preferredSize
                    var x = editorPos.x + editorComp.width - pref.width - 10 - 100
                    if (x < 0) x = 0
                    val y = editorPos.y + 10 + 30
                    panel.setBounds(x, y, pref.width, pref.height)
                    layered.add(panel, javax.swing.JLayeredPane.POPUP_LAYER)
                    layered.repaint()
                    overlayPanels[editor] = panel
                    overlayShownAt[editor] = System.currentTimeMillis()
                    try {
                        overlayUpdateTimers.remove(editor)?.let { t -> try { t.stop() } catch (ex: Exception) { LOG.warn("OverlayManager: stop update timer failed", ex) } }
                        if (inProgress) {
                            val timer = javax.swing.Timer(1000) {
                                try {
                                    ApplicationManager.getApplication().invokeLater {
                                        try {
                                            val p = overlayPanels[editor]
                                            val lbl = p?.getClientProperty("overlayLabel") as? JLabel
                                            lbl?.text = overlayText(editor, inProgress = true)
                                        } catch (ex: Exception) { LOG.warn("OverlayManager: update overlay label failed", ex) }
                                    }
                                } catch (ex: Exception) { LOG.warn("OverlayManager: invokeLater failed when updating overlay label", ex) }
                            }
                            timer.isRepeats = true
                            timer.start()
                            overlayUpdateTimers[editor] = timer
                        }
                    } catch (ex: Exception) { LOG.warn("OverlayManager: layered overlay setup failed", ex) }
                    return
                }
            } catch (e: Exception) { LOG.warn("OverlayManager: layered overlay path failed", e) }

            val comp = editor.contentComponent
            try {
                comp.add(panel)
                comp.revalidate(); comp.repaint()
                overlayPanels[editor] = panel
                overlayShownAt[editor] = System.currentTimeMillis()
            } catch (ex: Exception) { LOG.warn("OverlayManager: add overlay to comp failed", ex) }
        } catch (ex: Exception) { LOG.warn("OverlayManager: showOrUpdateOverlay failed", ex) }
    }

    fun hideTaskOverlay(editor: Editor) {
        try {
            overlayTaskPanels.remove(editor)?.forEach { entry -> try { _removePanel(entry.value, editor) } catch (ex: Exception) { LOG.warn("OverlayManager: hideTaskOverlay remove panel failed", ex) } }
            overlayTaskStartTimes.remove(editor)
        } catch (ex: Exception) { LOG.warn("OverlayManager: hideTaskOverlay failed", ex) }
    }

    fun hideTaskOverlay(editor: Editor, taskId: String) {
        try {
            val taskMap = overlayTaskPanels[editor] ?: return
            val panel = taskMap.remove(taskId) ?: return

            try {
                val rootPane = javax.swing.SwingUtilities.getRootPane(editor.contentComponent)
                val layered = rootPane?.layeredPane

                if (layered != null && panel.parent === layered) {
                    layered.remove(panel); layered.repaint()
                } else {
                    try { editor.contentComponent.remove(panel) } catch (ex: Exception) { LOG.warn("OverlayManager: remove panel from comp failed", ex) }
                    try { editor.contentComponent.revalidate(); editor.contentComponent.repaint() } catch (ex: Exception) { LOG.warn("OverlayManager: revalidate/repaint failed", ex) }
                }
            } catch (ex: Exception) { LOG.warn("OverlayManager: hideTaskOverlay inner failed", ex) }
        } catch (ex: Exception) { LOG.warn("OverlayManager: hideTaskOverlay outer failed", ex) }
    }

    fun hideOverlay(editor: Editor) {
        try {
            overlayHideTimers.remove(editor)?.let { t -> try { t.stop() } catch (ex: Exception) { LOG.warn("OverlayManager: stop hide timer failed", ex) } }

            val panel = overlayPanels[editor] ?: return

            val shownAt = overlayShownAt[editor] ?: 0L
            val elapsed = System.currentTimeMillis() - shownAt
            if (elapsed < MIN_OVERLAY_MS) {
                val remaining = (MIN_OVERLAY_MS - elapsed).coerceAtLeast(0L).toInt()
                val timer = javax.swing.Timer(remaining) {
                    try {
                        ApplicationManager.getApplication().invokeLater {
                            try {
                                val rootPane = javax.swing.SwingUtilities.getRootPane(editor.contentComponent)
                                val layered = rootPane?.layeredPane
                                if (layered != null && panel.parent === layered) {
                                    layered.remove(panel); layered.repaint()
                                } else {
                                    try { editor.contentComponent.remove(panel) } catch (ex: Exception) { LOG.warn("OverlayManager: remove panel from comp failed", ex) }
                                    try { editor.contentComponent.revalidate(); editor.contentComponent.repaint() } catch (ex: Exception) { LOG.warn("OverlayManager: revalidate/repaint failed", ex) }
                                }
                                overlayPanels.remove(editor)
                                overlayShownAt.remove(editor)
                                overlayHideTimers.remove(editor)
                            } catch (ex: Exception) { LOG.warn("OverlayManager: invokeLater inner failed", ex) }
                        }
                    } catch (ex: Exception) { LOG.warn("OverlayManager: invokeLater failed when hiding overlay", ex) }
                }
                timer.isRepeats = false
                overlayHideTimers[editor] = timer
                timer.start()
                return
            }

            try {
                overlayUpdateTimers.remove(editor)?.let { t -> try { t.stop() } catch (ex: Exception) { LOG.warn("OverlayManager: stop update timer failed", ex) } }
                val rootPane = javax.swing.SwingUtilities.getRootPane(editor.contentComponent)
                val layered = rootPane?.layeredPane
                if (layered != null && panel.parent === layered) {
                    layered.remove(panel); layered.repaint()
                } else {
                    editor.contentComponent.remove(panel)
                    editor.contentComponent.revalidate(); editor.contentComponent.repaint()
                }
            } catch (ex: Exception) { LOG.warn("OverlayManager: hideOverlay remove failed", ex) }
            overlayPanels.remove(editor)
            overlayShownAt.remove(editor)
        } catch (ex: Exception) { LOG.warn("OverlayManager: hideOverlay failed", ex) }
    }

    fun setCompleted(editor: Editor, value: Int) { overlayCompletedRules[editor] = value }
    fun setTotalRules(editor: Editor, value: Int) { overlayTotalRules[editor] = value }
    fun setCompletedToTotal(editor: Editor) { overlayCompletedRules[editor] = overlayTotalRules[editor] ?: overlayCompletedRules[editor] ?: 0 }
    fun incrementCompleted(editor: Editor): Int {
        val prev = overlayCompletedRules[editor] ?: 0
        val next = prev + 1
        overlayCompletedRules[editor] = next
        return next
    }
    fun getCompleted(editor: Editor): Int = overlayCompletedRules[editor] ?: 0
    fun getTotal(editor: Editor): Int = overlayTotalRules[editor] ?: 0

    private fun overlayText(editor: Editor, inProgress: Boolean): String {
        val startTime = overlayStartTime[editor] ?: 0L
        val totalRules = overlayTotalRules[editor] ?: 0
        val completedRules = overlayCompletedRules[editor] ?: 0

        val elapsedMs = if (startTime > 0) System.currentTimeMillis() - startTime else 0L
        val elapsedSec = elapsedMs / 1000
        val elapsedMin = elapsedSec / 60
        val elapsedDisplay = if (elapsedMin > 0) "$elapsedMin min ${elapsedSec % 60} sec" else "$elapsedSec sec"

        return if (inProgress) {
            "Highlighting... ($completedRules/$totalRules rules, $elapsedDisplay)"
        } else {
            "Highlighting complete ($completedRules/$totalRules rules, $elapsedDisplay)"
        }
    }

    private fun _removePanel(panel: JPanel, editor: Editor) {
        try {
            val rootPane = javax.swing.SwingUtilities.getRootPane(editor.contentComponent)
            val layered = rootPane?.layeredPane
            if (layered != null && panel.parent === layered) {
                layered.remove(panel); layered.repaint()
            } else {
                try { editor.contentComponent.remove(panel) } catch (ex: Exception) { LOG.warn("OverlayManager: remove panel from comp failed", ex) }
                try { editor.contentComponent.revalidate(); editor.contentComponent.repaint() } catch (ex: Exception) { LOG.warn("OverlayManager: revalidate/repaint failed", ex) }
            }
        } catch (ex: Exception) { LOG.warn("OverlayManager: _removePanel failed", ex) }
    }
}
