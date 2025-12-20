package com.github.golangadvisor.ui.dialogs

import com.github.golangadvisor.api.model.SearchResult
import com.github.golangadvisor.services.PackageSearchService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.Timer
import java.util.TimerTask
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Dialog for searching and adding new Go packages.
 */
class AddPackageDialog(private val project: Project) : DialogWrapper(project) {

    private val log = Logger.getInstance(AddPackageDialog::class.java)
    private val searchService = ApplicationManager.getApplication().service<PackageSearchService>()

    private val searchField = JBTextField()
    private val resultsList = JBList<SearchResult>()
    private val versionComboBox = JComboBox<String>()
    private val infoLabel = JBLabel()
    private val statusLabel = JBLabel()

    private var searchTimer: Timer? = null
    private var selectedPackage: SearchResult? = null

    init {
        title = "Add Go Package"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout()).apply {
            preferredSize = Dimension(600, 500)
            border = JBUI.Borders.empty(8)
        }

        // Top: Search field and status
        val topPanel = JPanel(BorderLayout()).apply {
            val searchPanel = JPanel(BorderLayout()).apply {
                add(JBLabel("Search: "), BorderLayout.WEST)
                searchField.emptyText.text = "Type package name (e.g. gin, echo, gorm)..."
                add(searchField, BorderLayout.CENTER)
            }
            add(searchPanel, BorderLayout.NORTH)

            statusLabel.foreground = java.awt.Color.GRAY
            statusLabel.border = JBUI.Borders.empty(4, 0)
            statusLabel.text = "Type at least 2 characters to search"
            add(statusLabel, BorderLayout.SOUTH)

            border = JBUI.Borders.emptyBottom(8)
        }
        panel.add(topPanel, BorderLayout.NORTH)

        // Setup search listener
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = scheduleSearch()
            override fun removeUpdate(e: DocumentEvent) = scheduleSearch()
            override fun changedUpdate(e: DocumentEvent) = scheduleSearch()
        })

        // Center: Results list
        resultsList.cellRenderer = PackageListCellRenderer()
        resultsList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        resultsList.emptyText.text = "Search results will appear here..."

        resultsList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                selectedPackage = resultsList.selectedValue
                selectedPackage?.let { loadVersions(it.path) }
                updateInfoLabel()
            }
        }

        val scrollPane = JBScrollPane(resultsList).apply {
            preferredSize = Dimension(550, 300)
        }
        panel.add(scrollPane, BorderLayout.CENTER)

        // Bottom: Version selector and info
        val bottomPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyTop(8)

            val versionPanel = JPanel(BorderLayout()).apply {
                add(JBLabel("Version: "), BorderLayout.WEST)
                versionComboBox.addItem("latest")
                add(versionComboBox, BorderLayout.CENTER)
            }
            add(versionPanel, BorderLayout.NORTH)

            infoLabel.border = JBUI.Borders.emptyTop(8)
            add(infoLabel, BorderLayout.CENTER)
        }
        panel.add(bottomPanel, BorderLayout.SOUTH)

        return panel
    }

    private fun scheduleSearch() {
        searchTimer?.cancel()
        searchTimer = Timer()
        searchTimer?.schedule(object : TimerTask() {
            override fun run() {
                val query = searchField.text
                log.info("Timer fired, searching for: '$query'")
                performSearch(query)
            }
        }, 500) // 500ms debounce
    }

    private fun performSearch(query: String) {
        if (query.length < 2) {
            SwingUtilities.invokeLater {
                resultsList.setListData(emptyArray())
                statusLabel.text = "Type at least 2 characters to search"
            }
            return
        }

        SwingUtilities.invokeLater {
            statusLabel.text = "Searching for '$query'..."
            resultsList.emptyText.text = "Searching..."
        }

        // Run search in background thread
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                log.info("Performing GitHub search for: $query")

                // Use runBlocking to call suspend function from non-coroutine context
                val results = kotlinx.coroutines.runBlocking {
                    searchService.searchPackages(query)
                }

                log.info("Search returned ${results.size} results")

                SwingUtilities.invokeLater {
                    if (results.isEmpty()) {
                        resultsList.emptyText.text = "No packages found for '$query'"
                        statusLabel.text = "No results found"
                    } else {
                        statusLabel.text = "Found ${results.size} packages"
                    }
                    resultsList.setListData(results.toTypedArray())
                    if (results.isNotEmpty()) {
                        resultsList.selectedIndex = 0
                    }
                }
            } catch (e: Exception) {
                log.warn("Search failed: ${e.message}", e)
                SwingUtilities.invokeLater {
                    statusLabel.text = "Search failed: ${e.message}"
                    resultsList.emptyText.text = "Search failed. Try again."
                }
            }
        }
    }

    private fun loadVersions(packagePath: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val versions = kotlinx.coroutines.runBlocking {
                    searchService.getPackageVersions(packagePath)
                }

                SwingUtilities.invokeLater {
                    versionComboBox.removeAllItems()
                    versionComboBox.addItem("latest")
                    versions.take(20).forEach { version ->
                        versionComboBox.addItem(version)
                    }
                }
            } catch (e: Exception) {
                log.warn("Failed to load versions: ${e.message}")
            }
        }
    }

    private fun updateInfoLabel() {
        val pkg = selectedPackage
        infoLabel.text = if (pkg != null) {
            buildString {
                append("<html>")
                append("<b>${pkg.path}</b><br>")
                if (pkg.synopsis.isNotBlank()) {
                    append("${pkg.synopsis}<br>")
                }
                if (pkg.license != null) {
                    append("<font color='gray'>License: ${pkg.license}</font> ")
                }
                if (pkg.stars != null && pkg.stars > 0) {
                    append("<font color='gray'>★ ${pkg.stars}</font>")
                }
                append("</html>")
            }
        } else {
            ""
        }
    }

    override fun doValidate(): ValidationInfo? {
        if (selectedPackage == null) {
            return ValidationInfo("Please search and select a package from the list", searchField)
        }
        return null
    }

    override fun dispose() {
        searchTimer?.cancel()
        super.dispose()
    }

    fun getSelectedPackage(): SearchResult? = selectedPackage

    fun getSelectedVersion(): String? {
        val version = versionComboBox.selectedItem as? String
        return if (version == "latest") null else version
    }

    private class PackageListCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): java.awt.Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)

            if (value is SearchResult) {
                // Use proper colors for selection state
                val textColor = if (isSelected) "#ffffff" else "#bbbbbb"
                val starsColor = if (isSelected) "#cccccc" else "#888888"
                val synopsisColor = if (isSelected) "#dddddd" else "#777777"

                text = buildString {
                    append("<html>")
                    append("<span style='color:$textColor'><b>${value.path}</b></span>")
                    if (value.stars != null && value.stars > 0) {
                        append(" <span style='color:$starsColor'>★${value.stars}</span>")
                    }
                    if (value.synopsis.isNotBlank()) {
                        val shortSynopsis = if (value.synopsis.length > 80) {
                            value.synopsis.take(77) + "..."
                        } else {
                            value.synopsis
                        }
                        append("<br><span style='font-size:smaller;color:$synopsisColor'>$shortSynopsis</span>")
                    }
                    append("</html>")
                }
            }

            return this
        }
    }
}
