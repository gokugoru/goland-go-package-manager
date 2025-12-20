package com.github.golangadvisor.api.client

import com.github.golangadvisor.api.model.PackageInfo
import com.github.golangadvisor.api.model.SearchResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Client for pkg.go.dev.
 *
 * Uses the internal search API endpoint that returns JSON.
 */
class PkgGoDevClient {

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val baseUrl = "https://pkg.go.dev"

    /**
     * Searches for packages using the autocomplete API.
     *
     * @param query Search query
     * @param limit Maximum number of results
     * @return List of search results
     */
    fun search(query: String, limit: Int = 20): List<SearchResult> {
        if (query.isBlank()) return emptyList()

        // Try the search completion API first (JSON)
        val completionResults = searchViaCompletion(query, limit)
        if (completionResults.isNotEmpty()) {
            return completionResults
        }

        // Fallback: parse HTML search results
        return searchViaHtml(query, limit)
    }

    /**
     * Uses the search completion/autocomplete API.
     */
    private fun searchViaCompletion(query: String, limit: Int): List<SearchResult> {
        val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8)
        val url = "$baseUrl/search-suggestions?q=$encodedQuery&limit=$limit"

        return try {
            val response = sendRequest(url, acceptJson = true)
            if (response.statusCode() == 200) {
                parseCompletionResults(response.body())
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Fallback: parse HTML search page.
     */
    private fun searchViaHtml(query: String, limit: Int): List<SearchResult> {
        val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8)
        val url = "$baseUrl/search?q=$encodedQuery&m=package"

        return try {
            val response = sendRequest(url, acceptJson = false)
            if (response.statusCode() == 200) {
                parseSearchResultsHtml(response.body(), limit)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Gets package information.
     *
     * @param packagePath The package import path
     * @return Package info or null
     */
    fun getPackageInfo(packagePath: String): PackageInfo? {
        val url = "$baseUrl/$packagePath"

        return try {
            val response = sendRequest(url, acceptJson = false)
            if (response.statusCode() == 200) {
                parsePackageInfo(packagePath, response.body())
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Gets package documentation.
     *
     * @param packagePath The package import path
     * @param version Optional version
     * @return Documentation HTML or null
     */
    fun getDocumentation(packagePath: String, version: String? = null): String? {
        val url = if (version != null) {
            "$baseUrl/$packagePath@$version"
        } else {
            "$baseUrl/$packagePath"
        }

        return try {
            val response = sendRequest(url, acceptJson = false)
            if (response.statusCode() == 200) {
                extractDocumentation(response.body())
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parses completion/autocomplete API results.
     */
    private fun parseCompletionResults(responseBody: String): List<SearchResult> {
        return try {
            // The completion API returns array of suggestions
            val suggestions = json.decodeFromString<List<SearchSuggestion>>(responseBody)
            suggestions.map { suggestion ->
                SearchResult(
                    path = suggestion.packagePath ?: suggestion.name,
                    synopsis = suggestion.synopsis ?: "",
                    latestVersion = suggestion.version,
                    license = null,
                    stars = null
                )
            }
        } catch (e: Exception) {
            // Try parsing as different format
            try {
                val wrapper = json.decodeFromString<SearchSuggestionsWrapper>(responseBody)
                wrapper.results.map { result ->
                    SearchResult(
                        path = result.packagePath,
                        synopsis = result.synopsis ?: "",
                        latestVersion = result.version,
                        license = null,
                        stars = null
                    )
                }
            } catch (e2: Exception) {
                emptyList()
            }
        }
    }

    /**
     * Parses search results from HTML.
     * Structure: <div class="search-result"><h2><a href="/path">name</a></h2><p>synopsis</p>...
     */
    private fun parseSearchResultsHtml(html: String, limit: Int): List<SearchResult> {
        val results = mutableListOf<SearchResult>()

        // Split by search-result divs
        val searchResultPattern = """<div[^>]*class="[^"]*search-result[^"]*"[^>]*>([\s\S]*?)</div>\s*(?=<div[^>]*class="[^"]*search-result|$)""".toRegex()
        val resultBlocks = searchResultPattern.findAll(html).toList()

        // If that doesn't work, try splitting by h2 tags in search context
        val blocks = if (resultBlocks.isEmpty()) {
            // Alternative: find h2 with links to packages
            """<h2[^>]*>\s*<a[^>]*href="/([^"@?]+)[^"]*"[^>]*>[\s\S]*?</h2>\s*<p>([^<]*)</p>""".toRegex()
                .findAll(html)
                .map { match ->
                    Pair(match.groupValues[1], match.groupValues[2])
                }
                .toList()
        } else {
            resultBlocks.mapNotNull { block ->
                val blockHtml = block.groupValues[1]
                // Extract path from <h2><a href="/path">
                val pathMatch = """<h2[^>]*>\s*<a[^>]*href="/([^"@?]+)""".toRegex().find(blockHtml)
                // Extract synopsis from first <p> tag
                val synopsisMatch = """<p[^>]*>([^<]+)</p>""".toRegex().find(blockHtml)

                if (pathMatch != null) {
                    Pair(pathMatch.groupValues[1], synopsisMatch?.groupValues?.get(1) ?: "")
                } else null
            }
        }

        for ((path, synopsis) in blocks) {
            val cleanPath = path.removePrefix("/").trim()

            // Validate path looks like a Go module
            if (cleanPath.isNotBlank() &&
                cleanPath.contains(".") &&
                cleanPath.contains("/") &&
                !cleanPath.startsWith("about") &&
                !cleanPath.startsWith("search") &&
                !cleanPath.startsWith("badge") &&
                !cleanPath.startsWith("license")
            ) {
                if (results.none { it.path == cleanPath }) {
                    results.add(
                        SearchResult(
                            path = cleanPath,
                            synopsis = synopsis.trim(),
                            latestVersion = null,
                            license = null,
                            stars = null
                        )
                    )

                    if (results.size >= limit) break
                }
            }
        }

        // If still empty, try a simpler pattern
        if (results.isEmpty()) {
            val simplePattern = """href="/([a-z][a-z0-9._-]*\.[a-z]+/[^"@?]+)"""".toRegex(RegexOption.IGNORE_CASE)
            simplePattern.findAll(html).forEach { match ->
                val path = match.groupValues[1].removePrefix("/").trim()
                if (path.isNotBlank() &&
                    results.none { it.path == path } &&
                    !path.contains("about") &&
                    !path.contains("search") &&
                    !path.contains("license") &&
                    !path.contains("badge")) {
                    results.add(SearchResult(path = path, synopsis = "", latestVersion = null, license = null, stars = null))
                    if (results.size >= limit) return@forEach
                }
            }
        }

        return results.take(limit)
    }

    /**
     * Parses package info from HTML.
     */
    private fun parsePackageInfo(path: String, html: String): PackageInfo? {
        return try {
            // Extract synopsis from meta description
            val synopsisPattern = """<meta name="description" content="([^"]*)"[^>]*>""".toRegex()
            val synopsis = synopsisPattern.find(html)?.groupValues?.get(1) ?: ""

            // Extract license
            val licensePatterns = listOf(
                """data-test-id="UnitHeader-license"[^>]*>([^<]+)<""".toRegex(),
                """License[^>]*>[\s\S]*?<a[^>]*>([^<]+)</a>""".toRegex(),
                """licenses?[^>]*>([A-Z0-9-]+)</""".toRegex(RegexOption.IGNORE_CASE)
            )
            var license: String? = null
            for (pattern in licensePatterns) {
                license = pattern.find(html)?.groupValues?.get(1)?.trim()
                if (license != null) break
            }

            // Extract latest version
            val versionPatterns = listOf(
                """data-test-id="UnitHeader-version"[^>]*>([^<]+)<""".toRegex(),
                """@(v[\d.]+(?:-[a-zA-Z0-9.]+)?)""".toRegex()
            )
            var latestVersion: String? = null
            for (pattern in versionPatterns) {
                latestVersion = pattern.find(html)?.groupValues?.get(1)?.trim()
                if (latestVersion != null) break
            }

            // Extract repository link
            val repoPattern = """Repository[^>]*>.*?href="([^"]+)"[^>]*>""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val repository = repoPattern.find(html)?.groupValues?.get(1)

            PackageInfo(
                path = path,
                synopsis = synopsis.substringBefore(" - ").trim(),
                description = synopsis,
                license = license,
                latestVersion = latestVersion,
                repository = repository
            )
        } catch (e: Exception) {
            PackageInfo(path = path)
        }
    }

    /**
     * Extracts documentation content from HTML.
     */
    private fun extractDocumentation(html: String): String? {
        // Look for the documentation section
        val docPattern = """<section class="Documentation[^"]*"[^>]*>([\s\S]*?)</section>""".toRegex()
        return docPattern.find(html)?.groupValues?.get(1)
    }

    private fun sendRequest(url: String, acceptJson: Boolean): HttpResponse<String> {
        val acceptHeader = if (acceptJson) {
            "application/json, text/plain, */*"
        } else {
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept", acceptHeader)
            .header("Accept-Language", "en-US,en;q=0.9")
            .GET()
            .build()

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    @Serializable
    private data class SearchSuggestion(
        val name: String = "",
        val packagePath: String? = null,
        val synopsis: String? = null,
        val version: String? = null
    )

    @Serializable
    private data class SearchSuggestionsWrapper(
        val results: List<SearchSuggestionResult> = emptyList()
    )

    @Serializable
    private data class SearchSuggestionResult(
        val packagePath: String = "",
        val synopsis: String? = null,
        val version: String? = null
    )
}
