package com.codedecorator.rider.minimal

import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*
import javax.swing.table.DefaultTableModel

class SimpleCodeHighlighterSettingsComponent {

    private val mainPanel: JPanel = JPanel(BorderLayout())
    private var table: JTable? = null
    private var tableModel: DefaultTableModel? = null

    init {
        createUI()
    }

    private fun createUI() {
        // Create simple table model
        val columnNames = arrayOf("Enabled", "Name", "Target Word", "Foreground Color", "Background Color")
        tableModel = DefaultTableModel(columnNames, 0)
        
        // Load actual rules from settings instead of hardcoded data
        loadRulesFromSettings()
        
        // Create table
        table = JTable(tableModel)
        table!!.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table!!.preferredScrollableViewportSize = Dimension(600, 200)
        
        // Create scroll pane
        val scrollPane = JScrollPane(table)
        
        // Create buttons
        val buttonPanel = JPanel()
        val addButton = JButton("Add Rule")
        val removeButton = JButton("Remove Rule")
        val resetButton = JButton("Reset to Defaults")
        
        addButton.addActionListener {
            tableModel!!.addRow(arrayOf(true, "New Rule", "pattern", "#000000", "#FFFFFF"))
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
        tableModel!!.setRowCount(0) // Clear existing rows
        
        for (rule in settings.rules) {
            tableModel!!.addRow(arrayOf(
                rule.enabled,
                rule.name,
                rule.targetWord,
                rule.foregroundColor,
                rule.backgroundColor
            ))
        }
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
        
        // Check if any rule data differs
        for (i in 0 until tableModel!!.rowCount) {
            val rule = settings.rules[i]
            if (tableModel!!.getValueAt(i, 0) != rule.enabled ||
                tableModel!!.getValueAt(i, 1) != rule.name ||
                tableModel!!.getValueAt(i, 2) != rule.targetWord ||
                tableModel!!.getValueAt(i, 3) != rule.foregroundColor ||
                tableModel!!.getValueAt(i, 4) != rule.backgroundColor) {
                return true
            }
        }
        return false
    }

    fun apply(settings: SimpleSettings) {
        // Convert table data back to HighlightRule objects
        settings.rules.clear()
        
        for (i in 0 until tableModel!!.rowCount) {
            val enabled = tableModel!!.getValueAt(i, 0) as? Boolean ?: true
            val name = tableModel!!.getValueAt(i, 1) as? String ?: "Rule"
            val targetWord = tableModel!!.getValueAt(i, 2) as? String ?: "pattern"
            val foregroundColor = tableModel!!.getValueAt(i, 3) as? String ?: "#000000"
            val backgroundColor = tableModel!!.getValueAt(i, 4) as? String ?: "#FFFFFF"
            
            // Create new rule with table data
            val rule = HighlightRule(
                id = "rule_$i",
                name = name,
                targetWord = targetWord,
                isRegex = targetWord.contains("\\"),
                condition = "",
                exclusion = "",
                backgroundColor = backgroundColor,
                foregroundColor = foregroundColor,
                fontStyle = HighlightRule.FontStyle.NORMAL,
                textDecoration = HighlightRule.TextDecoration.NONE,
                enabled = enabled
            )
            
            settings.rules.add(rule)
        }
    }

    fun reset(settings: SimpleSettings) {
        // Load actual settings into table
        loadRulesFromSettings()
    }
}