package com.github.golangadvisor.actions

import com.github.golangadvisor.services.GoModService
import com.github.golangadvisor.services.ToolWindowService
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
 * Action for removing a package from the project.
 *
 * Prompts for confirmation, then removes the package from go.mod
 * and runs 'go mod tidy'.
 */
class RemovePackageAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedPackage = ToolWindowService.getInstance(project).getSelectedPackage() ?: return

        val packagePath = selectedPackage.path

        // Confirm removal
        val confirm = Messages.showYesNoDialog(
            project,
            "Are you sure you want to remove '$packagePath'?\n\n" +
                    "This will remove the package from go.mod and run 'go mod tidy'.",
            "Remove Package",
            "Remove",
            "Cancel",
            Messages.getQuestionIcon()
        )

        if (confirm == Messages.YES) {
            removePackage(project, packagePath)
        }
    }

    private fun removePackage(project: Project, packagePath: String) {
        val goModService = project.service<GoModService>()

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Removing package: $packagePath",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Removing package..."

                runBlocking {
                    goModService.removePackage(packagePath)
                        .onSuccess {
                            NotificationUtil.packageRemoved(project, packagePath)
                        }
                        .onFailure { error ->
                            NotificationUtil.operationFailed(project, "Remove package", error.message)
                        }
                }
            }
        })
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null &&
                ToolWindowService.getInstance(project).getSelectedPackage() != null
    }
}
