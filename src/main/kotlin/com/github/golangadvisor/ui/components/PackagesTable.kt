package com.github.golangadvisor.ui.components

import com.github.golangadvisor.gomod.GoModDependency
import com.github.golangadvisor.toolwindow.PackagesTableModel
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.ui.PopupHandler
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Desktop
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URI
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableRowSorter

/**
 * Table component for displaying Go packages.
 *
 * Features:
 * - Sortable columns
 * - Row filtering
 * - Context menu
 * - Double-click to open package on pkg.go.dev
 * - Visual indicators for updates
 */
class PackagesTable(private val project: Project) : JBTable() {

    private val tableModel = PackagesTableModel()
    private val rowSorter = TableRowSorter(tableModel)

    init {
        model = tableModel
        setRowSorter(rowSorter)
        selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION

        setupColumns()
        setupRenderers()
        setupContextMenu()
        setupDoubleClick()

        // Visual settings
        setShowGrid(false)
        intercellSpacing = JBUI.size(0, 0)
        rowHeight = JBUI.scale(24)
    }

    private fun setupColumns() {
        // Package path column - wide
        columnModel.getColumn(0).preferredWidth = 300
        columnModel.getColumn(0).minWidth = 150

        // Version column
        columnModel.getColumn(1).preferredWidth = 100
        columnModel.getColumn(1).minWidth = 80

        // Type column
        columnModel.getColumn(2).preferredWidth = 80
        columnModel.getColumn(2).minWidth = 60

        // Latest version column
        columnModel.getColumn(3).preferredWidth = 120
        columnModel.getColumn(3).minWidth = 80
    }

    private fun setupRenderers() {
        // Package path renderer
        columnModel.getColumn(0).cellRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable,
                value: Any?,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int
            ): Component {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                val modelRow = convertRowIndexToModel(row)
                val pkg = tableModel.getPackageAt(modelRow)
                toolTipText = pkg?.path
                return this
            }
        }

        // Type column renderer (different color for indirect)
        columnModel.getColumn(2).cellRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable,
                value: Any?,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int
            ): Component {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                if (!isSelected) {
                    foreground = if (value == "indirect") {
                        Color.GRAY
                    } else {
                        table.foreground
                    }
                }
                return this
            }
        }

        // Latest version renderer (highlight updates)
        columnModel.getColumn(3).cellRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable,
                value: Any?,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int
            ): Component {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                val modelRow = convertRowIndexToModel(row)
                if (!isSelected && tableModel.hasUpdate(modelRow)) {
                    foreground = Color(0, 150, 0) // Green for updates
                }
                return this
            }
        }
    }

    private fun setupContextMenu() {
        val actionManager = ActionManager.getInstance()
        val actionGroup = actionManager.getAction("GolangAdvisor.PackageContextMenu")

        if (actionGroup is DefaultActionGroup) {
            PopupHandler.installPopupMenu(
                this,
                actionGroup,
                ActionPlaces.POPUP
            )
        }
    }

    private fun setupDoubleClick() {
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val pkg = getSelectedPackage() ?: return
                    openInBrowser(pkg.pkgGoDevUrl)
                }
            }
        })
    }

    /**
     * Sets the packages to display.
     */
    fun setPackages(packages: List<GoModDependency>) {
        tableModel.setPackages(packages)
    }

    /**
     * Gets the currently selected package.
     */
    fun getSelectedPackage(): GoModDependency? {
        val selectedRow = selectedRow
        if (selectedRow < 0) return null
        val modelRow = convertRowIndexToModel(selectedRow)
        return tableModel.getPackageAt(modelRow)
    }

    /**
     * Gets all selected packages.
     */
    fun getSelectedPackages(): List<GoModDependency> {
        return selectedRows.toList().mapNotNull { row ->
            val modelRow = convertRowIndexToModel(row)
            tableModel.getPackageAt(modelRow)
        }
    }

    /**
     * Gets packages with available updates.
     */
    fun getPackagesWithUpdates(): List<GoModDependency> {
        return tableModel.getPackagesWithUpdates()
    }

    /**
     * Gets the table model.
     */
    fun getPackagesTableModel(): PackagesTableModel = tableModel

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
