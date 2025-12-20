package com.github.golangadvisor.api.client

import com.github.golangadvisor.api.model.GitHubRelease
import com.github.golangadvisor.api.model.GitHubRepository
import com.github.golangadvisor.api.model.GitHubSearchResponse
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Client for GitHub REST API.
 *
 * Documentation: https://docs.github.com/en/rest
 *
 * Used for:
 * - Getting repository information (stars, forks, license)
 * - Getting release information
 * - Searching for Go repositories
 */
class GitHubApiClient {

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val baseUrl = "https://api.github.com"

    /**
     * Gets repository information.
     *
     * @param owner Repository owner
     * @param repo Repository name
     * @return Repository info or null
     */
    fun getRepository(owner: String, repo: String): GitHubRepository? {
        val url = "$baseUrl/repos/$owner/$repo"

        return try {
            val response = sendRequest(url)
            if (response.statusCode() == 200) {
                json.decodeFromString<GitHubRepository>(response.body())
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Gets the latest release of a repository.
     *
     * @param owner Repository owner
     * @param repo Repository name
     * @return Latest release or null
     */
    fun getLatestRelease(owner: String, repo: String): GitHubRelease? {
        val url = "$baseUrl/repos/$owner/$repo/releases/latest"

        return try {
            val response = sendRequest(url)
            if (response.statusCode() == 200) {
                json.decodeFromString<GitHubRelease>(response.body())
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Gets all releases of a repository.
     *
     * @param owner Repository owner
     * @param repo Repository name
     * @param perPage Number of results per page (max 100)
     * @return List of releases
     */
    fun getReleases(owner: String, repo: String, perPage: Int = 30): List<GitHubRelease> {
        val url = "$baseUrl/repos/$owner/$repo/releases?per_page=$perPage"

        return try {
            val response = sendRequest(url)
            if (response.statusCode() == 200) {
                json.decodeFromString<List<GitHubRelease>>(response.body())
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Gets repository tags.
     *
     * @param owner Repository owner
     * @param repo Repository name
     * @param perPage Number of results per page
     * @return List of tag names
     */
    fun getTags(owner: String, repo: String, perPage: Int = 30): List<String> {
        val url = "$baseUrl/repos/$owner/$repo/tags?per_page=$perPage"

        return try {
            val response = sendRequest(url)
            if (response.statusCode() == 200) {
                // Parse JSON array of objects with "name" field
                val tagsJson = response.body()
                val tagPattern = """"name"\s*:\s*"([^"]+)"""".toRegex()
                tagPattern.findAll(tagsJson).map { it.groupValues[1] }.toList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Searches for Go repositories.
     *
     * @param query Search query
     * @param perPage Number of results
     * @return Search response
     */
    fun searchRepositories(query: String, perPage: Int = 20): GitHubSearchResponse? {
        val encodedQuery = URLEncoder.encode("$query language:go", Charsets.UTF_8)
        val url = "$baseUrl/search/repositories?q=$encodedQuery&sort=stars&order=desc&per_page=$perPage"

        return try {
            val response = sendRequest(url)
            if (response.statusCode() == 200) {
                json.decodeFromString<GitHubSearchResponse>(response.body())
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Gets repository README content.
     *
     * @param owner Repository owner
     * @param repo Repository name
     * @return README content or null
     */
    fun getReadme(owner: String, repo: String): String? {
        val url = "$baseUrl/repos/$owner/$repo/readme"

        return try {
            val response = sendRequest(url, mapOf("Accept" to "application/vnd.github.raw"))
            if (response.statusCode() == 200) {
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun sendRequest(
        url: String,
        additionalHeaders: Map<String, String> = emptyMap()
    ): HttpResponse<String> {
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", "Golang-Advisor-Plugin/1.0")
            .header("Accept", "application/vnd.github.v3+json")

        additionalHeaders.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }

        val request = requestBuilder.GET().build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }
}
