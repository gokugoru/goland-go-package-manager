package com.github.golangadvisor.gomod

/**
 * Represents a Go module dependency with additional metadata.
 * This is used for display in the UI with enriched information.
 */
data class GoModDependency(
    val path: String,
    val version: String,
    val isIndirect: Boolean = false,
    val latestVersion: String? = null,
    val hasUpdate: Boolean = false,
    val description: String? = null,
    val license: String? = null,
    val repository: String? = null,
    val usedInCode: Boolean = false
) {
    /**
     * Returns the package name (last part of the path).
     */
    val name: String
        get() = path.substringAfterLast('/')

    /**
     * Returns the owner/org of the package (for github.com packages).
     */
    val owner: String?
        get() = if (path.startsWith("github.com/")) {
            path.removePrefix("github.com/").substringBefore('/')
        } else null

    /**
     * Returns true if this is a GitHub-hosted package.
     */
    val isGitHub: Boolean
        get() = path.startsWith("github.com/")

    /**
     * Returns the full import path.
     */
    val importPath: String
        get() = path

    /**
     * Returns the version without 'v' prefix if present.
     */
    val cleanVersion: String
        get() = version.removePrefix("v")

    /**
     * Returns the pkg.go.dev URL for this package.
     */
    val pkgGoDevUrl: String
        get() = "https://pkg.go.dev/$path@$version"

    /**
     * Returns the GitHub URL if this is a GitHub package.
     */
    val gitHubUrl: String?
        get() = if (isGitHub) {
            val parts = path.removePrefix("github.com/").split("/")
            if (parts.size >= 2) {
                "https://github.com/${parts[0]}/${parts[1]}"
            } else null
        } else null

    companion object {
        /**
         * Creates a GoModDependency from a RequireDirective.
         */
        fun fromRequireDirective(directive: RequireDirective): GoModDependency {
            return GoModDependency(
                path = directive.path,
                version = directive.version,
                isIndirect = directive.indirect
            )
        }
    }
}
