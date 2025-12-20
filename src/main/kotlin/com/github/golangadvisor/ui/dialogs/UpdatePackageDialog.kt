package com.github.golangadvisor.ui.dialogs

import com.github.golangadvisor.services.PackageSearchService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * Dialog for selecting a version to update a package to.
 *
 * Shows all available versions and highlights the current version.
 */
class UpdatePackageDialog(
    project: Project,
    private val packagePath: String,
    private val currentVersion: String,
    preloadedVersions: List<String>? = null
) : DialogWrapper(project) {

    private val searchService = ApplicationManager.getApplication().service<PackageSearchService>()

    private val versionsList = JBList<String>()
    private val infoLabel = JBLabel()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        title = "Update Package"
        init()
        setupUI()

        if (preloadedVersions != null) {
            setVersions(preloadedVersions)
        } else {
            loadVersions()
        }
    }

    private fun setupUI() {
        versionsList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        versionsList.cellRenderer = VersionListCellRenderer(currentVersion)
    }

    private fun loadVersions() {
        scope.launch {
            try {
                val versions = withContext(Dispatchers.IO) {
                    searchService.getPackageVersions(packagePath)
                }
                setVersions(versions)
            } catch (e: Exception) {
                infoLabel.text = "Failed to load versions: ${e.message}"
            }
        }
    }

    private fun setVersions(versions: List<String>) {
        versionsList.setListData(versions.toTypedArray())

        // Select the version after current one (if available)
        val currentIndex = versions.indexOf(currentVersion)
        if (currentIndex > 0) {
            versionsList.selectedIndex = 0 // Select latest
        } else if (versions.isNotEmpty()) {
            versionsList.selectedIndex = 0
        }

        // Update info
        val newerVersions = if (currentIndex >= 0) currentIndex else versions.size
        infoLabel.text = "$newerVersions newer version(s) available"
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout()).apply {
            preferredSize = Dimension(400, 350)
            border = JBUI.Borders.empty(8)
        }

        // Header
        val headerPanel = JPanel(BorderLayout()).apply {
            add(JBLabel("<html><b>$packagePath</b></html>"), BorderLayout.NORTH)
            add(JBLabel("Current version: $currentVersion"), BorderLayout.SOUTH)
            border = JBUI.Borders.emptyBottom(8)
        }
        panel.add(headerPanel, BorderLayout.NORTH)

        // Versions list
        val scrollPane = JBScrollPane(versionsList).apply {
            preferredSize = Dimension(350, 200)
        }
        panel.add(scrollPane, BorderLayout.CENTER)

        // Info label
        infoLabel.border = JBUI.Borders.emptyTop(8)
        panel.add(infoLabel, BorderLayout.SOUTH)

        return panel
    }

    override fun doValidate(): ValidationInfo? {
        if (versionsList.selectedValue == null) {
            return ValidationInfo("Please select a version", versionsList)
        }
        if (versionsList.selectedValue == currentVersion) {
            return ValidationInfo("Selected version is the same as current", versionsList)
        }
        return null
    }

    override fun dispose() {
        super.dispose()
        scope.cancel()
    }

    /**
     * Gets the selected version.
     */
    fun getSelectedVersion(): String {
        return versionsList.selectedValue ?: currentVersion
    }

    /**
     * Cell renderer that highlights the current version.
     */
    private class VersionListCellRenderer(
        private val currentVersion: String
    ) : javax.swing.DefaultListCellRenderer() {

        override fun getListCellRendererComponent(
            list: javax.swing.JList<*>,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): java.awt.Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)

            val version = value as? String ?: return this

            text = if (version == currentVersion) {
                "$version (current)"
            } else {
                version
            }

            if (version == currentVersion && !isSelected) {
                foreground = java.awt.Color.GRAY
            }

            return this
        }
    }
}
