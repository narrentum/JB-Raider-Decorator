package com.codedecorator.rider.minimal

import com.intellij.openapi.project.ProjectManager
import java.awt.*
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

class CodeDecoratorSettings {
    private val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(CodeDecoratorSettings::class.java)
    private val mainPanel: JPanel = JPanel(BorderLayout())
    private var tableModel: DefaultTableModel? = null
    private var table: JTable? = null
    private var throttleField: JSpinner? = null
    private var conditionLinesField: JSpinner? = null
    private var wholeFileCheck: JCheckBox? = null
    // remember last positive value so we can restore when unchecking
    private var lastPositiveConditionLines: Int = 200

    init {
    // println("[DEBUG] CodeDecoratorSettings constructor called")
        createUI()
    // println("[DEBUG] CodeDecoratorSettings constructor finished")
    }

    private fun createUI() {
        // println("[DEBUG] Starting createUI()")
        
        // Create table model with full 10 columns to match the primary RulesTableModel
        val columnNames = arrayOf(
            "Enabled",
            "Name",
            "Target Word",
            "IsRegex",
            "Condition",
            "Exclusion",
            "Background Color",
            "Foreground Color",
            "Font Style",
            "Text Decoration"
        )
        tableModel = object : DefaultTableModel(columnNames, 0) {
            override fun getColumnClass(columnIndex: Int): Class<*> {
                return when (columnIndex) {
                    0, 3 -> java.lang.Boolean::class.java  // Enabled and IsRegex - Boolean columns
                    else -> java.lang.String::class.java
                }
            }
        }
        
        // Load actual rules from settings
        loadRulesFromSettings()
        
        // println("[DEBUG] Rules loaded: ${tableModel!!.rowCount} rows")
        
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
        
        // Create throttle settings panel
        val throttlePanel = createThrottleSettingsPanel()
        
        // Combine title and throttle settings into north panel
        val northPanel = JPanel(BorderLayout())
        northPanel.add(titleLabel, BorderLayout.NORTH)
        northPanel.add(throttlePanel, BorderLayout.SOUTH)
        
        // Layout
        mainPanel.add(northPanel, BorderLayout.NORTH)
        mainPanel.add(scrollPane, BorderLayout.CENTER)
        mainPanel.add(buttonPanel, BorderLayout.SOUTH)
        
        // Add padding
        mainPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        
        // println("[DEBUG] UI creation complete with full functionality")
    }

    private fun loadRulesFromSettings() {
        val settings = SimpleSettings.getInstance()
        // println("[DEBUG] Loading ${settings.rules.size} rules from settings")
        
        tableModel!!.setRowCount(0) // Clear existing rows
        
    for (rule in settings.rules) {
            try {
        // println("[DEBUG] Loading rule: ${rule.name}, enabled=${rule.enabled}")
                tableModel!!.addRow(arrayOf(
                    rule.enabled,
                    rule.name,
                    rule.targetWord,
                    rule.isRegex,
                    rule.condition,
                    rule.exclusion,
                    rule.backgroundColor,
                    rule.foregroundColor,
                    rule.fontStyle.displayName,
                    rule.textDecoration.displayName
                ))
            } catch (e: Exception) {
                // println("[ERROR] Failed to load rule: ${e.message}")
                // Add safe fallback row using available properties
                tableModel!!.addRow(arrayOf(
                    true,
                    rule.name,
                    rule.targetWord,
                    false,
                    rule.condition,
                    rule.exclusion,
                    "#FFFFFF",
                    "#000000",
                    "Нормальный",
                    "Нет"
                ))
            }
        }
        
        // println("[DEBUG] Table now has ${tableModel!!.rowCount} rows")
    }

