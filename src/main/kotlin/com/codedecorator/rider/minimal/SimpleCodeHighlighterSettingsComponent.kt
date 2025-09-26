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
        
        // Add some sample data
        tableModel!!.addRow(arrayOf(true, "_this highlighting", "_this", "#000000", "#FFFF00"))
        tableModel!!.addRow(arrayOf(true, "TODO comments", "TODO", "#FF0000", "#FFFF00"))
        tableModel!!.addRow(arrayOf(true, "Console logs", "console\\.log", "#0000FF", "#E0E0E0"))
        
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
            tableModel!!.setRowCount(0)
            tableModel!!.addRow(arrayOf(true, "_this highlighting", "_this", "#000000", "#FFFF00"))
            tableModel!!.addRow(arrayOf(true, "TODO comments", "TODO", "#FF0000", "#FFFF00"))
            tableModel!!.addRow(arrayOf(true, "Console logs", "console\\.log", "#0000FF", "#E0E0E0"))
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

    fun getPanel(): JComponent {
        return mainPanel
    }

    fun getPreferredFocusedComponent(): JComponent? {
        return table
    }

    fun isModified(settings: SimpleSettings): Boolean {
        // For now, always return false to avoid save issues
        return false
    }

    fun apply(settings: SimpleSettings) {
        // TODO: Convert table data back to HighlightRule objects
        // For now, just ensure we don't crash
    }

    fun reset(settings: SimpleSettings) {
        // TODO: Load actual settings into table
        // For now, just show default data
    }
}