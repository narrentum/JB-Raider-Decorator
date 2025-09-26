package com.codedecorator.rider.minimal

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project

class ModernRulesManagerAction : AnAction() {
    
    override fun actionPerformed(event: AnActionEvent) {
        val project: Project? = event.project
        project?.let {
            // Open the Simple Code Highlighter settings using the correct configurable ID
            ShowSettingsUtil.getInstance().showSettingsDialog(it, "com.codedecorator.rider.configurable")
        }
    }
    
    override fun update(event: AnActionEvent) {
        // Action is always enabled when a project is open
        event.presentation.isEnabledAndVisible = event.project != null
    }
}
