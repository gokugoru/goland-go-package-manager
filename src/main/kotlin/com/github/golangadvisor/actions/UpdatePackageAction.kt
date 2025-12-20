package com.github.golangadvisor.actions

import com.github.golangadvisor.services.GoModService
import com.github.golangadvisor.services.PackageSearchService
import com.github.golangadvisor.services.ToolWindowService
import com.github.golangadvisor.ui.dialogs.UpdatePackageDialog
import com.github.golangadvisor.util.NotificationUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking

/**
 * Action for updating a package to a newer version.
 *
 * Opens a version selection dialog, then runs 'go get' with the new version.
 */
class UpdatePackageAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedPackage = ToolWindowService.getInstance(project).getSelectedPackage() ?: return

        val packagePath = selectedPackage.path
        val currentVersion = selectedPackage.version

        // Load versions and show dialog
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Loading versions for $packagePath",
            true
        ) {
            private var versions: List<String> = emptyList()

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                val searchService = ApplicationManager.getApplication().service<PackageSearchService>()

                runBlocking {
                    versions = searchService.getPackageVersions(packagePath)
                }
            }

            override fun onSuccess() {
                val dialog = UpdatePackageDialog(
                    project,
                    packagePath,
                    currentVersion,
                    versions
                )

                if (dialog.showAndGet()) {
                    val newVersion = dialog.getSelectedVersion()
                    if (newVersion != currentVersion) {
                        updatePackage(project, packagePath, newVersion)
                    }
                }
            }
        })
    }

    private fun updatePackage(project: Project, packagePath: String, newVersion: String) {
        val goModService = project.service<GoModService>()

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Updating $packagePath to $newVersion",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Running go get..."

                runBlocking {
                    goModService.updatePackage(packagePath, newVersion)
                        .onSuccess {
                            NotificationUtil.packageUpdated(project, packagePath, newVersion)
                        }
                        .onFailure { error ->
                            NotificationUtil.operationFailed(project, "Update package", error.message)
                        }
                }
            }
        })
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val selectedPackage = project?.let { ToolWindowService.getInstance(it).getSelectedPackage() }

        // Only enable if a package is selected AND it has an available update
        e.presentation.isEnabled = selectedPackage != null && selectedPackage.hasUpdate
    }
}
