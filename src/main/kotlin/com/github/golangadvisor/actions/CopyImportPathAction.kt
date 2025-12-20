package com.github.golangadvisor.actions

import com.github.golangadvisor.services.ToolWindowService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.StringSelection

/**
 * Action for copying a package's import path to the clipboard.
 */
class CopyImportPathAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedPackage = ToolWindowService.getInstance(project).getSelectedPackage() ?: return

        CopyPasteManager.getInstance().setContents(
            StringSelection(selectedPackage.path)
        )
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null &&
                ToolWindowService.getInstance(project).getSelectedPackage() != null
    }
}
