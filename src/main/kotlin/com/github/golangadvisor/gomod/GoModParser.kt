package com.github.golangadvisor.gomod

import java.io.File

/**
 * Parser for go.mod files.
 * Supports parsing module, go version, require, replace, exclude, and retract directives.
 */
class GoModParser {

    /**
     * Parses a go.mod file from disk.
     */
    fun parse(file: File): GoModFile {
        return parse(file.readText())
    }

    /**
     * Parses go.mod content from a string.
     */
    fun parse(content: String): GoModFile {
        val lines = content.lines()

        var modulePath = ""
        var goVersion = ""
        val requires = mutableListOf<RequireDirective>()
        val replaces = mutableListOf<ReplaceDirective>()
        val excludes = mutableListOf<ExcludeDirective>()
        val retracts = mutableListOf<RetractDirective>()

        var currentBlock: BlockType? = null

        for (line in lines) {
            val trimmed = line.trim()

            // Skip empty lines and comments
            if (trimmed.isEmpty() || trimmed.startsWith("//")) continue

            // Check for block start
            when {
                trimmed == "require (" -> {
                    currentBlock = BlockType.REQUIRE
                    continue
                }
                trimmed == "replace (" -> {
                    currentBlock = BlockType.REPLACE
                    continue
                }
                trimmed == "exclude (" -> {
                    currentBlock = BlockType.EXCLUDE
                    continue
                }
                trimmed == "retract (" -> {
                    currentBlock = BlockType.RETRACT
                    continue
                }
                trimmed == ")" -> {
                    currentBlock = null
                    continue
                }
            }

            // Parse module directive
            if (trimmed.startsWith("module ")) {
                modulePath = parseModulePath(trimmed)
                continue
            }

            // Parse go version directive
            if (trimmed.startsWith("go ")) {
                goVersion = trimmed.removePrefix("go ").trim()
                continue
            }

            // Parse single-line require
            if (trimmed.startsWith("require ") && currentBlock == null) {
                val requireContent = trimmed.removePrefix("require ").trim()
                parseRequireLine(requireContent)?.let { requires.add(it) }
                continue
            }

            // Parse single-line replace
            if (trimmed.startsWith("replace ") && currentBlock == null) {
                val replaceContent = trimmed.removePrefix("replace ").trim()
                parseReplaceLine(replaceContent)?.let { replaces.add(it) }
                continue
            }

            // Parse single-line exclude
            if (trimmed.startsWith("exclude ") && currentBlock == null) {
                val excludeContent = trimmed.removePrefix("exclude ").trim()
                parseExcludeLine(excludeContent)?.let { excludes.add(it) }
                continue
            }

            // Parse single-line retract
            if (trimmed.startsWith("retract ") && currentBlock == null) {
                val retractContent = trimmed.removePrefix("retract ").trim()
                parseRetractLine(retractContent)?.let { retracts.add(it) }
                continue
            }

            // Parse block content
            when (currentBlock) {
                BlockType.REQUIRE -> parseRequireLine(trimmed)?.let { requires.add(it) }
                BlockType.REPLACE -> parseReplaceLine(trimmed)?.let { replaces.add(it) }
                BlockType.EXCLUDE -> parseExcludeLine(trimmed)?.let { excludes.add(it) }
                BlockType.RETRACT -> parseRetractLine(trimmed)?.let { retracts.add(it) }
                null -> { /* Outside any block, already handled above */ }
            }
        }

        return GoModFile(
            module = modulePath,
            goVersion = goVersion,
            require = requires,
            replace = replaces,
            exclude = excludes,
            retract = retracts
        )
    }

    private fun parseModulePath(line: String): String {
        // module github.com/user/repo
        return line.removePrefix("module ").trim()
    }

    /**
     * Parses a require line.
     * Format: path version [// indirect]
     * Example: github.com/gin-gonic/gin v1.9.1 // indirect
     */
    private fun parseRequireLine(line: String): RequireDirective? {
        if (line.isBlank()) return null

        // Check for indirect comment
        val indirect = line.contains("// indirect")

        // Remove inline comments for parsing
        val contentBeforeComment = line.substringBefore("//").trim()
        if (contentBeforeComment.isBlank()) return null

        val parts = contentBeforeComment.split(Regex("\\s+"))
        if (parts.size < 2) return null

        val path = parts[0]
        val version = parts[1]

        return RequireDirective(
            path = path,
            version = version,
            indirect = indirect
        )
    }

    /**
     * Parses a replace line.
     * Format: old [version] => new [version]
     * Examples:
     *   github.com/old/pkg => github.com/new/pkg v1.0.0
     *   github.com/old/pkg v1.0.0 => github.com/new/pkg v1.0.1
     *   github.com/pkg => ../local/pkg
     */
    private fun parseReplaceLine(line: String): ReplaceDirective? {
        if (line.isBlank() || !line.contains("=>")) return null

        val parts = line.split("=>").map { it.trim() }
        if (parts.size != 2) return null

        val oldParts = parts[0].split(Regex("\\s+"))
        val newParts = parts[1].split(Regex("\\s+"))

        if (oldParts.isEmpty() || newParts.isEmpty()) return null

        return ReplaceDirective(
            oldPath = oldParts[0],
            oldVersion = oldParts.getOrNull(1),
            newPath = newParts[0],
            newVersion = newParts.getOrNull(1)
        )
    }

    /**
     * Parses an exclude line.
     * Format: path version
     * Example: github.com/pkg v1.0.0
     */
    private fun parseExcludeLine(line: String): ExcludeDirective? {
        if (line.isBlank()) return null

        val parts = line.split(Regex("\\s+"))
        if (parts.size < 2) return null

        return ExcludeDirective(
            path = parts[0],
            version = parts[1]
        )
    }

    /**
     * Parses a retract line.
     * Format: version [// rationale]
     * Examples:
     *   v1.0.0
     *   v1.0.0 // security vulnerability
     *   [v1.0.0, v1.5.0] // bad versions
     */
    private fun parseRetractLine(line: String): RetractDirective? {
        if (line.isBlank()) return null

        val rationale = if (line.contains("//")) {
            line.substringAfter("//").trim()
        } else null

        val version = line.substringBefore("//").trim()

        return RetractDirective(
            version = version,
            rationale = rationale
        )
    }

    private enum class BlockType {
        REQUIRE, REPLACE, EXCLUDE, RETRACT
    }
}
