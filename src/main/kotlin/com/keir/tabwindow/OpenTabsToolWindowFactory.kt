package com.keir.tabwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class OpenTabsToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = ContentFactory.getInstance().createContent(null, null, false)
        val panel = OpenTabsPanel(project, content)
        content.component = panel
        content.preferredFocusableComponent = panel.list
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }
}
