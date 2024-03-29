package com.scaventz.window

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class WindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val comparator = ComparatorForm(project)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(comparator.panel, "Diff Panel", false)
        toolWindow.contentManager.addContent(content)
    }
}