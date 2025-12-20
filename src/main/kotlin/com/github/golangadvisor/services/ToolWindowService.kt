package com.github.golangadvisor.services

import com.github.golangadvisor.gomod.GoModDependency
import com.github.golangadvisor.toolwindow.GolangAdvisorToolWindow
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * Project-level service for accessing the Golang Advisor tool window.
 */
@Service(Service.Level.PROJECT)
class ToolWindowService {

    private var toolWindow: GolangAdvisorToolWindow? = null

    fun setToolWindow(window: GolangAdvisorToolWindow) {
        toolWindow = window
    }

    fun getToolWindow(): GolangAdvisorToolWindow? = toolWindow

    fun getSelectedPackage(): GoModDependency? {
        return toolWindow?.getSelectedPackage()
    }

    fun getSelectedPackages(): List<GoModDependency> {
        return toolWindow?.getSelectedPackages() ?: emptyList()
    }

    companion object {
        fun getInstance(project: Project): ToolWindowService {
            return project.getService(ToolWindowService::class.java)
        }
    }
}
