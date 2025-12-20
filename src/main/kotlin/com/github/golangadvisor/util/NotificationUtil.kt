package com.github.golangadvisor.util

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/**
 * Utility for showing notifications to the user.
 */
object NotificationUtil {

    private const val GROUP_ID = "Golang Advisor"

    /**
     * Shows an information notification.
     */
    fun info(project: Project?, title: String, content: String) {
        notify(project, title, content, NotificationType.INFORMATION)
    }

    /**
     * Shows a warning notification.
     */
    fun warning(project: Project?, title: String, content: String) {
        notify(project, title, content, NotificationType.WARNING)
    }

    /**
     * Shows an error notification.
     */
    fun error(project: Project?, title: String, content: String) {
        notify(project, title, content, NotificationType.ERROR)
    }

    /**
     * Shows a success notification (info with success styling).
     */
    fun success(project: Project?, title: String, content: String) {
        notify(project, title, content, NotificationType.INFORMATION)
    }

    /**
     * Shows a notification with package operation result.
     */
    fun packageAdded(project: Project?, packagePath: String) {
        info(project, "Package Added", "Successfully added $packagePath")
    }

    fun packageRemoved(project: Project?, packagePath: String) {
        info(project, "Package Removed", "Successfully removed $packagePath")
    }

    fun packageUpdated(project: Project?, packagePath: String, newVersion: String) {
        info(project, "Package Updated", "Updated $packagePath to $newVersion")
    }

    fun operationFailed(project: Project?, operation: String, errorMessage: String?) {
        error(project, "Operation Failed", "$operation failed: ${errorMessage ?: "Unknown error"}")
    }

    private fun notify(
        project: Project?,
        title: String,
        content: String,
        type: NotificationType
    ) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(title, content, type)
            .notify(project)
    }
}
