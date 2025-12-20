package com.github.golangadvisor.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

/**
 * Settings page for Golang Advisor.
 *
 * Displayed in Settings/Preferences → Tools → Golang Advisor.
 */
class GolangAdvisorConfigurable : Configurable {

    private var mainPanel: JPanel? = null

    // UI components
    private val autoCheckUpdatesCheckBox = JBCheckBox("Automatically check for package updates")
    private val updateIntervalSpinner = JSpinner(SpinnerNumberModel(30, 5, 1440, 5))
    private val showIndirectCheckBox = JBCheckBox("Show indirect dependencies")
    private val showNotificationsCheckBox = JBCheckBox("Show update notifications")
    private val useGoProxyCheckBox = JBCheckBox("Use Go Module Proxy for version info")
    private val goProxyUrlField = JBTextField()
    private val githubApiCheckBox = JBCheckBox("Use GitHub API for additional package info")

    override fun getDisplayName(): String = "Golang Advisor"

    override fun createComponent(): JComponent {
        // Build the settings form
        mainPanel = FormBuilder.createFormBuilder()
            .addComponent(JBLabel("<html><b>Update Checking</b></html>").apply {
                border = JBUI.Borders.emptyBottom(8)
            })
            .addComponent(autoCheckUpdatesCheckBox)
            .addLabeledComponent(
                JBLabel("Check interval (minutes):"),
                updateIntervalSpinner
            )
            .addComponent(showNotificationsCheckBox)

            .addSeparator()

            .addComponent(JBLabel("<html><b>Display</b></html>").apply {
                border = JBUI.Borders.empty(8, 0)
            })
            .addComponent(showIndirectCheckBox)

            .addSeparator()

            .addComponent(JBLabel("<html><b>API Settings</b></html>").apply {
                border = JBUI.Borders.empty(8, 0)
            })
            .addComponent(useGoProxyCheckBox)
            .addLabeledComponent(
                JBLabel("Go Proxy URL:"),
                goProxyUrlField
            )
            .addComponent(githubApiCheckBox)

            .addComponentFillVertically(JPanel(), 0)
            .panel

        // Setup listeners
        autoCheckUpdatesCheckBox.addActionListener {
            updateIntervalSpinner.isEnabled = autoCheckUpdatesCheckBox.isSelected
        }

        useGoProxyCheckBox.addActionListener {
            goProxyUrlField.isEnabled = useGoProxyCheckBox.isSelected
        }

        return mainPanel!!
    }

    override fun isModified(): Boolean {
        val settings = GolangAdvisorSettings.getInstance()
        return autoCheckUpdatesCheckBox.isSelected != settings.autoCheckUpdates ||
                (updateIntervalSpinner.value as Int) != settings.updateCheckIntervalMinutes ||
                showIndirectCheckBox.isSelected != settings.showIndirectDependencies ||
                showNotificationsCheckBox.isSelected != settings.showUpdateNotifications ||
                useGoProxyCheckBox.isSelected != settings.useGoProxy ||
                goProxyUrlField.text != settings.goProxyUrl ||
                githubApiCheckBox.isSelected != settings.githubApiEnabled
    }

    override fun apply() {
        val settings = GolangAdvisorSettings.getInstance()
        settings.autoCheckUpdates = autoCheckUpdatesCheckBox.isSelected
        settings.updateCheckIntervalMinutes = updateIntervalSpinner.value as Int
        settings.showIndirectDependencies = showIndirectCheckBox.isSelected
        settings.showUpdateNotifications = showNotificationsCheckBox.isSelected
        settings.useGoProxy = useGoProxyCheckBox.isSelected
        settings.goProxyUrl = goProxyUrlField.text
        settings.githubApiEnabled = githubApiCheckBox.isSelected
    }

    override fun reset() {
        val settings = GolangAdvisorSettings.getInstance()
        autoCheckUpdatesCheckBox.isSelected = settings.autoCheckUpdates
        updateIntervalSpinner.value = settings.updateCheckIntervalMinutes
        showIndirectCheckBox.isSelected = settings.showIndirectDependencies
        showNotificationsCheckBox.isSelected = settings.showUpdateNotifications
        useGoProxyCheckBox.isSelected = settings.useGoProxy
        goProxyUrlField.text = settings.goProxyUrl
        githubApiCheckBox.isSelected = settings.githubApiEnabled

        // Update enabled state
        updateIntervalSpinner.isEnabled = autoCheckUpdatesCheckBox.isSelected
        goProxyUrlField.isEnabled = useGoProxyCheckBox.isSelected
    }

    override fun disposeUIResources() {
        mainPanel = null
    }
}
