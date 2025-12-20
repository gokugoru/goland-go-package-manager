package com.github.golangadvisor

import com.github.golangadvisor.services.GoModService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Startup activity that runs when a project is opened.
 *
 * Initializes the GoModService and triggers initial package loading.
 */
class GolangAdvisorStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        // Initialize the GoModService
        val goModService = project.service<GoModService>()

        // Check if this is a Go project
        if (goModService.hasGoModFile()) {
            // Trigger initial load of dependencies
            goModService.refresh()
        }
    }
}
