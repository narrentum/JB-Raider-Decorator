package com.codedecorator.rider.minimal

import javax.swing.table.AbstractTableModel

class RulesTableModel : AbstractTableModel() {

    private val rules = mutableListOf<HighlightRule>()
    
    // Columns order matches EnhancedRulesJTable expectations:
    // 0: Enabled, 1: Name, 2: Target Word, 3: Regex, 4: Condition, 5: Exclusion,
    // 6: Background Color, 7: Foreground Color, 8: Font Style, 9: Text Decoration
    private val columnNames = arrayOf(
        "Enabled",
        "Name",
        "Target Word",
        "Regex",
        "Condition",
        "Exclusion",
        "Background Color",
        "Foreground Color",
        "Font Style",
        "Text Decoration"
    )

    init {
        // Initialize with default rules
        rules.addAll(HighlightRule.createDefault())
    }

    override fun getRowCount(): Int = rules.size

    override fun getColumnCount(): Int = columnNames.size

    override fun getColumnName(column: Int): String = columnNames[column]

    override fun getColumnClass(columnIndex: Int): Class<*> {
        return when (columnIndex) {
            0 -> Boolean::class.java // Enabled
            1 -> String::class.java  // Name
            2 -> String::class.java  // Target Word
            3 -> Boolean::class.java // Regex
            4 -> String::class.java  // Condition
            5 -> String::class.java  // Exclusion
            6 -> String::class.java  // Background Color
            7 -> String::class.java  // Foreground Color
            8 -> HighlightRule.FontStyle::class.java // Font Style
            9 -> HighlightRule.TextDecoration::class.java // Text Decoration
            else -> String::class.java
        }
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = true

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
        if (rowIndex >= rules.size) return null
        val rule = rules[rowIndex]
        
        return when (columnIndex) {
            0 -> rule.enabled
            1 -> rule.name
            2 -> rule.targetWord
            3 -> rule.isRegex
            4 -> rule.condition
            5 -> rule.exclusion
            6 -> rule.backgroundColor
            7 -> rule.foregroundColor
            8 -> rule.fontStyle
            9 -> rule.textDecoration
            else -> null
        }
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        if (rowIndex >= rules.size) return
        val rule = rules[rowIndex]
        
        val updatedRule = when (columnIndex) {
            0 -> rule.copy(enabled = aValue as? Boolean ?: rule.enabled)
            1 -> rule.copy(name = aValue as? String ?: rule.name)
            2 -> rule.copy(targetWord = aValue as? String ?: rule.targetWord)
            3 -> rule.copy(isRegex = aValue as? Boolean ?: rule.isRegex)
            4 -> rule.copy(condition = aValue as? String ?: rule.condition)
            5 -> rule.copy(exclusion = aValue as? String ?: rule.exclusion)
            6 -> rule.copy(backgroundColor = aValue as? String ?: rule.backgroundColor)
            7 -> rule.copy(foregroundColor = aValue as? String ?: rule.foregroundColor)
            8 -> rule.copy(fontStyle = aValue as? HighlightRule.FontStyle ?: rule.fontStyle)
            9 -> rule.copy(textDecoration = aValue as? HighlightRule.TextDecoration ?: rule.textDecoration)
            else -> rule
        }
        
        rules[rowIndex] = updatedRule
        fireTableCellUpdated(rowIndex, columnIndex)
    }

    fun addRule(rule: HighlightRule) {
        rules.add(rule)
        fireTableRowsInserted(rules.size - 1, rules.size - 1)
    }

    fun removeRule(rowIndex: Int) {
        if (rowIndex >= 0 && rowIndex < rules.size) {
            rules.removeAt(rowIndex)
            fireTableRowsDeleted(rowIndex, rowIndex)
        }
    }

    fun getRules(): List<HighlightRule> = ArrayList(rules)

    fun setRules(newRules: List<HighlightRule>) {
        rules.clear()
        rules.addAll(newRules)
        fireTableDataChanged()
    }
}