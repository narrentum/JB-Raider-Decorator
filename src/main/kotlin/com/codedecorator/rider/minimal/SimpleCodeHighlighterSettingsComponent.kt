package com.codedecorator.rider.minimal

import java.awt.BorderLayout
import javax.swing.*

class SimpleCodeHighlighterSettingsComponent {

    private val mainPanel: JPanel = JPanel(BorderLayout())
    private var rulesTable: EnhancedRulesTable? = null
    private var tableModel: RulesTableModel? = null

    init {
        createUI()
    }

    private fun createUI() {
        // Create the main panel with the table
        tableModel = RulesTableModel()
        rulesTable = EnhancedRulesTable(tableModel!!)
        
        // Create a scroll pane for the table
        val scrollPane = JScrollPane(rulesTable)
        scrollPane.preferredSize = java.awt.Dimension(600, 400)
        
        // Create a panel for buttons
        val buttonPanel = JPanel()
        val addButton = JButton("Add Rule")
        val removeButton = JButton("Remove Rule")
        
        addButton.addActionListener {
            tableModel?.addRule(HighlightRule.createDefault()[0].copy(name = "New Rule"))
        }
        
        removeButton.addActionListener {
            val selectedRow = rulesTable?.selectedRow ?: -1
            if (selectedRow >= 0) {
                tableModel?.removeRule(selectedRow)
            }
        }
        
        buttonPanel.add(addButton)
        buttonPanel.add(removeButton)
        
        // Layout
        mainPanel.add(scrollPane, BorderLayout.CENTER)
        mainPanel.add(buttonPanel, BorderLayout.SOUTH)
        
        // Add some padding
        mainPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
    }

    fun getPanel(): JComponent {
        return mainPanel
    }

    fun getPreferredFocusedComponent(): JComponent? {
        return rulesTable
    }

    fun isModified(settings: SimpleSettings): Boolean {
        return tableModel?.getRules() != settings.rules
    }

    fun apply(settings: SimpleSettings) {
        tableModel?.getRules()?.let { rules ->
            settings.rules.clear()
            settings.rules.addAll(rules)
        }
    }

    fun reset(settings: SimpleSettings) {
        tableModel?.setRules(ArrayList(settings.rules))
    }
}