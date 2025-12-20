package com.github.golangadvisor.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Package information from pkg.go.dev or other sources.
 */
data class PackageInfo(
    val path: String,
    val synopsis: String = "",
    val description: String? = null,
    val license: String? = null,
    val latestVersion: String? = null,
    val repository: String? = null,
    val documentation: String? = null,
    val stars: Int? = null,
    val forks: Int? = null,
    val openIssues: Int? = null
) {
    val pkgGoDevUrl: String
        get() = "https://pkg.go.dev/$path"
}

/**
 * Search result from package search.
 */
data class SearchResult(
    val path: String,
    val synopsis: String = "",
    val latestVersion: String? = null,
    val license: String? = null,
    val stars: Int? = null,
    val importedBy: Int? = null
) {
    val pkgGoDevUrl: String
        get() = "https://pkg.go.dev/$path"
}

/**
 * Version info from Go Module Proxy.
 */
@Serializable
data class ModuleVersion(
    @SerialName("Version")
    val version: String,

    @SerialName("Time")
    val time: String? = null
)

/**
 * GitHub repository information.
 */
@Serializable
data class GitHubRepository(
    @SerialName("id")
    val id: Long,

    @SerialName("full_name")
    val fullName: String,

    @SerialName("name")
    val name: String,

    @SerialName("description")
    val description: String? = null,

    @SerialName("html_url")
    val htmlUrl: String,

    @SerialName("stargazers_count")
    val stargazersCount: Int = 0,

    @SerialName("forks_count")
    val forksCount: Int = 0,

    @SerialName("open_issues_count")
    val openIssuesCount: Int = 0,

    @SerialName("license")
    val license: GitHubLicense? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null,

    @SerialName("default_branch")
    val defaultBranch: String = "main"
)

/**
 * GitHub license information.
 */
@Serializable
data class GitHubLicense(
    @SerialName("key")
    val key: String,

    @SerialName("name")
    val name: String,

    @SerialName("spdx_id")
    val spdxId: String? = null
)

/**
 * GitHub release information.
 */
@Serializable
data class GitHubRelease(
    @SerialName("id")
    val id: Long,

    @SerialName("tag_name")
    val tagName: String,

    @SerialName("name")
    val name: String? = null,

    @SerialName("html_url")
    val htmlUrl: String? = null,

    @SerialName("prerelease")
    val prerelease: Boolean = false,

    @SerialName("draft")
    val draft: Boolean = false,

    @SerialName("published_at")
    val publishedAt: String? = null
)

/**
 * GitHub search response.
 */
@Serializable
data class GitHubSearchResponse(
    @SerialName("total_count")
    val totalCount: Int,

    @SerialName("incomplete_results")
    val incompleteResults: Boolean,

    @SerialName("items")
    val items: List<GitHubRepository>
)