    private fun createThrottleSettingsPanel(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 5))
        panel.border = BorderFactory.createTitledBorder("Performance Settings")
        
        val throttleLabel = JLabel("Highlight update delay (ms):")
        
        // Create spinner with model for throttle delay (50ms to 60000ms = 1 minute)
        val throttleModel = SpinnerNumberModel(5000, 50, 60000, 250)
        throttleField = JSpinner(throttleModel)
        throttleField!!.preferredSize = Dimension(80, 25)
        
        // Add tooltip
        throttleField!!.toolTipText = "Delay between highlight updates. Higher values reduce CPU usage but slower response."
        throttleLabel.toolTipText = "Minimum time between highlight updates to prevent excessive CPU usage"
        
        // Load current value from settings
        val settings = SimpleSettings.getInstance()
        throttleField!!.value = settings.throttleDelayMs
        
        val helpLabel = JLabel("(50ms - 60000ms, recommended: 1000-5000ms)")
        helpLabel.font = helpLabel.font.deriveFont(Font.ITALIC, helpLabel.font.size - 1f)
        helpLabel.foreground = Color.GRAY
        
        panel.add(throttleLabel)
        panel.add(throttleField!!)
    // Condition search lines spinner
    val condLabel = JLabel("Lines to search for rule conditions:")
        // Allow -1 to represent "search whole file" (per product requirement)
        val condModel = SpinnerNumberModel(200, -1, 5000, 10)
        conditionLinesField = JSpinner(condModel)
    conditionLinesField!!.preferredSize = Dimension(80, 25)
    conditionLinesField!!.toolTipText = "Only search for rule.condition within the first N lines of the file. Use checkbox or set -1 to search whole file."
    // Load current value
        val curVal = settings.conditionSearchLines
        // initialize checkbox and spinner state
        wholeFileCheck = JCheckBox("Search whole file")
        wholeFileCheck!!.toolTipText = "When checked, condition search will scan the whole document (spinner will be -1)."
        if (curVal <= 0) {
            // whole-file mode
            wholeFileCheck!!.isSelected = true
            conditionLinesField!!.value = -1
            conditionLinesField!!.isEnabled = false
        } else {
            wholeFileCheck!!.isSelected = false
            conditionLinesField!!.value = curVal
            conditionLinesField!!.isEnabled = true
            lastPositiveConditionLines = curVal
        }

        wholeFileCheck!!.addActionListener {
            val sel = wholeFileCheck!!.isSelected
            if (sel) {
                // set spinner to -1 and disable
                conditionLinesField!!.value = -1
                conditionLinesField!!.isEnabled = false
            } else {
                // restore last positive or default
                val restore = if (lastPositiveConditionLines > 0) lastPositiveConditionLines else 200
                conditionLinesField!!.value = restore
                conditionLinesField!!.isEnabled = true
            }
        }

        // remember last positive value when spinner changes
        conditionLinesField!!.addChangeListener(javax.swing.event.ChangeListener {
            val valInt = (conditionLinesField!!.value as? Int) ?: -1
            if (valInt > 0) {
                lastPositiveConditionLines = valInt
            }
        })

        panel.add(condLabel)
        panel.add(conditionLinesField!!)
        panel.add(wholeFileCheck!!)
        panel.add(helpLabel)
        
        return panel
    }

    fun getPanel(): JComponent {
        // println("[DEBUG] getPanel() called. MainPanel component count: ${mainPanel.componentCount}")
        // for (i in 0 until mainPanel.componentCount) {
        //     val component = mainPanel.getComponent(i)
        //     println("[DEBUG] Component $i: ${component.javaClass.simpleName}")
        // }
        return mainPanel
    }

    fun getPreferredFocusedComponent(): JComponent? {
        return table
    }

    fun isModified(settings: SimpleSettings): Boolean {
        // Check throttle delay setting
        val throttleValue = throttleField?.value as? Int ?: settings.throttleDelayMs
        if (throttleValue != settings.throttleDelayMs) {
            return true
        }
        val condValue = conditionLinesField?.value as? Int ?: settings.conditionSearchLines
        if (condValue != settings.conditionSearchLines) return true
        
        // Check if row count differs
        if (tableModel!!.rowCount != settings.rules.size) {
            return true
        }
        
        // Check if any rule data differs (column mapping: 0=Enabled,1=Name,2=Target,3=IsRegex,4=Condition,5=Exclusion,
        // 6=Background,7=Foreground,8=FontStyle,9=TextDecoration)
        for (i in 0 until tableModel!!.rowCount) {
            val rule = settings.rules[i]
            if (tableModel!!.getValueAt(i, 0) != rule.enabled ||
                tableModel!!.getValueAt(i, 1) != rule.name ||
                tableModel!!.getValueAt(i, 2) != rule.targetWord ||
                tableModel!!.getValueAt(i, 3) != rule.isRegex ||
                tableModel!!.getValueAt(i, 4) != rule.condition ||
                tableModel!!.getValueAt(i, 5) != rule.exclusion ||
                tableModel!!.getValueAt(i, 6) != rule.backgroundColor ||
                tableModel!!.getValueAt(i, 7) != rule.foregroundColor ||
                tableModel!!.getValueAt(i, 8) != rule.fontStyle.displayName ||
                tableModel!!.getValueAt(i, 9) != rule.textDecoration.displayName) {
                return true
            }
        }
        return false
    }

    fun apply(settings: SimpleSettings) {
        // println("[DEBUG] Apply called - saving ${tableModel!!.rowCount} rules")
        
        // Save throttle delay setting
        val throttleValue = throttleField?.value as? Int ?: settings.throttleDelayMs
        settings.throttleDelayMs = throttleValue
    val condValue = conditionLinesField?.value as? Int ?: settings.conditionSearchLines
    settings.conditionSearchLines = condValue
        // println("[DEBUG] Applied throttle delay: ${throttleValue}ms")
        
        applyTableToSettings(settings)
        refreshAllProjectHighlighting()
        try {
            LOG.info("[Settings] Applied ${settings.rules.size} rules and refreshed highlighting")
        } catch (_: Exception) {}
    }

    fun reset(settings: SimpleSettings) {
        // println("[DEBUG] Reset called")
        
        // Reset throttle field to current settings value
        throttleField?.value = settings.throttleDelayMs
        val curVal = settings.conditionSearchLines
        if (curVal <= 0) {
            wholeFileCheck?.isSelected = true
            conditionLinesField?.value = -1
            conditionLinesField?.isEnabled = false
        } else {
            wholeFileCheck?.isSelected = false
            conditionLinesField?.value = curVal
            conditionLinesField?.isEnabled = true
            lastPositiveConditionLines = curVal
        }
        // println("[DEBUG] Reset throttle delay to: ${settings.throttleDelayMs}ms")
        
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

    // Column 4: Condition - text
    columnModel.getColumn(4).preferredWidth = 200

    // Column 5: Exclusion - text
    columnModel.getColumn(5).preferredWidth = 200
        
        // Column 8: Font Style - dropdown
        val fontStyleCombo = JComboBox(arrayOf("Нормальный", "Жирный", "Курсив", "Жирный курсив"))
        columnModel.getColumn(8).cellEditor = DefaultCellEditor(fontStyleCombo)
        columnModel.getColumn(8).preferredWidth = 120

        // Column 9: Text Decoration - dropdown
        val textDecorationCombo = JComboBox(arrayOf("Нет", "Подчеркивание", "Зачеркивание"))
        columnModel.getColumn(9).cellEditor = DefaultCellEditor(textDecorationCombo)
        columnModel.getColumn(9).preferredWidth = 120
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
                "",
                "",
                "#FFFFFF",
                "#000000",
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
        // println("[DEBUG] Converting ${tableModel!!.rowCount} table rows to rules")
        
        for (i in 0 until tableModel!!.rowCount) {
            try {
                val enabled = tableModel!!.getValueAt(i, 0) as? Boolean ?: true
                val name = tableModel!!.getValueAt(i, 1) as? String ?: "Rule"
                val targetWord = tableModel!!.getValueAt(i, 2) as? String ?: "pattern"
                val isRegex = tableModel!!.getValueAt(i, 3) as? Boolean ?: false
                val condition = tableModel!!.getValueAt(i, 4) as? String ?: ""
                val exclusion = tableModel!!.getValueAt(i, 5) as? String ?: ""
                val backgroundColor = tableModel!!.getValueAt(i, 6) as? String ?: "#FFFFFF"
                val foregroundColor = tableModel!!.getValueAt(i, 7) as? String ?: "#000000"
                val fontStyleStr = tableModel!!.getValueAt(i, 8) as? String ?: "Нормальный"
                val textDecorationStr = tableModel!!.getValueAt(i, 9) as? String ?: "Нет"
                
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
                    condition = condition,
                    exclusion = exclusion,
                    backgroundColor = backgroundColor,
                    foregroundColor = foregroundColor,
                    fontStyle = fontStyle,
                    textDecoration = textDecoration,
                    enabled = enabled
                )
                
                settings.rules.add(rule)
                
            } catch (e: Exception) {
                // println("[ERROR] Failed to apply row $i: ${e.message}")
            }
        }
    }

    private fun refreshAllProjectHighlighting() {
        val projects = ProjectManager.getInstance().openProjects
        for (project in projects) {
            val highlighterComponent = try {
                DirectHighlighterComponent.getInstance(project)
            } catch (_: Exception) {
                null
            }
            highlighterComponent?.refreshAllHighlighting()
        }
    }
}