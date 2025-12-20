package com.github.golangadvisor.api.client

import com.github.golangadvisor.api.model.ModuleVersion
import com.github.golangadvisor.util.VersionComparator
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Client for Go Module Proxy (proxy.golang.org).
 *
 * Documentation: https://go.dev/ref/mod#module-proxy
 *
 * Endpoints:
 * - GET $base/$module/@v/list - list versions
 * - GET $base/$module/@latest - get latest version info
 * - GET $base/$module/@v/$version.info - get version info
 * - GET $base/$module/@v/$version.mod - get go.mod file
 */
class GoProxyClient {

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val versionComparator = VersionComparator()

    private val baseUrl = "https://proxy.golang.org"

    /**
     * Gets all available versions of a module.
     *
     * @param modulePath The module path (e.g., "github.com/gin-gonic/gin")
     * @return List of versions, sorted newest first
     */
    fun getVersions(modulePath: String): List<String> {
        val encodedPath = encodeModulePath(modulePath)
        val url = "$baseUrl/$encodedPath/@v/list"

        return try {
            val response = sendRequest(url)
            if (response.statusCode() == 200) {
                response.body()
                    .lines()
                    .filter { it.isNotBlank() }
                    .sortedWith(versionComparator.reversed())
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Gets the latest version of a module.
     *
     * @param modulePath The module path
     * @return Latest version or null if not found
     */
    fun getLatestVersion(modulePath: String): String? {
        val encodedPath = encodeModulePath(modulePath)
        val url = "$baseUrl/$encodedPath/@latest"

        return try {
            val response = sendRequest(url)
            if (response.statusCode() == 200) {
                val versionInfo = json.decodeFromString<ModuleVersion>(response.body())
                versionInfo.version
            } else {
                // Fallback: get from version list
                getVersions(modulePath).firstOrNull()
            }
        } catch (e: Exception) {
            // Fallback to version list
            try {
                getVersions(modulePath).firstOrNull()
            } catch (e2: Exception) {
                null
            }
        }
    }

    /**
     * Gets version info for a specific version.
     *
     * @param modulePath The module path
     * @param version The version (e.g., "v1.9.1")
     * @return Version info or null
     */
    fun getVersionInfo(modulePath: String, version: String): ModuleVersion? {
        val encodedPath = encodeModulePath(modulePath)
        val url = "$baseUrl/$encodedPath/@v/$version.info"

        return try {
            val response = sendRequest(url)
            if (response.statusCode() == 200) {
                json.decodeFromString<ModuleVersion>(response.body())
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Gets the go.mod file for a specific version.
     *
     * @param modulePath The module path
     * @param version The version
     * @return go.mod content or null
     */
    fun getGoMod(modulePath: String, version: String): String? {
        val encodedPath = encodeModulePath(modulePath)
        val url = "$baseUrl/$encodedPath/@v/$version.mod"

        return try {
            val response = sendRequest(url)
            if (response.statusCode() == 200) {
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if a module exists.
     *
     * @param modulePath The module path
     * @return true if the module exists
     */
    fun moduleExists(modulePath: String): Boolean {
        return getLatestVersion(modulePath) != null
    }

    /**
     * Encodes a module path for use in URLs.
     *
     * According to the Go module proxy protocol, uppercase letters
     * must be escaped as !lowercase.
     */
    private fun encodeModulePath(path: String): String {
        return path.map { char ->
            if (char.isUpperCase()) {
                "!${char.lowercaseChar()}"
            } else {
                char.toString()
            }
        }.joinToString("")
    }

    private fun sendRequest(url: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", "Golang-Advisor-Plugin/1.0")
            .GET()
            .build()

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }
}
