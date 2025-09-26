package com.codedecorator.rider.minimal

import com.intellij.openapi.project.ProjectManager
import java.awt.*
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

class SimpleCodeHighlighterSettingsComponent {
    private val mainPanel: JPanel = JPanel(BorderLayout())
    private var tableModel: DefaultTableModel? = null
    private var table: JTable? = null
    private var throttleField: JSpinner? = null

    init {
        println("[DEBUG] SimpleCodeHighlighterSettingsComponent constructor called")
        createUI()
        println("[DEBUG] SimpleCodeHighlighterSettingsComponent constructor finished")
    }

    private fun createUI() {
        println("[DEBUG] Starting createUI()")
        
        // Create table model with all 8 columns
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
        tableModel = object : DefaultTableModel(columnNames, 0) {
            override fun getColumnClass(columnIndex: Int): Class<*> {
                return when (columnIndex) {
                    0, 3 -> java.lang.Boolean::class.java  // Enabled и IsRegex - Boolean columns
                    else -> java.lang.String::class.java
                }
            }
        }
        
        // Load actual rules from settings
        loadRulesFromSettings()
        
        println("[DEBUG] Rules loaded: ${tableModel!!.rowCount} rows")
        
        // Create table
        table = JTable(tableModel)
        table!!.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table!!.preferredScrollableViewportSize = Dimension(800, 250)
        
        // Set up column renderers and editors
        setupTableColumns()
        
        // Create scroll pane
        val scrollPane = JScrollPane(table)
        
        // Create buttons
        val buttonPanel = createButtonPanel()
        
        // Create simple title
        val titleLabel = JLabel("CoDecorator Rules")
        titleLabel.font = titleLabel.font.deriveFont(16f)
        titleLabel.horizontalAlignment = SwingConstants.CENTER
        
        // Layout
        mainPanel.add(titleLabel, BorderLayout.NORTH)
        mainPanel.add(scrollPane, BorderLayout.CENTER)
        mainPanel.add(buttonPanel, BorderLayout.SOUTH)
        
        // Add padding
        mainPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        
        println("[DEBUG] UI creation complete with full functionality")
    }

    private fun loadRulesFromSettings() {
        val settings = SimpleSettings.getInstance()
        println("[DEBUG] Loading ${settings.rules.size} rules from settings")
        
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
                    rule.fontStyle.displayName,
                    rule.textDecoration.displayName
                ))
            } catch (e: Exception) {
                println("[ERROR] Failed to load rule $index: ${e.message}")
                // Add safe fallback row
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
        
        println("[DEBUG] Table now has ${tableModel!!.rowCount} rows")
    }

    fun getPanel(): JComponent {
        println("[DEBUG] getPanel() called. MainPanel component count: ${mainPanel.componentCount}")
        for (i in 0 until mainPanel.componentCount) {
            val component = mainPanel.getComponent(i)
            println("[DEBUG] Component $i: ${component.javaClass.simpleName}")
        }
        return mainPanel
    }

    fun getPreferredFocusedComponent(): JComponent? {
        return table
    }

    fun isModified(settings: SimpleSettings): Boolean {
        // Check if row count differs
        if (tableModel!!.rowCount != settings.rules.size) {
            return true
        }
        
        // Check if any rule data differs
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
        println("[DEBUG] Apply called - saving ${tableModel!!.rowCount} rules")
        applyTableToSettings(settings)
        refreshAllProjectHighlighting()
    }

    fun reset(settings: SimpleSettings) {
        println("[DEBUG] Reset called")
        loadRulesFromSettings()
    }

    private fun setupTableColumns() {
        val columnModel = table!!.columnModel
        
        // Column 0: Enabled - checkbox
        columnModel.getColumn(0).cellRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean,
                row: Int, column: Int
            ): java.awt.Component {
                val checkbox = JCheckBox()
                checkbox.isSelected = value as? Boolean ?: false
                checkbox.horizontalAlignment = JCheckBox.CENTER
                checkbox.isOpaque = true
                if (isSelected) {
                    checkbox.background = table.selectionBackground
                } else {
                    checkbox.background = table.background
                }
                return checkbox
            }
        }
        val enabledEditor = JCheckBox()
        enabledEditor.horizontalAlignment = JCheckBox.CENTER
        columnModel.getColumn(0).cellEditor = DefaultCellEditor(enabledEditor)
        columnModel.getColumn(0).preferredWidth = 60
        
        // Column 3: IsRegex - checkbox
        columnModel.getColumn(3).cellRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean,
                row: Int, column: Int
            ): java.awt.Component {
                val checkbox = JCheckBox()
                checkbox.isSelected = value as? Boolean ?: false
                checkbox.horizontalAlignment = JCheckBox.CENTER
                checkbox.isOpaque = true
                if (isSelected) {
                    checkbox.background = table.selectionBackground
                } else {
                    checkbox.background = table.background
                }
                return checkbox
            }
        }
        val regexEditor = JCheckBox()
        regexEditor.horizontalAlignment = JCheckBox.CENTER
        columnModel.getColumn(3).cellEditor = DefaultCellEditor(regexEditor)
        columnModel.getColumn(3).preferredWidth = 60
        
        // Column 6: Font Style - dropdown
        val fontStyleCombo = JComboBox(arrayOf("Нормальный", "Жирный", "Курсив", "Жирный курсив"))
        columnModel.getColumn(6).cellEditor = DefaultCellEditor(fontStyleCombo)
        columnModel.getColumn(6).preferredWidth = 120
        
        // Column 7: Text Decoration - dropdown
        val textDecorationCombo = JComboBox(arrayOf("Нет", "Подчеркивание", "Зачеркивание"))
        columnModel.getColumn(7).cellEditor = DefaultCellEditor(textDecorationCombo)
        columnModel.getColumn(7).preferredWidth = 120
    }

    private fun createButtonPanel(): JPanel {
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
                "Нормальный",
                "Нет"
            ))
        }
        
        removeButton.addActionListener {
            val selectedRow = table!!.selectedRow
            if (selectedRow >= 0) {
                tableModel!!.removeRow(selectedRow)
            }
        }
        
        resetButton.addActionListener {
            val settings = SimpleSettings.getInstance()
            settings.rules.clear()
            settings.rules.addAll(HighlightRule.createDefault())
            loadRulesFromSettings()
            refreshAllProjectHighlighting()
        }
        
        buttonPanel.add(addButton)
        buttonPanel.add(removeButton)
        buttonPanel.add(resetButton)
        
        return buttonPanel
    }

    private fun applyTableToSettings(settings: SimpleSettings) {
        settings.rules.clear()
        println("[DEBUG] Converting ${tableModel!!.rowCount} table rows to rules")
        
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
                    "Жирный курсив" -> HighlightRule.FontStyle.BOLD_ITALIC
                    else -> HighlightRule.FontStyle.NORMAL
                }
                
                val textDecoration = when (textDecorationStr) {
                    "Подчеркивание" -> HighlightRule.TextDecoration.UNDERLINE
                    "Зачеркивание" -> HighlightRule.TextDecoration.STRIKETHROUGH
                    else -> HighlightRule.TextDecoration.NONE
                }
                
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
        val projects = ProjectManager.getInstance().openProjects
        for (project in projects) {
            val highlighterComponent = project.getComponent(DirectHighlighterComponent::class.java)
            highlighterComponent?.refreshAllHighlighting()
        }
    }
}