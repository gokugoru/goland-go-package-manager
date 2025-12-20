package com.github.golangadvisor.actions

import com.github.golangadvisor.services.ToolWindowService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import java.awt.Desktop
import java.net.URI

/**
 * Action for opening a package's documentation on pkg.go.dev.
 */
class ViewOnPkgGoDevAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedPackage = ToolWindowService.getInstance(project).getSelectedPackage() ?: return

        openInBrowser(selectedPackage.pkgGoDevUrl)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null &&
                ToolWindowService.getInstance(project).getSelectedPackage() != null
    }

    private fun openInBrowser(url: String) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI(url))
            }
        } catch (e: Exception) {
            // Ignore browser open errors
        }
    }
}
