package com.codedecorator.rider.minimal

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "CodeDecoratorSettings",
    storages = [Storage("CodeDecoratorSettings.xml")]
)
@Service
class SimpleSettings : PersistentStateComponent<SimpleSettings> {
    
    // Список всех правил подсветки
    var rules: MutableList<HighlightRule> = HighlightRule.createDefault().toMutableList()
    
    // Задержка в миллисекундах для throttling обновлений подсветки
    var throttleDelayMs: Int = 5000
    // Количество строк, в пределах которых ищутся условия правил (по умолчанию - 200)
    var conditionSearchLines: Int = 200
    // Включить отладочные сообщения для событий документа (полезно для проверки paste/undo/cut)
    var debugEvents: Boolean = false
    // Применять частичные результаты по мере завершения правил (true) или ждать выполнения всех правил (false)
    var applyPartialOnRuleComplete: Boolean = true
    
    override fun getState(): SimpleSettings = this
    
    override fun loadState(state: SimpleSettings) {
        XmlSerializerUtil.copyBean(state, this)
        
        // Если правила пустые или версия устарела, добавляем новые дефолтные
        if (rules.isEmpty() || !hasNewRules()) {
            println("[SimpleSettings] Loading new default rules (v1.2.3) - VS Code color scheme with rgba() colors")
            rules.clear()
            rules.addAll(HighlightRule.createDefault())
        }
    }
    
    private fun hasNewRules(): Boolean {
        // Принудительно обновляем правила для версии 1.2.3 (новые цвета VS Code)
        val newRuleIds = setOf("rule_todo_fixed", "rule_todo_qa", "rule_todo_progress", "rule_fixme_fixed")
        val existingIds = rules.map { it.id }.toSet()
        
        // Проверяем не только наличие правил, но и их паттерны и цвета
        val hasCorrectPatterns = rules.any { rule ->
            rule.id == "rule_todo_fixed" && rule.targetWord.startsWith("//") && !rule.targetWord.startsWith(".*//")
        }
        
        // ВАЖНО: Проверяем что у _this правила используется новая цветовая схема VS Code
        val hasCorrectThisColors = rules.any { rule ->
            rule.id == "rule_this" && rule.backgroundColor.startsWith("rgba(")
        }
        
        return newRuleIds.all { it in existingIds } && hasCorrectPatterns && hasCorrectThisColors
    }
    
    fun getEnabledRules(): List<HighlightRule> = rules.filter { it.enabled }
    
    fun addRule(rule: HighlightRule) {
        // Генерируем уникальный ID если не задан
        if (rule.id.isEmpty()) {
            rule.id = "rule_${System.currentTimeMillis()}"
        }
        rules.add(rule)
    }
    
    fun removeRule(ruleId: String) {
        rules.removeIf { it.id == ruleId }
    }
    
    fun updateRule(ruleId: String, updatedRule: HighlightRule) {
        val index = rules.indexOfFirst { it.id == ruleId }
        if (index >= 0) {
            rules[index] = updatedRule.copy(id = ruleId)
        }
    }
    
    companion object {
        fun getInstance(): SimpleSettings {
            return com.intellij.openapi.application.ApplicationManager
                .getApplication()
                .getService(SimpleSettings::class.java)
        }
    }
}
