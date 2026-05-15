package com.ki960213.riverpodgraph.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class RiverpodToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        if (toolWindow.contentManager.contentCount > 0) {
            return
        }

        val content = ContentFactory.getInstance().createContent(
            DependencyGraphPanel(),
            "Dependencies",
            false,
        )
        toolWindow.contentManager.addContent(content)
    }
}
