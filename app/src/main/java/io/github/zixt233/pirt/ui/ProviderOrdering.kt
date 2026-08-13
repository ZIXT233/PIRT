package io.github.zixt233.pirt.ui

import io.github.zixt233.pirt.runtime.PiProvider

internal fun providerPriority(id: String): Int = when {
    id == "openai-codex" -> 0
    id == "anthropic" -> 1
    id == "openai" -> 2
    id == "google" -> 3
    id == "deepseek" -> 4
    id == "kimi-coding" -> 5
    id == "minimax-cn" -> 6
    id == "openrouter" -> 7
    id == "github-copilot" -> 8
    id == "qwen-token-plan-cn" -> 9
    id == "zai-coding-cn" -> 10
    id == "xiaomi-token-plan-cn" -> 11
    id.startsWith("custom") -> 12
    else -> 100
}

internal fun sortProviders(providers: List<PiProvider>): List<PiProvider> = providers.sortedWith(
    compareBy<PiProvider>(
        { if (it.configured) 0 else 1 },
        { providerPriority(it.id) },
        { it.name },
    )
)
