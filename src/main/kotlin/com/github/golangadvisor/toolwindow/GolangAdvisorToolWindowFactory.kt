package com.github.golangadvisor.toolwindow

import com.github.golangadvisor.services.GoModService
import com.github.golangadvisor.services.ToolWindowService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Factory for creating the Golang Advisor tool window.
 *
 * The tool window provides a GUI for managing Go dependencies:
 * - View installed packages from go.mod
 * - Add new packages with search
 * - Update packages to newer versions
 * - Remove unused packages
 */
class GolangAdvisorToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val golangAdvisorPanel = GolangAdvisorToolWindow(project, toolWindow)

        // Register in service for access from actions
        ToolWindowService.getInstance(project).setToolWindow(golangAdvisorPanel)

        val content = ContentFactory.getInstance().createContent(
            golangAdvisorPanel.getContent(),
            "",
            false
        )
        toolWindow.contentManager.addContent(content)
    }

    /**
     * Tool window should be available in all projects.
     * It will show appropriate message if go.mod is not found.
     */
    override fun shouldBeAvailable(project: Project): Boolean {
        return true
    }
}
