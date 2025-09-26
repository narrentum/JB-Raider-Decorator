package com.codedecorator.rider.minimal

import com.intellij.openapi.project.ProjectManager
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*
import javax.swing.event.TableModelListener
import javax.swing.table.DefaultTableModel

class SimpleCodeHighlighterSettingsComponent {

    private val mainPanel: JPanel = JPanel(BorderLayout())
    private var table: JTable? = null
    private var tableModel: DefaultTableModel? = null

    init {
        createUI()
    }

    private fun createUI() {
        // Create table model with all important columns
        val columnNames = arrayOf(
            "Enabled", 
            "Name", 
            "Target Word", 
            "IsRegex",
            "Foreground Color", 
            "Background Color",
            "Font Style",
            "Text Decoration"
        )
        tableModel = DefaultTableModel(columnNames, 0)
        
        // Load actual rules from settings instead of hardcoded data
        loadRulesFromSettings()
        
        // Create table
        table = JTable(tableModel)
        table!!.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table!!.preferredScrollableViewportSize = Dimension(800, 250) // Увеличили для большего количества столбцов
        
        // Remove automatic table listener - only apply on manual Apply button press
        // TableModelListener was causing cycles and clearing all rules
        
        // Create scroll pane
        val scrollPane = JScrollPane(table)
        
        // Create buttons
        val buttonPanel = JPanel()
        val addButton = JButton("Add Rule")
        val removeButton = JButton("Remove Rule")
        val resetButton = JButton("Reset to Defaults")
        
        addButton.addActionListener {
            tableModel!!.addRow(arrayOf(
                true, 
                "New Rule", 
                "pattern", 
                false,
                "#000000", 
                "#FFFFFF",
                "Нормальный", // String вместо enum
                "Нет"        // String вместо enum
            ))
        }
        
        removeButton.addActionListener {
            val selectedRow = table!!.selectedRow
            if (selectedRow >= 0) {
                tableModel!!.removeRow(selectedRow)
            }
        }
        
        resetButton.addActionListener {
            // Reset to default rules
            val settings = SimpleSettings.getInstance()
            settings.rules.clear()
            settings.rules.addAll(HighlightRule.createDefault())
            loadRulesFromSettings()
            
            // Immediately refresh highlighting
            refreshAllProjectHighlighting()
        }
        
        buttonPanel.add(addButton)
        buttonPanel.add(removeButton)
        buttonPanel.add(resetButton)
        
        // Add title
        val titleLabel = JLabel("Simple Code Highlighter Rules")
        titleLabel.font = titleLabel.font.deriveFont(14f)
        
        // Layout
        mainPanel.add(titleLabel, BorderLayout.NORTH)
        mainPanel.add(scrollPane, BorderLayout.CENTER)
        mainPanel.add(buttonPanel, BorderLayout.SOUTH)
        
        // Add padding
        mainPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
    }
    
    private fun loadRulesFromSettings() {
        val settings = SimpleSettings.getInstance()
        println("[DEBUG] Loading ${settings.rules.size} rules into table")
        
        // Temporarily disable table listener to avoid cycles
        val listeners = tableModel!!.tableModelListeners
        for (listener in listeners) {
            tableModel!!.removeTableModelListener(listener)
        }
        
        tableModel!!.setRowCount(0) // Clear existing rows
        
        for ((index, rule) in settings.rules.withIndex()) {
            try {
                println("[DEBUG] Loading rule $index: ${rule.name}, enabled=${rule.enabled}")
                tableModel!!.addRow(arrayOf(
                    rule.enabled,
                    rule.name,
                    rule.targetWord,
                    rule.isRegex,
                    rule.foregroundColor,
                    rule.backgroundColor,
                    rule.fontStyle.displayName, // Используем displayName вместо enum
                    rule.textDecoration.displayName // Используем displayName вместо enum
                ))
            } catch (e: Exception) {
                println("[ERROR] Failed to load rule $index: ${e.message}")
                // Добавляем безопасную строку с базовыми значениями
                tableModel!!.addRow(arrayOf(
                    true,
                    rule.name ?: "Unknown Rule",
                    rule.targetWord ?: "pattern",
                    false,
                    "#000000",
                    "#FFFFFF",
                    "Нормальный",
                    "Нет"
                ))
            }
        }
        
        // Re-add listeners
        for (listener in listeners) {
            tableModel!!.addTableModelListener(listener)
        }
        
        println("[DEBUG] Table now has ${tableModel!!.rowCount} rows")
    }

