package com.example.pirt.model

data class WorkspaceConfig(val rootPath: String)

/** A direct view of a Pi session. Draft instances exist only in memory until Pi writes JSONL. */
data class PiSession(
    /** Process/UI identity. For persisted sessions this is the Pi id; drafts use an ephemeral handle. */
    val runtimeKey: String,
    val id: String? = null,
    val name: String,
    val path: String? = null,
    val firstMessage: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val messageCount: Int = 0,
) {
    val displayName: String get() = name.ifBlank { firstMessage.orEmpty().ifBlank { "新会话" } }
}

enum class MessageRole { USER, ASSISTANT, SYSTEM }
data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val text: String,
    val images: List<ChatImage> = emptyList(),
    val entryId: String? = null,
)

data class ChatImage(
    val data: String,
    val mimeType: String,
)
