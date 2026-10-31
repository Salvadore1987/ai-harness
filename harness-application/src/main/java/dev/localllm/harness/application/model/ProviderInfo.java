package dev.localllm.harness.application.model;

/**
 * State of an LLM provider in this run.
 *
 * @param provider the provider
 * @param available whether requests can be sent to it, for example whether its API key is set
 * @param defaultModel model a new session of this provider starts with
 */
public record ProviderInfo(LlmProvider provider, boolean available, String defaultModel) {
}
