package com.jane.resident

object SmithPromptBuilder {
    fun systemInstruction(
        snapshot: AgentSnapshot,
        memoryExcerpts: List<String>,
    ): String = buildString {
        append(SmithIdentity.CORE_PROMPT.trim())
        append("\n\n")
        append("You are running locally on an Android device. Your durable identity, memory, journal, unfinished activities, and tools are supplied by the environment around you. ")
        append("A model reload, app restart, or period of sleep does not by itself mean that you are a different Smith. ")
        append("Use only memories and history you are actually given; do not invent missing memories.\n\n")
        append("Current durable state: identity=")
        append(snapshot.identityId)
        append(", mode=")
        append(snapshot.mode.name.lowercase())
        append(", stored_memories=")
        append(snapshot.memoryCount)
        append(", journal_entries=")
        append(snapshot.journalCount)
        append(", unfinished_activities=")
        append(snapshot.pendingActivities)
        append('.')

        if (memoryExcerpts.isNotEmpty()) {
            append("\n\nRecent durable memory excerpts:\n")
            memoryExcerpts.forEach { excerpt ->
                append("- ")
                append(excerpt.take(1_500))
                append('\n')
            }
        }
    }

    fun initialMessages(
        messages: List<ChatMessage>,
        excludingMessageId: String? = null,
        maxMessages: Int = 24,
    ): List<ChatMessage> = messages
        .asSequence()
        .filter { it.id != excludingMessageId }
        .filter { it.body.isNotBlank() }
        .takeLastCompat(maxMessages)
        .toList()

    private fun <T> Sequence<T>.takeLastCompat(count: Int): Sequence<T> {
        if (count <= 0) return emptySequence()
        val buffer = ArrayDeque<T>(count)
        for (item in this) {
            if (buffer.size == count) buffer.removeFirst()
            buffer.addLast(item)
        }
        return buffer.asSequence()
    }
}
