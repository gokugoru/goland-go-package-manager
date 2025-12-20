package com.github.golangadvisor.util

/**
 * Comparator for semantic versions.
 *
 * Supports:
 * - Standard semver: v1.2.3
 * - Pre-release versions: v1.2.3-alpha, v1.2.3-beta.1
 * - Build metadata: v1.2.3+build
 * - Pseudo-versions: v0.0.0-20210101120000-abcdef123456
 */
class VersionComparator : Comparator<String> {

    override fun compare(v1: String, v2: String): Int {
        val parsed1 = parseVersion(v1)
        val parsed2 = parseVersion(v2)

        // Compare major, minor, patch
        for (i in 0 until 3) {
            val cmp = parsed1.parts.getOrElse(i) { 0 } - parsed2.parts.getOrElse(i) { 0 }
            if (cmp != 0) return cmp
        }

        // If main version is equal, compare pre-release
        // No pre-release > has pre-release
        if (parsed1.prerelease == null && parsed2.prerelease != null) return 1
        if (parsed1.prerelease != null && parsed2.prerelease == null) return -1
        if (parsed1.prerelease != null && parsed2.prerelease != null) {
            return comparePrerelease(parsed1.prerelease, parsed2.prerelease)
        }

        return 0
    }

    /**
     * Checks if version1 is newer than version2.
     */
    fun isNewer(version1: String, version2: String): Boolean {
        return compare(version1, version2) > 0
    }

    /**
     * Checks if version1 is older than version2.
     */
    fun isOlder(version1: String, version2: String): Boolean {
        return compare(version1, version2) < 0
    }

    /**
     * Checks if two versions are equal.
     */
    fun areEqual(version1: String, version2: String): Boolean {
        return compare(version1, version2) == 0
    }

    /**
     * Returns a reversed comparator (newest first).
     */
    override fun reversed(): Comparator<String> {
        return Comparator { v1, v2 -> compare(v2, v1) }
    }

    /**
     * Validates if a string is a valid version.
     */
    fun isValidVersion(version: String): Boolean {
        val pattern = """^v?\d+(\.\d+)*(-[a-zA-Z0-9.-]+)?(\+[a-zA-Z0-9.-]+)?$""".toRegex()
        return pattern.matches(version)
    }

    /**
     * Normalizes a version string (adds 'v' prefix if missing).
     */
    fun normalize(version: String): String {
        return if (version.startsWith("v")) version else "v$version"
    }

    /**
     * Removes 'v' prefix from version.
     */
    fun stripPrefix(version: String): String {
        return version.removePrefix("v")
    }

    private fun parseVersion(version: String): ParsedVersion {
        // Remove 'v' prefix
        var v = version.removePrefix("v")

        // Extract build metadata (after +)
        val buildIndex = v.indexOf('+')
        val build = if (buildIndex >= 0) {
            val b = v.substring(buildIndex + 1)
            v = v.substring(0, buildIndex)
            b
        } else null

        // Extract pre-release (after -)
        val prereleaseIndex = v.indexOf('-')
        val prerelease = if (prereleaseIndex >= 0) {
            val p = v.substring(prereleaseIndex + 1)
            v = v.substring(0, prereleaseIndex)
            p
        } else null

        // Parse version parts
        val parts = v.split('.')
            .mapNotNull { it.toIntOrNull() }

        return ParsedVersion(parts, prerelease, build)
    }

    private fun comparePrerelease(p1: String, p2: String): Int {
        val parts1 = p1.split('.')
        val parts2 = p2.split('.')

        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val part1 = parts1.getOrNull(i)
            val part2 = parts2.getOrNull(i)

            if (part1 == null) return -1
            if (part2 == null) return 1

            val num1 = part1.toIntOrNull()
            val num2 = part2.toIntOrNull()

            val cmp = when {
                num1 != null && num2 != null -> num1 - num2
                num1 != null -> -1 // Numbers before strings
                num2 != null -> 1
                else -> part1.compareTo(part2)
            }

            if (cmp != 0) return cmp
        }

        return 0
    }

    private data class ParsedVersion(
        val parts: List<Int>,
        val prerelease: String?,
        val build: String?
    )

    companion object {
        /**
         * Default instance for convenience.
         */
        val DEFAULT = VersionComparator()

        /**
         * Sorts a list of versions, newest first.
         */
        fun sortNewestFirst(versions: List<String>): List<String> {
            return versions.sortedWith(DEFAULT.reversed())
        }

        /**
         * Sorts a list of versions, oldest first.
         */
        fun sortOldestFirst(versions: List<String>): List<String> {
            return versions.sortedWith(DEFAULT)
        }

        /**
         * Gets the newest version from a list.
         */
        fun newest(versions: List<String>): String? {
            return versions.maxWithOrNull(DEFAULT)
        }

        /**
         * Gets the oldest version from a list.
         */
        fun oldest(versions: List<String>): String? {
            return versions.minWithOrNull(DEFAULT)
        }
    }
}
