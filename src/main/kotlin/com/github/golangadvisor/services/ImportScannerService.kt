package com.github.golangadvisor.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Service(Service.Level.PROJECT)
class ImportScannerService(
    private val project: Project
) {
    private val singleImportRegex = Regex("""import\s+"([^"]+)"""")
    private val blockImportRegex = Regex("""import\s*\(\s*([\s\S]*?)\s*\)""")
    private val importLineRegex = Regex("""(?:[\w_]+\s+)?"([^"]+)"""")

    suspend fun scanImports(): Set<String> {
        return withContext(Dispatchers.IO) {
            val basePath = project.basePath ?: return@withContext emptySet()
            val projectDir = File(basePath)

            if (!projectDir.exists()) {
                return@withContext emptySet()
            }

            val imports = mutableSetOf<String>()

            projectDir.walkTopDown()
                .filter { it.isFile && it.extension == "go" }
                .filter { !isVendorOrTestdata(it, projectDir) }
                .forEach { file ->
                    try {
                        imports.addAll(extractImports(file.readText()))
                    } catch (_: Exception) {
                    }
                }

            imports
        }
    }

    fun isPackageUsedInCode(packagePath: String, imports: Set<String>): Boolean {
        return imports.any { importPath ->
            importPath == packagePath ||
            importPath.startsWith("$packagePath/") ||
            packagePath.startsWith("$importPath/")
        }
    }

    private fun extractImports(sourceCode: String): Set<String> {
        val imports = mutableSetOf<String>()

        singleImportRegex.findAll(sourceCode).forEach { match ->
            val importPath = match.groupValues[1]
            if (isExternalImport(importPath)) {
                imports.add(importPath)
            }
        }

        blockImportRegex.findAll(sourceCode).forEach { match ->
            val block = match.groupValues[1]
            importLineRegex.findAll(block).forEach { lineMatch ->
                val importPath = lineMatch.groupValues[1]
                if (isExternalImport(importPath)) {
                    imports.add(importPath)
                }
            }
        }

        return imports
    }

    private fun isExternalImport(importPath: String): Boolean {
        val firstSegment = importPath.substringBefore('/')
        return firstSegment.contains('.')
    }

    private fun isVendorOrTestdata(file: File, projectDir: File): Boolean {
        val relativePath = file.relativeTo(projectDir).path
        return relativePath.contains("vendor${File.separator}") ||
                relativePath.contains("testdata${File.separator}") ||
                relativePath.startsWith("vendor${File.separator}") ||
                relativePath.startsWith("testdata${File.separator}")
    }

    companion object {
        fun getInstance(project: Project): ImportScannerService {
            return project.getService(ImportScannerService::class.java)
        }
    }
}
