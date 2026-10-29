package dev.localllm.harness.application.model;

/**
 * A single message of the conversation history sent to the model.
 *
 * <p>Model reasoning (thinking) is never part of a message: it is streamed to the user only and
 * kept out of the context window.
 *
 * @param role author of the message
 * @param content message text
 */
public record ChatMessage(Role role, String content) {
}
