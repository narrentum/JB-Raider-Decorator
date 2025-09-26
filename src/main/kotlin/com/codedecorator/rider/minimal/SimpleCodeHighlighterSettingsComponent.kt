package com.codedecorator.rider.minimal

import com.intellij.openapi.project.ProjectManager
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*
import javax.swing.event.TableModelListener
import javax.swing.table.DefaultTableModel
import javax.swing.table.DefaultTableCellRenderer

class SimpleCodeHighlighterSettingsComponent {

    private val mainPanel: JPanel = JPanel(BorderLayout())
    private var table: JTable? = null
    private var tableModel: DefaultTableModel? = null
    private var throttleField: JSpinner? = null

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
        tableModel = object : DefaultTableModel(columnNames, 0) {
            override fun getColumnClass(columnIndex: Int): Class<*> {
                return when (columnIndex) {
                    0 -> Boolean::class.java  // Enabled column
                    3 -> Boolean::class.java  // IsRegex column
                    else -> String::class.java
                }
            }
            
            override fun isCellEditable(row: Int, column: Int): Boolean {
                return true // All cells are editable
            }
        }
        
        // Load actual rules from settings instead of hardcoded data
        loadRulesFromSettings()
        
        // Create table
        table = JTable(tableModel)
        table!!.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table!!.preferredScrollableViewportSize = Dimension(800, 250) // Увеличили для большего количества столбцов
        
        // Set up column renderers and editors
        val columnModel = table!!.columnModel
        
        // Column 0: Enabled - checkbox (both renderer and editor)
        val enabledCheckbox = JCheckBox()
        enabledCheckbox.horizontalAlignment = JCheckBox.CENTER
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
        
        // Column 3: IsRegex - checkbox (both renderer and editor)
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
        
        // Create top panel with title and throttle settings
        val topPanel = JPanel(BorderLayout())
        
        // Add title
        val titleLabel = JLabel("CoDecorator Rules")
        titleLabel.font = titleLabel.font.deriveFont(14f)
        topPanel.add(titleLabel, BorderLayout.WEST)
        
        // Add throttle settings
        val throttlePanel = JPanel()
        throttlePanel.add(JLabel("Update delay (ms):"))
        throttleField = JSpinner(SpinnerNumberModel(5000, 100, 60000, 100))
        throttleField!!.preferredSize = Dimension(100, 25)
        throttlePanel.add(throttleField)
        topPanel.add(throttlePanel, BorderLayout.EAST)
        
        // Layout
        mainPanel.add(topPanel, BorderLayout.NORTH)
        mainPanel.add(scrollPane, BorderLayout.CENTER)
        mainPanel.add(buttonPanel, BorderLayout.SOUTH)
        
        // Add padding
        mainPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
    }
    
    private fun loadRulesFromSettings() {
        val settings = SimpleSettings.getInstance()
        println("[DEBUG] Loading ${settings.rules.size} rules into table")
        
        // Загружаем значение throttle в UI
        throttleField!!.value = settings.throttleDelayMs
        
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
        // Сохранить настройку throttle
        settings.throttleDelayMs = throttleField!!.value as Int
        refreshAllProjectHighlighting()
    }
    
    private fun applyTableToSettings(settings: SimpleSettings) {
        // Convert table data back to HighlightRule objects
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
                
                println("[DEBUG] Row $i: enabled=${enabled} (${tableModel!!.getValueAt(i, 0)?.javaClass?.simpleName})")
                
                // Convert string values back to enums
                val fontStyle = when (fontStyleStr) {
                    "Жирный" -> HighlightRule.FontStyle.BOLD
                    "Курсив" -> HighlightRule.FontStyle.ITALIC
                    "Жирный курсив" -> HighlightRule.FontStyle.BOLD_ITALIC
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
        // Загрузить настройку throttle
        throttleField!!.value = settings.throttleDelayMs
    }
}