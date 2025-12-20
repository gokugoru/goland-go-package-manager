package com.github.golangadvisor.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Application-level settings for Golang Advisor.
 *
 * Persisted in the IDE's global settings.
 */
@State(
    name = "GolangAdvisorSettings",
    storages = [Storage("GolangAdvisorSettings.xml")]
)
@Service(Service.Level.APP)
class GolangAdvisorSettings : PersistentStateComponent<GolangAdvisorSettings.State> {

    private var myState = State()

    data class State(
        var autoCheckUpdates: Boolean = true,
        var updateCheckIntervalMinutes: Int = 30,
        var showIndirectDependencies: Boolean = true,
        var showUpdateNotifications: Boolean = true,
        var useGoProxy: Boolean = true,
        var goProxyUrl: String = "https://proxy.golang.org",
        var githubApiEnabled: Boolean = true
    )

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    /**
     * Whether to automatically check for package updates.
     */
    var autoCheckUpdates: Boolean
        get() = myState.autoCheckUpdates
        set(value) { myState.autoCheckUpdates = value }

    /**
     * Interval between update checks in minutes.
     */
    var updateCheckIntervalMinutes: Int
        get() = myState.updateCheckIntervalMinutes
        set(value) { myState.updateCheckIntervalMinutes = value }

    /**
     * Whether to show indirect dependencies in the package list.
     */
    var showIndirectDependencies: Boolean
        get() = myState.showIndirectDependencies
        set(value) { myState.showIndirectDependencies = value }

    /**
     * Whether to show notifications when updates are available.
     */
    var showUpdateNotifications: Boolean
        get() = myState.showUpdateNotifications
        set(value) { myState.showUpdateNotifications = value }

    /**
     * Whether to use Go Module Proxy for version info.
     */
    var useGoProxy: Boolean
        get() = myState.useGoProxy
        set(value) { myState.useGoProxy = value }

    /**
     * Custom Go Module Proxy URL.
     */
    var goProxyUrl: String
        get() = myState.goProxyUrl
        set(value) { myState.goProxyUrl = value }

    /**
     * Whether to use GitHub API for additional info.
     */
    var githubApiEnabled: Boolean
        get() = myState.githubApiEnabled
        set(value) { myState.githubApiEnabled = value }

    companion object {
        fun getInstance(): GolangAdvisorSettings {
            return com.intellij.openapi.application.ApplicationManager.getApplication()
                .getService(GolangAdvisorSettings::class.java)
        }
    }
}
