package com.github.golangadvisor.services

import com.github.golangadvisor.api.client.GitHubApiClient
import com.github.golangadvisor.api.client.GoProxyClient
import com.github.golangadvisor.api.client.PkgGoDevClient
import com.github.golangadvisor.api.model.PackageInfo
import com.github.golangadvisor.api.model.SearchResult
import com.intellij.openapi.components.Service
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/**
 * Application-level service for searching Go packages.
 *
 * Uses multiple sources:
 * - pkg.go.dev for package search and documentation
 * - Go Module Proxy for version information
 * - GitHub API for repository details
 */
@Service(Service.Level.APP)
class PackageSearchService {

    private val pkgGoDevClient = PkgGoDevClient()
    private val goProxyClient = GoProxyClient()
    private val gitHubClient = GitHubApiClient()

    /**
     * Searches for packages by query using GitHub Search API.
     *
     * @param query The search query
     * @param limit Maximum number of results
     * @return List of search results
     */
    suspend fun searchPackages(query: String, limit: Int = 20): List<SearchResult> {
        if (query.isBlank()) return emptyList()

        return withContext(Dispatchers.IO) {
            // Use GitHub API as primary search (pkg.go.dev has no public API)
            val searchResponse = gitHubClient.searchRepositories(query, limit)

            searchResponse?.items?.map { repo ->
                SearchResult(
                    path = "github.com/${repo.fullName}",
                    synopsis = repo.description ?: "",
                    latestVersion = null,
                    license = repo.license?.name,
                    stars = repo.stargazersCount
                )
            } ?: emptyList()
        }
    }

    /**
     * Gets detailed information about a package.
     *
     * @param packagePath The package import path
     * @return Package info or null if not found
     */
    suspend fun getPackageInfo(packagePath: String): PackageInfo? {
        return withContext(Dispatchers.IO) {
            val pkgInfo = pkgGoDevClient.getPackageInfo(packagePath)

            // Enrich with GitHub data if available
            if (packagePath.startsWith("github.com/")) {
                val gitHubInfo = getGitHubInfo(packagePath)
                pkgInfo?.copy(
                    stars = gitHubInfo?.stars,
                    forks = gitHubInfo?.forks,
                    openIssues = gitHubInfo?.openIssues
                )
            } else {
                pkgInfo
            }
        }
    }

    /**
     * Gets all available versions of a package.
     *
     * @param packagePath The package import path
     * @return List of versions, sorted newest first
     */
    suspend fun getPackageVersions(packagePath: String): List<String> {
        return withContext(Dispatchers.IO) {
            // First try Go Proxy
            val proxyVersions = goProxyClient.getVersions(packagePath)
            if (proxyVersions.isNotEmpty()) {
                return@withContext proxyVersions
            }

            // Fallback to GitHub tags for github.com packages
            if (packagePath.startsWith("github.com/")) {
                val parts = packagePath.removePrefix("github.com/").split("/")
                if (parts.size >= 2) {
                    val tags = gitHubClient.getTags(parts[0], parts[1], 30)
                    // Filter to only version-like tags (v1.0.0, 1.0.0, etc)
                    return@withContext tags.filter { tag ->
                        tag.matches(Regex("^v?\\d+\\.\\d+.*"))
                    }
                }
            }
            emptyList()
        }
    }

    /**
     * Gets the latest version of a package.
     *
     * @param packagePath The package import path
     * @return Latest version or null
     */
    suspend fun getLatestVersion(packagePath: String): String? {
        return withContext(Dispatchers.IO) {
            goProxyClient.getLatestVersion(packagePath)
        }
    }

    /**
     * Searches for packages and enriches results with additional data.
     *
     * @param query The search query
     * @param limit Maximum number of results
     * @return List of enriched search results
     */
    suspend fun searchPackagesEnriched(query: String, limit: Int = 20): List<SearchResult> {
        val basicResults = searchPackages(query, limit)

        return withContext(Dispatchers.IO) {
            basicResults.map { result ->
                async {
                    enrichSearchResult(result)
                }
            }.awaitAll()
        }
    }

    /**
     * Enriches a search result with version and GitHub info.
     */
    private suspend fun enrichSearchResult(result: SearchResult): SearchResult {
        return try {
            val latestVersion = goProxyClient.getLatestVersion(result.path)

            val gitHubInfo = if (result.path.startsWith("github.com/")) {
                getGitHubInfo(result.path)
            } else null

            result.copy(
                latestVersion = latestVersion ?: result.latestVersion,
                stars = gitHubInfo?.stars ?: result.stars,
                license = gitHubInfo?.license ?: result.license
            )
        } catch (e: Exception) {
            result // Return original on error
        }
    }

    /**
     * Gets GitHub repository info for a package.
     */
    private suspend fun getGitHubInfo(packagePath: String): GitHubInfo? {
        val parts = packagePath.removePrefix("github.com/").split("/")
        if (parts.size < 2) return null

        val owner = parts[0]
        val repo = parts[1]

        return try {
            val repoInfo = gitHubClient.getRepository(owner, repo) ?: return null

            GitHubInfo(
                stars = repoInfo.stargazersCount,
                forks = repoInfo.forksCount,
                openIssues = repoInfo.openIssuesCount,
                license = repoInfo.license?.name
            )
        } catch (e: Exception) {
            null
        }
    }

    private data class GitHubInfo(
        val stars: Int,
        val forks: Int,
        val openIssues: Int,
        val license: String?
    )
}
