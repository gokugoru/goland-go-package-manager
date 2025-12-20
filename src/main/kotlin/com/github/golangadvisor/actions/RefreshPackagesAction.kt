package com.github.golangadvisor.actions

import com.github.golangadvisor.services.GoModService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking

/**
 * Action for refreshing the package list.
 *
 * Re-parses go.mod and updates the package table.
 */
class RefreshPackagesAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        refreshPackages(project)
    }

    private fun refreshPackages(project: Project) {
        val goModService = project.service<GoModService>()

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Refreshing packages",
            false
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Reading go.mod..."

                runBlocking {
                    goModService.refresh()
                }
            }
        })
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null &&
                project.service<GoModService>().hasGoModFile()
    }
}
