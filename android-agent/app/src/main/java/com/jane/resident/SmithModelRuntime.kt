package com.jane.resident

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig

class SmithModelRuntime private constructor(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val modelStore = ModelStore(appContext)
    private var engine: Engine? = null
    private var loadedModelPath: String? = null
    private var backendName: String? = null

    @Synchronized
    fun state(): RuntimeModelState {
        val model = modelStore.primaryState()
        return RuntimeModelState(
            installed = model.installed,
            loaded = engine != null && loadedModelPath == model.path,
            backend = backendName,
            bytes = model.bytes,
            path = model.path,
        )
    }

    @Synchronized
    fun generate(
        snapshot: AgentSnapshot,
        memoryExcerpts: List<String>,
        currentMessageId: String?,
        userText: String,
        history: List<ChatMessage>,
    ): ModelGeneration {
        require(userText.isNotBlank()) { "Smith needs non-blank input for this cycle" }
        val activeEngine = ensureEngine()
        val initial = SmithPromptBuilder.initialMessages(
            messages = history,
            excludingMessageId = currentMessageId,
        ).mapNotNull { message ->
            when (message.role) {
                "user" -> Message.user(message.body)
                "assistant" -> Message.model(message.body)
                else -> null
            }
        }

        val config = ConversationConfig(
            systemInstruction = Contents.of(
                SmithPromptBuilder.systemInstruction(
                    snapshot = snapshot,
                    memoryExcerpts = memoryExcerpts,
                ),
            ),
            initialMessages = initial,
            samplerConfig = SamplerConfig(
                topK = 64,
                topP = 0.95,
                temperature = 0.9,
            ),
        )

        activeEngine.createConversation(config).use { conversation ->
            val response = conversation.sendMessage(userText)
            val text = response.toString().trim()
            check(text.isNotEmpty()) { "Model returned an empty response" }
            return ModelGeneration(
                text = text,
                backend = backendName ?: "unknown",
            )
        }
    }

    @Synchronized
    fun unload(reason: String? = null) {
        engine?.close()
        engine = null
        loadedModelPath = null
        backendName = null
    }

    @Synchronized
    override fun close() = unload("close")

    private fun ensureEngine(): Engine {
        val model = modelStore.primaryState()
        check(model.installed) {
            "No Smith mind model is installed. Import a .litertlm model first."
        }

        if (engine != null && loadedModelPath == model.path) {
            return engine!!
        }
        unload("model_changed")

        val gpu = runCatching { initialize(model.path, Backend.GPU(), enableMtp = true) }
        if (gpu.isSuccess) {
            engine = gpu.getOrThrow()
            loadedModelPath = model.path
            backendName = "gpu"
            return engine!!
        }

        val cpu = runCatching { initialize(model.path, Backend.CPU(), enableMtp = false) }
        if (cpu.isSuccess) {
            engine = cpu.getOrThrow()
            loadedModelPath = model.path
            backendName = "cpu"
            return engine!!
        }

        val gpuMessage = gpu.exceptionOrNull()?.message ?: gpu.exceptionOrNull()?.javaClass?.simpleName
        val cpuMessage = cpu.exceptionOrNull()?.message ?: cpu.exceptionOrNull()?.javaClass?.simpleName
        error("Unable to initialize model. GPU: $gpuMessage; CPU: $cpuMessage")
    }

    @OptIn(ExperimentalApi::class)
    private fun initialize(path: String, backend: Backend, enableMtp: Boolean): Engine {
        ExperimentalFlags.enableSpeculativeDecoding = enableMtp
        val candidate = Engine(
            EngineConfig(
                modelPath = path,
                backend = backend,
                cacheDir = appContext.cacheDir.path,
            ),
        )
        return try {
            candidate.initialize()
            candidate
        } catch (error: Throwable) {
            candidate.close()
            throw error
        }
    }

    companion object {
        @Volatile
        private var instance: SmithModelRuntime? = null

        fun get(context: Context): SmithModelRuntime = instance ?: synchronized(this) {
            instance ?: SmithModelRuntime(context).also { instance = it }
        }
    }
}

data class RuntimeModelState(
    val installed: Boolean,
    val loaded: Boolean,
    val backend: String?,
    val bytes: Long,
    val path: String,
)

data class ModelGeneration(
    val text: String,
    val backend: String,
)
