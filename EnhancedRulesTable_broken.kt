package com.codedecorator.rider.minimal

import com.intellij.ui.ColorPanel
import com.intellij.ui.components.JBCheckBox
import java.awt.Color
import java.awt.Component
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

/**
 * Кастомный рендерер для колонки с цветами
 */
class ColorTableCellRenderer : TableCellRenderer {
    private val colorPanel = ColorPanel()
    private val label = JLabel()
    
    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        val colorString = value as? String ?: "#FFFFFF"
        
        return try {
            val color = parseColorForTable(colorString)
            if (color != null) {
                colorPanel.selectedColor = color
                colorPanel.toolTipText = colorString
                colorPanel
            } else {
                label.text = if (colorString.isEmpty()) "No color" else colorString
                label.toolTipText = if (colorString.isEmpty()) "Transparent" else "Color: $colorString"
                label
            }
        } catch (e: Exception) {
            label.text = if (colorString.isEmpty()) "No color" else colorString
            label.toolTipText = if (colorString.isEmpty()) "Transparent" else "Invalid color: $colorString"
            label
        }
    }
    
    private fun parseColorForTable(colorString: String): Color? {
        return when {
            colorString.isEmpty() -> null
            colorString.startsWith("rgba(") -> {
                try {
                    // rgba(255, 107, 53, 0.1) -> convert to solid color for display
                    val values = colorString.removePrefix("rgba(").removeSuffix(")").split(",").map { it.trim() }
                    if (values.size >= 3) {
                        val r = values[0].toInt()
                        val g = values[1].toInt()
                        val b = values[2].toInt()
                        Color(r, g, b)
                    } else null
                } catch (e: Exception) {
                    null
                }
            }
            colorString.startsWith("#") -> Color.decode(colorString)
            else -> Color.decode("#$colorString")
        }
    }
}

/**
 * Кастомный редактор для выбора стиля шрифта
 */
class FontStyleCellEditor : AbstractCellEditor(), TableCellEditor {
    private val comboBox = JComboBox(HighlightRule.FontStyle.values())
    
    init {
        // Завершаем редактирование сразу при выборе элемента
        comboBox.addActionListener {
            stopCellEditing()
        }
    }
    
    override fun getTableCellEditorComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        row: Int,
        column: Int
    ): Component {
        comboBox.selectedItem = value
        return comboBox
    }
    
    override fun getCellEditorValue(): Any = comboBox.selectedItem ?: HighlightRule.FontStyle.NORMAL
}

/**
 * Кастомный редактор для выбора оформления текста
 */
class TextDecorationCellEditor : AbstractCellEditor(), TableCellEditor {
    private val comboBox = JComboBox(HighlightRule.TextDecoration.values())
    
    init {
        // Завершаем редактирование сразу при выборе элемента
        comboBox.addActionListener {
            stopCellEditing()
        }
    }
    
    override fun getTableCellEditorComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        row: Int,
        column: Int
    ): Component {
        comboBox.selectedItem = value
        return comboBox
    }
    
    override fun getCellEditorValue(): Any = comboBox.selectedItem ?: HighlightRule.TextDecoration.NONE
}
class ColorTableCellEditor : AbstractCellEditor(), TableCellEditor {
    private val colorPanel = ColorPanel()
    private var currentValue = "#FFFFFF"
    
    init {
        // Добавляем слушатель изменения цвета для мгновенного применения
        colorPanel.addActionListener {
            // Завершаем редактирование сразу при выборе цвета
            stopCellEditing()
        }
    }
    
    override fun getTableCellEditorComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        row: Int,
        column: Int
    ): Component {
        currentValue = value as? String ?: "#FFFFFF"
        
        try {
            colorPanel.selectedColor = Color.decode(currentValue)
        } catch (e: NumberFormatException) {
            colorPanel.selectedColor = Color.WHITE
        }
        
        return colorPanel
    }
    
    override fun getCellEditorValue(): Any {
        val color = colorPanel.selectedColor
        return if (color != null) {
            String.format("#%02X%02X%02X", color.red, color.green, color.blue)
        } else {
            "#FFFFFF"
        }
    }
}

/**
 * Кастомный редактор для boolean колонок
 */
class BooleanTableCellEditor : AbstractCellEditor(), TableCellEditor {
    private val checkBox = JBCheckBox()
    
    init {
        checkBox.horizontalAlignment = SwingConstants.CENTER
        checkBox.addActionListener { stopCellEditing() }
    }
    
    override fun getTableCellEditorComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        row: Int,
        column: Int
    ): Component {
        checkBox.isSelected = value as? Boolean ?: false
        checkBox.background = if (isSelected) table.selectionBackground else table.background
        return checkBox
    }
    
    override fun getCellEditorValue(): Any = checkBox.isSelected
}
class BooleanTableCellRenderer : TableCellRenderer {
    private val checkBox = JBCheckBox()
    
    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        checkBox.isSelected = value as? Boolean ?: false
        checkBox.horizontalAlignment = SwingConstants.CENTER
        checkBox.isOpaque = true
        checkBox.background = if (isSelected) table.selectionBackground else table.background
        return checkBox
    }
}

/**
 * Улучшенная версия таблицы правил с кастомными редакторами
 */
class EnhancedRulesTable(tableModel: RulesTableModel) : JTable(tableModel) {
    
