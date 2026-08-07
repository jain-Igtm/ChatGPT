package com.jane.resident

import android.content.Context
import android.net.Uri
import java.io.File

class ModelStore(private val context: Context) {
    private val modelsDir: File = File(context.filesDir, "models").apply { mkdirs() }

    val primaryModel: File
        get() = File(modelsDir, PRIMARY_MODEL_FILENAME)

    val toolRouterModel: File
        get() = File(modelsDir, TOOL_ROUTER_FILENAME)

    fun primaryState(): ModelFileState = primaryModel.toState("mind")

    fun toolRouterState(): ModelFileState = toolRouterModel.toState("tool_router")

    fun importPrimary(uri: Uri): ModelFileState = importModel(uri, primaryModel, "mind")

    fun importToolRouter(uri: Uri): ModelFileState = importModel(uri, toolRouterModel, "tool_router")

    private fun importModel(uri: Uri, destination: File, role: String): ModelFileState {
        val temporary = File(modelsDir, destination.name + ".importing")
        temporary.delete()

        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected model" }
            temporary.outputStream().buffered().use { output -> input.copyTo(output) }
        }

        require(temporary.length() >= MIN_MODEL_BYTES) {
            "Selected file is too small to be a LiteRT-LM model package"
        }

        if (destination.exists() && !destination.delete()) {
            temporary.delete()
            error("Unable to replace existing model")
        }
        if (!temporary.renameTo(destination)) {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }
        return destination.toState(role)
    }

    private fun File.toState(role: String): ModelFileState = ModelFileState(
        role = role,
        installed = exists() && isFile && length() >= MIN_MODEL_BYTES,
        path = absolutePath,
        bytes = if (exists()) length() else 0L,
    )

    companion object {
        const val PRIMARY_MODEL_FILENAME = "smith-mind.litertlm"
        const val TOOL_ROUTER_FILENAME = "smith-tools.litertlm"
        private const val MIN_MODEL_BYTES = 50L * 1024L * 1024L
    }
}

data class ModelFileState(
    val role: String,
    val installed: Boolean,
    val path: String,
    val bytes: Long,
)
