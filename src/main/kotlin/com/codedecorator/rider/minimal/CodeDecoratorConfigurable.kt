package com.codedecorator.rider.minimal

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import javax.swing.*

class CodeDecoratorConfigurable : Configurable {

    private var settingsComponent: CodeDecoratorSettings? = null

    override fun getDisplayName(): String {
        return "CoDecorator"
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        return settingsComponent?.getPreferredFocusedComponent()
    }

    override fun createComponent(): JComponent? {
        settingsComponent = CodeDecoratorSettings()
        return settingsComponent?.getPanel()
    }

    override fun isModified(): Boolean {
    val settings = SimpleSettings.getInstance()
    return settingsComponent?.isModified(settings) ?: false
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
    val settings = SimpleSettings.getInstance()
    settingsComponent?.apply(settings)
    }

    override fun reset() {
    val settings = SimpleSettings.getInstance()
    settingsComponent?.reset(settings)
    }

    override fun disposeUIResources() {
        settingsComponent = null
    }
}