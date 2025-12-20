package com.github.golangadvisor.toolwindow

import com.github.golangadvisor.gomod.GoModDependency
import com.github.golangadvisor.services.GoModService
import com.github.golangadvisor.services.ImportScannerService
import com.github.golangadvisor.services.VersionCheckService
import com.github.golangadvisor.ui.components.PackagesTable
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent

/**
 * Main tool window panel for Golang Advisor.
 *
 * Contains:
 * - Toolbar with actions (Add, Remove, Update, Refresh)
 * - Search field for filtering packages
 * - Table displaying installed packages
 * - Status bar showing loading state and errors
 */
class GolangAdvisorToolWindow(
    private val project: Project,
    private val toolWindow: ToolWindow
) : Disposable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val goModService = project.service<GoModService>()
    private val importScannerService = ImportScannerService.getInstance(project)
    private val versionCheckService = ApplicationManager.getApplication().service<VersionCheckService>()

    private val loadingPanel: JBLoadingPanel
    private val packagesTable: PackagesTable
    private val searchField: SearchTextField
    private val statusLabel: JLabel
    private val mainPanel: JPanel

    private var allPackages: List<GoModDependency> = emptyList()

    init {
        packagesTable = PackagesTable(project)
        searchField = SearchTextField(true)
        statusLabel = JLabel("", SwingConstants.LEFT)

        loadingPanel = JBLoadingPanel(BorderLayout(), this)
        mainPanel = createMainPanel()

        loadingPanel.add(mainPanel, BorderLayout.CENTER)

        setupListeners()
        observeState()
    }

    private fun createMainPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            // Toolbar
            val toolbar = createToolbar()
            add(toolbar.component, BorderLayout.NORTH)

            // Center content
            val centerPanel = JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(4)

                // Search field
                searchField.textEditor.emptyText.text = "Filter packages..."
                add(searchField, BorderLayout.NORTH)

                // Packages table
                val scrollPane = JBScrollPane(packagesTable).apply {
                    border = JBUI.Borders.emptyTop(4)
                }
                add(scrollPane, BorderLayout.CENTER)

                // Status bar
                statusLabel.border = JBUI.Borders.empty(4, 0, 0, 0)
                add(statusLabel, BorderLayout.SOUTH)
            }
            add(centerPanel, BorderLayout.CENTER)
        }
    }

    private fun createToolbar(): ActionToolbar {
        val actionManager = ActionManager.getInstance()

        // Get the action group defined in plugin.xml
        val actionGroup = actionManager.getAction("GolangAdvisor.ToolWindowActions")

        return if (actionGroup is DefaultActionGroup) {
            actionManager.createActionToolbar(
                ActionPlaces.TOOLWINDOW_TITLE,
                actionGroup,
                true
            ).apply {
                targetComponent = mainPanel
            }
        } else {
            // Fallback: create an empty toolbar
            actionManager.createActionToolbar(
                ActionPlaces.TOOLWINDOW_TITLE,
                DefaultActionGroup(),
                true
            )
        }
    }

    private fun setupListeners() {
        // Search field listener with debounce
        searchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                filterPackages(searchField.text)
            }
        })

        // Listen for go.mod changes
        goModService.addListener {
            scope.launch {
                refreshPackages()
            }
        }
    }

    private fun observeState() {
        // Observe loading state
        scope.launch {
            goModService.isLoading.collectLatest { isLoading ->
                if (isLoading) {
                    loadingPanel.startLoading()
                } else {
                    loadingPanel.stopLoading()
                }
            }
        }

        // Observe dependencies - show ALL dependencies
        scope.launch {
            goModService.dependencies.collectLatest { dependencies ->
                allPackages = dependencies
                packagesTable.setPackages(dependencies)
                updateStatus(dependencies.size)

                // Check for updates in background
                checkUpdatesAsync(dependencies)
            }
        }

        // Observe errors
        scope.launch {
            goModService.error.collectLatest { error ->
                if (error != null) {
                    statusLabel.text = "Error: $error"
                }
            }
        }
    }

    private fun checkUpdatesAsync(dependencies: List<GoModDependency>) {
        scope.launch(Dispatchers.IO) {
            try {
                // Scan imports from .go files
                val imports = importScannerService.scanImports()

                // Enrich with import usage info
                val withImportInfo = dependencies.map { dep ->
                    val usedInCode = importScannerService.isPackageUsedInCode(dep.path, imports)
                    dep.copy(usedInCode = usedInCode)
                }

                // Enrich with version updates
                val enriched = versionCheckService.enrichWithUpdates(withImportInfo)

                launch(Dispatchers.Main) {
                    allPackages = enriched
                    packagesTable.setPackages(
                        if (searchField.text.isBlank()) enriched
                        else filterList(enriched, searchField.text)
                    )
                }
            } catch (e: Exception) {
                // Silently ignore update check errors
            }
        }
    }

    /**
     * Refreshes the package list.
     */
    suspend fun refreshPackages() {
        goModService.refresh()
    }

    /**
     * Filters displayed packages by query.
     */
    private fun filterPackages(query: String) {
        val filtered = filterList(allPackages, query)
        packagesTable.setPackages(filtered)
        updateStatus(filtered.size, allPackages.size)
    }

    private fun filterList(packages: List<GoModDependency>, query: String): List<GoModDependency> {
        if (query.isBlank()) return packages

        val lowerQuery = query.lowercase()
        return packages.filter { pkg ->
            pkg.path.lowercase().contains(lowerQuery) ||
            pkg.name.lowercase().contains(lowerQuery)
        }
    }

    private fun updateStatus(shown: Int, total: Int = shown) {
        statusLabel.text = if (shown == total) {
            "$total package${if (total != 1) "s" else ""}"
        } else {
            "Showing $shown of $total packages"
        }
    }

    /**
     * Gets the currently selected package.
     */
    fun getSelectedPackage(): GoModDependency? {
        return packagesTable.getSelectedPackage()
    }

    /**
     * Gets all selected packages.
     */
    fun getSelectedPackages(): List<GoModDependency> {
        return packagesTable.getSelectedPackages()
    }

    /**
     * Returns the main content component.
     */
    fun getContent(): JComponent = loadingPanel

    override fun dispose() {
        scope.cancel()
    }
}
