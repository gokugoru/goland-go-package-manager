package com.github.golangadvisor.toolwindow

import com.github.golangadvisor.gomod.GoModDependency
import javax.swing.table.AbstractTableModel

/**
 * Table model for displaying Go packages.
 *
 * Columns:
 * - Package: The import path
 * - Version: Current version
 * - Type: Direct or indirect dependency
 * - Latest: Latest available version (with update indicator)
 */
class PackagesTableModel : AbstractTableModel() {

    private val columns = arrayOf("Package", "Version", "Type", "Latest")
    private var packages: List<GoModDependency> = emptyList()

    override fun getRowCount(): Int = packages.size

    override fun getColumnCount(): Int = columns.size

    override fun getColumnName(column: Int): String = columns[column]

    override fun getColumnClass(columnIndex: Int): Class<*> = String::class.java

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        if (rowIndex < 0 || rowIndex >= packages.size) return ""

        val pkg = packages[rowIndex]
        return when (columnIndex) {
            0 -> pkg.path
            1 -> pkg.version
            2 -> formatTypeColumn(pkg)
            3 -> formatLatestVersion(pkg)
            else -> ""
        }
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

    /**
     * Updates the package list.
     */
    fun setPackages(newPackages: List<GoModDependency>) {
        packages = newPackages
        fireTableDataChanged()
    }

    /**
     * Gets the package at the specified row.
     */
    fun getPackageAt(rowIndex: Int): GoModDependency? {
        return packages.getOrNull(rowIndex)
    }

    /**
     * Gets all packages.
     */
    fun getAllPackages(): List<GoModDependency> = packages

    /**
     * Checks if a package has an update available.
     */
    fun hasUpdate(rowIndex: Int): Boolean {
        return packages.getOrNull(rowIndex)?.hasUpdate == true
    }

    /**
     * Formats the Type column to show usage status.
     * - "used" - package is imported in .go files
     * - "transitive" - package is a dependency of another package
     */
    private fun formatTypeColumn(pkg: GoModDependency): String {
        return if (pkg.usedInCode) "used" else "transitive"
    }

    /**
     * Formats the latest version display.
     */
    private fun formatLatestVersion(pkg: GoModDependency): String {
        return when {
            pkg.latestVersion == null -> "checking..."
            pkg.hasUpdate -> "⬆ ${pkg.latestVersion}"
            else -> "✓ up to date"
        }
    }

    /**
     * Gets the number of packages with updates.
     */
    fun getUpdateCount(): Int {
        return packages.count { it.hasUpdate }
    }

    /**
     * Gets packages that have updates available.
     */
    fun getPackagesWithUpdates(): List<GoModDependency> {
        return packages.filter { it.hasUpdate }
    }

    /**
     * Gets direct dependencies only.
     */
    fun getDirectDependencies(): List<GoModDependency> {
        return packages.filter { !it.isIndirect }
    }

    /**
     * Gets indirect dependencies only.
     */
    fun getIndirectDependencies(): List<GoModDependency> {
        return packages.filter { it.isIndirect }
    }
}
