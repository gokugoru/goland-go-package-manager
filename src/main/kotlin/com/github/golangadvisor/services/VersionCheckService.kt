package com.github.golangadvisor.services

import com.github.golangadvisor.api.client.GitHubApiClient
import com.github.golangadvisor.api.client.GoProxyClient
import com.github.golangadvisor.gomod.GoModDependency
import com.github.golangadvisor.util.VersionComparator
import com.intellij.openapi.components.Service
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Application-level service for checking package version updates.
 *
 * Features:
 * - Checks for newer versions of installed packages
 * - Caches version information to reduce API calls
 * - Uses multiple sources (Go Proxy, GitHub)
 */
@Service(Service.Level.APP)
class VersionCheckService {

    private val goProxyClient = GoProxyClient()
    private val gitHubClient = GitHubApiClient()
    private val versionComparator = VersionComparator()

    // Cache for version info (path -> VersionInfo)
    private val versionCache = ConcurrentHashMap<String, CachedVersionInfo>()
    private val cacheTtlMs = 5 * 60 * 1000L // 5 minutes

    /**
     * Result of a version check.
     */
    data class VersionUpdate(
        val packagePath: String,
        val currentVersion: String,
        val latestVersion: String?,
        val hasUpdate: Boolean
    )

    /**
     * Checks updates for a list of dependencies.
     *
     * @param dependencies List of dependencies to check
     * @return List of version updates
     */
    suspend fun checkUpdates(dependencies: List<GoModDependency>): List<VersionUpdate> {
        return withContext(Dispatchers.IO) {
            dependencies.map { dep ->
                async {
                    checkUpdate(dep)
                }
            }.awaitAll()
        }
    }

    /**
     * Checks update for a single dependency.
     */
    suspend fun checkUpdate(dependency: GoModDependency): VersionUpdate {
        val latestVersion = getLatestVersion(dependency.path)

        val hasUpdate = latestVersion != null &&
                versionComparator.isNewer(latestVersion, dependency.version)

        return VersionUpdate(
            packagePath = dependency.path,
            currentVersion = dependency.version,
            latestVersion = latestVersion,
            hasUpdate = hasUpdate
        )
    }

    /**
     * Gets the latest version for a package.
     * Uses cache when available.
     */
    suspend fun getLatestVersion(packagePath: String): String? {
        // Check cache first
        val cached = versionCache[packagePath]
        if (cached != null && !cached.isExpired()) {
            return cached.latestVersion
        }

        // Fetch from sources
        val latestVersion = fetchLatestVersion(packagePath)

        // Update cache
        versionCache[packagePath] = CachedVersionInfo(
            latestVersion = latestVersion,
            timestamp = System.currentTimeMillis()
        )

        return latestVersion
    }

    /**
     * Gets all available versions for a package.
     */
    suspend fun getAllVersions(packagePath: String): List<String> {
        return withContext(Dispatchers.IO) {
            goProxyClient.getVersions(packagePath)
        }
    }

    /**
     * Fetches the latest version from multiple sources.
     */
    private suspend fun fetchLatestVersion(packagePath: String): String? {
        return withContext(Dispatchers.IO) {
            // Try Go Proxy first (most reliable)
            val proxyVersion = try {
                goProxyClient.getLatestVersion(packagePath)
            } catch (e: Exception) {
                null
            }

            if (proxyVersion != null) {
                return@withContext proxyVersion
            }

            // Fallback to GitHub for github.com packages
            if (packagePath.startsWith("github.com/")) {
                try {
                    getLatestGitHubVersion(packagePath)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }
    }

    /**
     * Gets the latest version from GitHub releases.
     */
    private suspend fun getLatestGitHubVersion(packagePath: String): String? {
        val parts = packagePath.removePrefix("github.com/").split("/")
        if (parts.size < 2) return null

        val owner = parts[0]
        val repo = parts[1]

        val release = gitHubClient.getLatestRelease(owner, repo)
        return release?.tagName
    }

    /**
     * Enriches dependencies with update information.
     */
    suspend fun enrichWithUpdates(dependencies: List<GoModDependency>): List<GoModDependency> {
        val updates = checkUpdates(dependencies)
        val updateMap = updates.associateBy { it.packagePath }

        return dependencies.map { dep ->
            val update = updateMap[dep.path]
            if (update != null) {
                dep.copy(
                    latestVersion = update.latestVersion,
                    hasUpdate = update.hasUpdate
                )
            } else {
                dep
            }
        }
    }

    /**
     * Clears the version cache.
     */
    fun clearCache() {
        versionCache.clear()
    }

    /**
     * Removes expired entries from the cache.
     */
    fun cleanupCache() {
        val now = System.currentTimeMillis()
        versionCache.entries.removeIf { (_, info) ->
            now - info.timestamp > cacheTtlMs
        }
    }

    private data class CachedVersionInfo(
        val latestVersion: String?,
        val timestamp: Long
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - timestamp > 5 * 60 * 1000L
        }
    }
}
