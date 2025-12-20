package com.github.golangadvisor.services

import com.github.golangadvisor.gomod.GoCommandExecutor
import com.github.golangadvisor.gomod.GoModDependency
import com.github.golangadvisor.gomod.GoModFile
import com.github.golangadvisor.gomod.GoModParser
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Project-level service for managing go.mod file operations.
 *
 * Responsibilities:
 * - Parsing go.mod
 * - Adding/removing/updating dependencies
 * - Running go commands
 * - Notifying listeners about changes
 */
@Service(Service.Level.PROJECT)
class GoModService(
    private val project: Project
) : Disposable {

    private val log = Logger.getInstance(GoModService::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val parser = GoModParser()
    private val commandExecutor = GoCommandExecutor(project)

    private val _dependencies = MutableStateFlow<List<GoModDependency>>(emptyList())
    val dependencies: StateFlow<List<GoModDependency>> = _dependencies.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var cachedGoModFile: GoModFile? = null
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    init {
        setupFileWatcher()
        // Initial load
        scope.launch {
            refresh()
        }
    }

    private fun setupFileWatcher() {
        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    val hasGoModChange = events.any { event ->
                        event.file?.name == "go.mod" || event.path.endsWith("go.mod")
                    }
                    if (hasGoModChange) {
                        invalidateCache()
                        scope.launch { refresh() }
                        notifyListeners()
                    }
                }
            }
        )
    }

    /**
     * Checks if go.mod file exists in the project.
     */
    fun hasGoModFile(): Boolean {
        return findGoModFile() != null
    }

    /**
     * Finds the go.mod file in the project.
     */
    fun findGoModFile(): File? {
        val basePath = project.basePath ?: return null
        val goModFile = File(basePath, "go.mod")
        return if (goModFile.exists()) goModFile else null
    }

    /**
     * Gets the parsed go.mod file.
     */
    fun getGoModFile(): GoModFile? {
        if (cachedGoModFile != null) return cachedGoModFile

        val file = findGoModFile() ?: return null
        cachedGoModFile = parser.parse(file)
        return cachedGoModFile
    }

    /**
     * Refreshes the dependency list from go.mod.
     */
    suspend fun refresh() {
        _isLoading.value = true
        _error.value = null

        try {
            val goModFile = withContext(Dispatchers.IO) {
                invalidateCache()
                getGoModFile()
            }

            if (goModFile == null) {
                _dependencies.value = emptyList()
                _error.value = "go.mod file not found"
                return
            }

            val deps = goModFile.require.map { require ->
                GoModDependency.fromRequireDirective(require)
            }

            _dependencies.value = deps

        } catch (e: Exception) {
            log.error("Failed to parse go.mod", e)
            _error.value = "Failed to parse go.mod: ${e.message}"
            _dependencies.value = emptyList()
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Gets all dependencies from go.mod.
     */
    fun getDependencies(): List<GoModDependency> {
        return _dependencies.value
    }

    /**
     * Gets direct dependencies only (non-indirect).
     */
    fun getDirectDependencies(): List<GoModDependency> {
        return _dependencies.value.filter { !it.isIndirect }
    }

    /**
     * Gets indirect dependencies only.
     */
    fun getIndirectDependencies(): List<GoModDependency> {
        return _dependencies.value.filter { it.isIndirect }
    }

    /**
     * Adds a new package to the project.
     *
     * @param packagePath The package import path
     * @param version Optional version (defaults to "latest")
     */
    suspend fun addPackage(packagePath: String, version: String? = null): Result<Unit> {
        _isLoading.value = true
        _error.value = null

        return try {
            val versionSpec = version ?: "latest"

            val result = withContext(Dispatchers.IO) {
                commandExecutor.goGet(packagePath, versionSpec)
            }

            result.onSuccess {
                invalidateCache()
                refresh()
            }.onFailure { e ->
                _error.value = "Failed to add package: ${e.message}"
            }

            result

        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Removes a package from the project.
     *
     * @param packagePath The package import path to remove
     */
    suspend fun removePackage(packagePath: String): Result<Unit> {
        _isLoading.value = true
        _error.value = null

        return try {
            val result = withContext(Dispatchers.IO) {
                commandExecutor.execute("mod", "edit", "-droprequire", packagePath)
            }

            result.onSuccess {
                invalidateCache()
                refresh()
            }.onFailure { e ->
                _error.value = "Failed to remove package: ${e.message}"
            }

            result.map { }

        } catch (e: Exception) {
            _error.value = "Failed to remove package: ${e.message}"
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Updates a package to a specific version.
     *
     * @param packagePath The package import path
     * @param newVersion The new version to update to
     */
    suspend fun updatePackage(packagePath: String, newVersion: String): Result<Unit> {
        _isLoading.value = true
        _error.value = null

        return try {
            val result = withContext(Dispatchers.IO) {
                commandExecutor.goGet(packagePath, newVersion)
            }

            result.onSuccess {
                invalidateCache()
                refresh()
            }.onFailure { e ->
                _error.value = "Failed to update package: ${e.message}"
            }

            result

        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Updates all packages to their latest versions.
     */
    suspend fun updateAllPackages(): Result<Unit> {
        _isLoading.value = true
        _error.value = null

        return try {
            val result = withContext(Dispatchers.IO) {
                commandExecutor.execute("get", "-u", "./...")
            }

            result.map { }.onSuccess {
                invalidateCache()
                refresh()
            }.onFailure { e ->
                _error.value = "Failed to update packages: ${e.message}"
            }

        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Runs go mod tidy.
     */
    suspend fun runModTidy(): Result<Unit> {
        _isLoading.value = true

        return try {
            val result = withContext(Dispatchers.IO) {
                commandExecutor.modTidy()
            }

            result.onSuccess {
                invalidateCache()
                refresh()
            }

            result

        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Gets available versions for a package.
     */
    suspend fun getAvailableVersions(packagePath: String): List<String> {
        return withContext(Dispatchers.IO) {
            commandExecutor.listVersions(packagePath).getOrElse { emptyList() }
        }
    }

    /**
     * Removes a package line from go.mod content.
     */
    private fun removePackageFromContent(content: String, packagePath: String): String {
        val lines = content.lines().toMutableList()
        val iterator = lines.iterator()

        while (iterator.hasNext()) {
            val line = iterator.next().trim()

            // Remove single-line require
            if (line.startsWith("require ") && line.contains(packagePath)) {
                iterator.remove()
                continue
            }

            // Remove from require block
            if (line.startsWith(packagePath) && !line.startsWith("module ")) {
                iterator.remove()
            }
        }

        return lines.joinToString("\n")
    }

    /**
     * Invalidates the cached go.mod file.
     */
    private fun invalidateCache() {
        cachedGoModFile = null
    }

    /**
     * Adds a listener for changes.
     */
    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    /**
     * Removes a change listener.
     */
    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it() }
    }

    override fun dispose() {
        listeners.clear()
    }
}
