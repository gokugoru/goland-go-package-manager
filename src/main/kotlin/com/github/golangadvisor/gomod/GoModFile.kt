package com.github.golangadvisor.gomod

/**
 * Represents a parsed go.mod file.
 */
data class GoModFile(
    val module: String,
    val goVersion: String,
    val require: List<RequireDirective>,
    val replace: List<ReplaceDirective> = emptyList(),
    val exclude: List<ExcludeDirective> = emptyList(),
    val retract: List<RetractDirective> = emptyList()
)

/**
 * Represents a require directive in go.mod.
 * Example: github.com/gin-gonic/gin v1.9.1
 */
data class RequireDirective(
    val path: String,
    val version: String,
    val indirect: Boolean = false
)

/**
 * Represents a replace directive in go.mod.
 * Example: github.com/old/pkg => github.com/new/pkg v1.0.0
 */
data class ReplaceDirective(
    val oldPath: String,
    val oldVersion: String?,
    val newPath: String,
    val newVersion: String?
)

/**
 * Represents an exclude directive in go.mod.
 * Example: exclude github.com/pkg v1.0.0
 */
data class ExcludeDirective(
    val path: String,
    val version: String
)

/**
 * Represents a retract directive in go.mod.
 * Example: retract v1.0.0
 */
data class RetractDirective(
    val version: String,
    val rationale: String? = null
)
