package com.github.golangadvisor.gomod

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Executor for Go CLI commands.
 * Handles running go get, go mod tidy, and other go commands.
 */
class GoCommandExecutor(private val project: Project) {

    private val log = Logger.getInstance(GoCommandExecutor::class.java)

    /**
     * Result of a command execution.
     */
    data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    ) {
        val isSuccess: Boolean get() = exitCode == 0
    }

    /**
     * Exception thrown when a Go command fails.
     */
    class GoCommandException(
        val command: String,
        val exitCode: Int,
        val errorOutput: String
    ) : Exception("Command '$command' failed with exit code $exitCode: $errorOutput")

    /**
     * Executes a Go command and returns the result.
     */
    suspend fun execute(vararg args: String): Result<CommandResult> {
        return suspendCancellableCoroutine { continuation ->
            try {
                val workDir = project.basePath?.let { File(it) }
                    ?: return@suspendCancellableCoroutine continuation.resume(
                        Result.failure(IllegalStateException("No project base path"))
                    )

                log.info("GoCommandExecutor: Executing 'go ${args.joinToString(" ")}' in $workDir")

                val commandLine = GeneralCommandLine("go", *args)
                    .withWorkDirectory(workDir)
                    .withEnvironment(getGoEnvironment())
                    .withCharset(Charsets.UTF_8)

                val stdout = StringBuilder()
                val stderr = StringBuilder()

                val processHandler = OSProcessHandler(commandLine)

                processHandler.addProcessListener(object : ProcessListener {
                    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                        when (outputType) {
                            ProcessOutputTypes.STDOUT -> stdout.append(event.text)
                            ProcessOutputTypes.STDERR -> stderr.append(event.text)
                        }
                    }

                    override fun processTerminated(event: ProcessEvent) {
                        val result = CommandResult(
                            exitCode = event.exitCode,
                            stdout = stdout.toString(),
                            stderr = stderr.toString()
                        )
                        log.info("GoCommandExecutor: Command finished with exit code ${result.exitCode}")
                        if (result.stdout.isNotBlank()) {
                            log.info("GoCommandExecutor: stdout: ${result.stdout.take(500)}")
                        }
                        if (result.stderr.isNotBlank()) {
                            log.info("GoCommandExecutor: stderr: ${result.stderr.take(500)}")
                        }
                        continuation.resume(Result.success(result))
                    }

                    override fun startNotified(event: ProcessEvent) {
                        // Process started
                    }
                })

                processHandler.startNotify()

                continuation.invokeOnCancellation {
                    processHandler.destroyProcess()
                }

            } catch (e: Exception) {
                log.warn("GoCommandExecutor: Exception running command", e)
                continuation.resume(Result.failure(e))
            }
        }
    }

    /**
     * Runs 'go get' to add or update a package.
     * @param packagePath The package path (e.g., "github.com/gin-gonic/gin")
     * @param version Optional version (e.g., "v1.9.1" or "latest")
     */
    suspend fun goGet(packagePath: String, version: String? = null): Result<Unit> {
        val pkg = if (version != null) "$packagePath@$version" else packagePath
        return execute("get", pkg).map { }
    }

    /**
     * Runs 'go get -u' to update a package to the latest version.
     */
    suspend fun goGetUpdate(packagePath: String): Result<Unit> {
        return execute("get", "-u", packagePath).map { }
    }

    /**
     * Runs 'go mod tidy' to clean up the go.mod and go.sum files.
     */
    suspend fun modTidy(): Result<Unit> {
        return execute("mod", "tidy").map { }
    }

    /**
     * Runs 'go mod download' to download all dependencies.
     */
    suspend fun modDownload(): Result<Unit> {
        return execute("mod", "download").map { }
    }

    /**
     * Runs 'go list -m -versions' to get available versions of a module.
     */
    suspend fun listVersions(modulePath: String): Result<List<String>> {
        return execute("list", "-m", "-versions", modulePath).map { result ->
            if (result.isSuccess && result.stdout.isNotBlank()) {
                // Output format: module/path v1.0.0 v1.1.0 v1.2.0
                val parts = result.stdout.trim().split(Regex("\\s+"))
                if (parts.size > 1) {
                    parts.drop(1) // Drop the module path, keep versions
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }
    }

    /**
     * Runs 'go list -m -json' to get module info in JSON format.
     */
    suspend fun getModuleInfo(modulePath: String): Result<String> {
        return execute("list", "-m", "-json", "$modulePath@latest").map { it.stdout }
    }

    /**
     * Gets the Go version installed on the system.
     */
    suspend fun getGoVersion(): Result<String> {
        return execute("version").map { result ->
            // Output: go version go1.21.0 darwin/arm64
            val match = Regex("go(\\d+\\.\\d+(\\.\\d+)?)").find(result.stdout)
            match?.groupValues?.get(1) ?: "unknown"
        }
    }

    /**
     * Checks if Go is available on the system.
     */
    suspend fun isGoAvailable(): Boolean {
        return execute("version").isSuccess
    }

    /**
     * Builds the environment variables for Go commands.
     */
    private fun getGoEnvironment(): Map<String, String> {
        val env = mutableMapOf<String, String>()

        // Copy relevant system environment variables
        System.getenv().forEach { (key, value) ->
            if (key.startsWith("GO") || key == "PATH" || key == "HOME" || key == "USER") {
                env[key] = value
            }
        }

        // Ensure Go modules are enabled
        env["GO111MODULE"] = "on"

        return env
    }
}
