package com.github.golangadvisor.actions

import com.github.golangadvisor.services.GoModService
import com.github.golangadvisor.ui.dialogs.AddPackageDialog
import com.github.golangadvisor.util.NotificationUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking

/**
 * Action for adding a new Go package to the project.
 *
 * Opens a search dialog, then runs 'go get' to add the selected package.
 */
class AddPackageAction : AnAction() {

    private val log = Logger.getInstance(AddPackageAction::class.java)

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val dialog = AddPackageDialog(project)
        if (dialog.showAndGet()) {
            val selectedPackage = dialog.getSelectedPackage() ?: return
            val version = dialog.getSelectedVersion()

            log.info("Adding package: ${selectedPackage.path}, version: $version")
            addPackage(project, selectedPackage.path, version)
        }
    }

    private fun addPackage(project: Project, packagePath: String, version: String?) {
        val goModService = project.service<GoModService>()

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Adding package: $packagePath",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Running go get $packagePath..."

                runBlocking {
                    log.info("Running go get for: $packagePath@${version ?: "latest"}")
                    val result = goModService.addPackage(packagePath, version)

                    result.onSuccess {
                        log.info("Successfully added package: $packagePath")
                        NotificationUtil.packageAdded(project, packagePath)
                    }.onFailure { error ->
                        log.warn("Failed to add package: $packagePath", error)
                        NotificationUtil.operationFailed(project, "Add package", error.message)
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