    fun getPanel(): JComponent {
        return mainPanel
    }

    fun getPreferredFocusedComponent(): JComponent? {
        return table
    }

    fun isModified(settings: SimpleSettings): Boolean {
        // Simple check - if row count differs, it's modified
        if (tableModel!!.rowCount != settings.rules.size) {
            return true
        }
        
        // Check if any rule data differs (all 8 columns)
        for (i in 0 until tableModel!!.rowCount) {
            val rule = settings.rules[i]
            if (tableModel!!.getValueAt(i, 0) != rule.enabled ||
                tableModel!!.getValueAt(i, 1) != rule.name ||
                tableModel!!.getValueAt(i, 2) != rule.targetWord ||
                tableModel!!.getValueAt(i, 3) != rule.isRegex ||
                tableModel!!.getValueAt(i, 4) != rule.foregroundColor ||
                tableModel!!.getValueAt(i, 5) != rule.backgroundColor ||
                tableModel!!.getValueAt(i, 6) != rule.fontStyle.displayName ||
                tableModel!!.getValueAt(i, 7) != rule.textDecoration.displayName) {
                return true
            }
        }
        return false
    }

    fun apply(settings: SimpleSettings) {
        applyTableToSettings(settings)
        refreshAllProjectHighlighting()
    }
    
    private fun applyTableToSettings(settings: SimpleSettings) {
        // Convert table data back to HighlightRule objects
        settings.rules.clear()
        
        for (i in 0 until tableModel!!.rowCount) {
            try {
                val enabled = tableModel!!.getValueAt(i, 0) as? Boolean ?: true
                val name = tableModel!!.getValueAt(i, 1) as? String ?: "Rule"
                val targetWord = tableModel!!.getValueAt(i, 2) as? String ?: "pattern"
                val isRegex = tableModel!!.getValueAt(i, 3) as? Boolean ?: false
                val foregroundColor = tableModel!!.getValueAt(i, 4) as? String ?: "#000000"
                val backgroundColor = tableModel!!.getValueAt(i, 5) as? String ?: "#FFFFFF"
                val fontStyleStr = tableModel!!.getValueAt(i, 6) as? String ?: "Нормальный"
                val textDecorationStr = tableModel!!.getValueAt(i, 7) as? String ?: "Нет"
                
                // Convert string values back to enums
                val fontStyle = when (fontStyleStr) {
                    "Жирный" -> HighlightRule.FontStyle.BOLD
                    "Курсив" -> HighlightRule.FontStyle.ITALIC
                    else -> HighlightRule.FontStyle.NORMAL
                }
                
                val textDecoration = when (textDecorationStr) {
                    "Подчёркивание" -> HighlightRule.TextDecoration.UNDERLINE
                    "Зачёркивание" -> HighlightRule.TextDecoration.STRIKETHROUGH
                    else -> HighlightRule.TextDecoration.NONE
                }
                
                // Create new rule with all table data
                val rule = HighlightRule(
                    id = "rule_$i",
                    name = name,
                    targetWord = targetWord,
                    isRegex = isRegex,
                    condition = "",
                    exclusion = "",
                    backgroundColor = backgroundColor,
                    foregroundColor = foregroundColor,
                    fontStyle = fontStyle,
                    textDecoration = textDecoration,
                    enabled = enabled
                )
                
                settings.rules.add(rule)
                
            } catch (e: Exception) {
                println("[ERROR] Failed to apply row $i: ${e.message}")
            }
        }
    }
    
    private fun refreshAllProjectHighlighting() {
        // Get all open projects and refresh highlighting
        val projects = ProjectManager.getInstance().openProjects
        for (project in projects) {
            val highlighterComponent = project.getComponent(DirectHighlighterComponent::class.java)
            highlighterComponent?.refreshAllHighlighting()
        }
    }

    fun reset(settings: SimpleSettings) {
        // Load actual settings into table
        loadRulesFromSettings()
    }
}