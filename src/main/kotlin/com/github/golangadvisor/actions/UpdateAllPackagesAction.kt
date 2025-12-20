package com.github.golangadvisor.actions

import com.github.golangadvisor.services.GoModService
import com.github.golangadvisor.util.NotificationUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.runBlocking

/**
 * Action for updating all packages to their latest versions.
 *
 * Prompts for confirmation, then runs 'go get -u ./...'.
 */
class UpdateAllPackagesAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val goModService = project.service<GoModService>()

        val dependencies = goModService.getDependencies()
        val packagesWithUpdates = dependencies.count { it.hasUpdate }

        val message = if (packagesWithUpdates > 0) {
            "This will update $packagesWithUpdates package(s) to their latest versions.\n\n" +
                    "Continue?"
        } else {
            "This will check and update all packages to their latest versions.\n\n" +
                    "Continue?"
        }

        val confirm = Messages.showYesNoDialog(
            project,
            message,
            "Update All Packages",
            "Update All",
            "Cancel",
            Messages.getQuestionIcon()
        )

        if (confirm == Messages.YES) {
            updateAllPackages(project)
        }
    }

    private fun updateAllPackages(project: Project) {
        val goModService = project.service<GoModService>()

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Updating all packages",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Running go get -u ./..."

                runBlocking {
                    goModService.updateAllPackages()
                        .onSuccess {
                            NotificationUtil.info(
                                project,
                                "Update Complete",
                                "All packages have been updated to their latest versions."
                            )
                        }
                        .onFailure { error ->
                            NotificationUtil.operationFailed(project, "Update all packages", error.message)
                        }
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
