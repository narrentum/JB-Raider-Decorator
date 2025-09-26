package com.codedecorator.rider.minimal

import com.intellij.openapi.project.ProjectManager
import java.awt.*
import javax.swing.*
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
        
        // Create table model with simple columns
        val columns = arrayOf("Name", "Pattern", "Enabled")
        tableModel = DefaultTableModel(columns, 0)
        
        // Load test data for now
        loadTestData()
        
        println("[DEBUG] Test data loaded: ${tableModel!!.rowCount} rows")
        
        // Create table
        table = JTable(tableModel)
        table!!.preferredScrollableViewportSize = Dimension(600, 300)
        
        // Create scroll pane
        val scrollPane = JScrollPane(table)
        
        // Create simple title
        val titleLabel = JLabel("CoDecorator Rules")
        titleLabel.font = titleLabel.font.deriveFont(16f)
        titleLabel.horizontalAlignment = SwingConstants.CENTER
        
        // Simple layout
        mainPanel.add(titleLabel, BorderLayout.NORTH)
        mainPanel.add(scrollPane, BorderLayout.CENTER)
        
        // Add padding
        mainPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        
        println("[DEBUG] UI creation complete. Components added to layout:")
        println("[DEBUG] - titleLabel added to NORTH")
        println("[DEBUG] - scrollPane added to CENTER")
    }

    private fun loadTestData() {
        // Add some test data
        tableModel!!.addRow(arrayOf("Test Rule 1", "this", true))
        tableModel!!.addRow(arrayOf("Test Rule 2", "TODO", true))
        tableModel!!.addRow(arrayOf("Test Rule 3", "FIXME", false))
        
        // Also try to load from real settings
        try {
            val settings = SimpleSettings.getInstance()
            println("[DEBUG] Loading ${settings.rules.size} rules from settings")
            
            for (rule in settings.rules) {
                tableModel!!.addRow(arrayOf(rule.name, rule.targetWord, rule.enabled))
            }
        } catch (e: Exception) {
            println("[ERROR] Failed to load settings: ${e.message}")
        }
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
        // For now, always return false to simplify
        return false
    }

    fun apply(settings: SimpleSettings) {
        // Simplified - no-op for now
        println("[DEBUG] Apply called")
    }

    fun reset(settings: SimpleSettings) {
        // Simplified - reload test data
        tableModel!!.rowCount = 0
        loadTestData()
        println("[DEBUG] Reset called")
    }
}