package com.example.pirt.ui

import com.example.pirt.runtime.PiProvider

internal fun providerPriority(id: String): Int = when (id) {
    "openai-codex" -> 0
    "anthropic" -> 1
    "openai" -> 2
    "google" -> 3
    "deepseek" -> 4
    "kimi-coding" -> 5
    "minimax-cn" -> 6
    "openrouter" -> 7
    "github-copilot" -> 8
    "qwen-token-plan-cn" -> 9
    "zai-coding-cn" -> 10
    "xiaomi-token-plan-cn" -> 11
    else -> 100
}

internal fun sortProviders(providers: List<PiProvider>): List<PiProvider> = providers.sortedWith(
    compareBy<PiProvider>(
        { if (it.configured) 0 else 1 },
        { providerPriority(it.id) },
        { it.name },
    )
)
