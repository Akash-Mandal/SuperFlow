package com.superflow.ai

data class ProviderTemplate(
    val name: String,
    val description: String,
    val baseUrl: String,
    val model: String,
    val customHeaders: String = "",
    val note: String = ""
)

object ProviderTemplates {
    val all: List<ProviderTemplate> = listOf(
        ProviderTemplate(
            "OpenAI",
            "api.openai.com · gpt-4o",
            "https://api.openai.com",
            "gpt-4o"
        ),
        ProviderTemplate(
            "Groq",
            "api.groq.com/openai · llama-3.3-70b",
            "https://api.groq.com/openai",
            "llama-3.3-70b-versatile"
        ),
        ProviderTemplate(
            "OpenRouter",
            "openrouter.ai/api/v1 · gpt-4o",
            "https://openrouter.ai/api/v1",
            "openai/gpt-4o",
            "HTTP-Referer: https://superflow.app\nX-Title: SuperFlow"
        ),
        ProviderTemplate(
            "Together AI",
            "api.together.xyz · Llama-3.1-70B",
            "https://api.together.xyz",
            "meta-llama/Meta-Llama-3.1-70B-Instruct-Turbo"
        ),
        ProviderTemplate(
            "DeepSeek",
            "api.deepseek.com · deepseek-chat",
            "https://api.deepseek.com",
            "deepseek-chat"
        ),
        ProviderTemplate(
            "Mistral",
            "api.mistral.ai · mistral-large",
            "https://api.mistral.ai",
            "mistral-large-latest"
        ),
        ProviderTemplate(
            "Ollama (device)",
            "10.0.2.2:11434 · llama3.1",
            "http://10.0.2.2:11434",
            "llama3.1",
            note = "localhost never resolves from a device; use 10.0.2.2 for the emulator or your LAN IP."
        ),
        ProviderTemplate(
            "LM Studio",
            "Local server · pick loaded model",
            "http://10.0.2.2:1234",
            "local-model"
        )
    )
}