    init {
        setupCustomRenderers()
        setupKeyBindings()
    }
    
    private fun setupCustomRenderers() {
        // Используем стандартные рендереры и редакторы для boolean колонок
        val booleanRenderer = JTable().getDefaultRenderer(Boolean::class.javaObjectType)
        val booleanEditor = JTable().getDefaultEditor(Boolean::class.javaObjectType)
        
        columnModel.getColumn(0).cellRenderer = booleanRenderer // Enabled
        columnModel.getColumn(0).cellEditor = booleanEditor
        
        columnModel.getColumn(3).cellRenderer = booleanRenderer // Regex
        columnModel.getColumn(3).cellEditor = booleanEditor
        
        // Кастомный рендерер и редактор для цветовых колонок
        val colorRenderer = ColorTableCellRenderer()
        val colorEditor = ColorTableCellEditor()
        
        columnModel.getColumn(6).cellRenderer = colorRenderer // BG Color
        columnModel.getColumn(6).cellEditor = colorEditor
        
        columnModel.getColumn(7).cellRenderer = colorRenderer // FG Color  
        columnModel.getColumn(7).cellEditor = colorEditor
        
        // Комбобоксы для стилей шрифта и оформления
        columnModel.getColumn(8).cellEditor = FontStyleCellEditor()
        columnModel.getColumn(9).cellEditor = TextDecorationCellEditor()
        
        // Настройка ширины колонок
        columnModel.getColumn(0).preferredWidth = 60  // Enabled
        columnModel.getColumn(1).preferredWidth = 150 // Name
        columnModel.getColumn(2).preferredWidth = 200 // Pattern
        columnModel.getColumn(3).preferredWidth = 60  // Regex
        columnModel.getColumn(4).preferredWidth = 100 // Condition
        columnModel.getColumn(5).preferredWidth = 100 // Exclusion
        columnModel.getColumn(6).preferredWidth = 80  // BG Color
        columnModel.getColumn(7).preferredWidth = 80  // FG Color
        columnModel.getColumn(8).preferredWidth = 100 // Font Style
        columnModel.getColumn(9).preferredWidth = 100 // Decoration
        
        println("[EnhancedRulesTable] Setup custom renderers and editors")
    }
    
    private fun setupKeyBindings() {
        // Простая обработка кликов - позволяем стандартным редакторам работать
        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                val row = rowAtPoint(e.point)
                val col = columnAtPoint(e.point)
                
                if (row >= 0 && col >= 0) {
                    // Для всех колонок - стандартная обработка
                    if (e.clickCount == 1) {
                        // Начинаем редактирование по одному клику
                        editCellAt(row, col)
                        val editor = cellEditor
                        if (editor != null) {
                            val component = editor.getTableCellEditorComponent(this@EnhancedRulesTable, getValueAt(row, col), true, row, col)
                            component.requestFocusInWindow()
                        }
                    }
                }
            }
        })
        
        // Добавляем обработку потери фокуса для автоматического сохранения
        addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusLost(e: java.awt.event.FocusEvent) {
                // Завершаем редактирование при потере фокуса
                if (isEditing) {
                    cellEditor?.stopCellEditing()
                }
            }
        })
        
        // Enter для подтверждения редактирования
        getInputMap(JComponent.WHEN_FOCUSED).put(
            KeyStroke.getKeyStroke("ENTER"), "finishEditing"
        )
        
        getActionMap().put("finishEditing", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent) {
                if (isEditing) {
                    cellEditor?.stopCellEditing()
                }
            }
        })
        
        // Escape для отмены редактирования
        getInputMap(JComponent.WHEN_FOCUSED).put(
            KeyStroke.getKeyStroke("ESCAPE"), "cancelEditing"
        )
        
        getActionMap().put("cancelEditing", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent) {
                if (isEditing) {
                    cellEditor?.cancelCellEditing()
                }
            }
        })
    }
    
    // Переопределяем метод для автоматического завершения редактирования
    override fun changeSelection(rowIndex: Int, columnIndex: Int, toggle: Boolean, extend: Boolean) {
        // Завершаем текущее редактирование перед сменой выделения
        if (isEditing) {
            cellEditor?.stopCellEditing()
        }
        super.changeSelection(rowIndex, columnIndex, toggle, extend)
    }
}
        actionMap.put("startEditing", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent) {
                val row = selectedRow
                val col = selectedColumn
                if (row >= 0 && col >= 0) {
                    editCellAt(row, col)
                }
            }
        })
        
        // Space для активации редактора (особенно для чекбоксов)
        getInputMap(JComponent.WHEN_FOCUSED).put(
            KeyStroke.getKeyStroke("SPACE"), "activateEditor"
        )
        actionMap.put("activateEditor", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent) {
                val row = selectedRow
                val col = selectedColumn
                if (row >= 0 && col >= 0) {
                    editCellAt(row, col)
                    // Для boolean колонок симулируем клик
                    if (col == 0 || col == 3) {
                        val editor = cellEditor
                        if (editor != null) {
                            val component = editor.getTableCellEditorComponent(this@EnhancedRulesTable, getValueAt(row, col), true, row, col)
                            if (component is JCheckBox) {
                                component.doClick()
                            }
                        }
                    }
                }
            }
        })
    }
}