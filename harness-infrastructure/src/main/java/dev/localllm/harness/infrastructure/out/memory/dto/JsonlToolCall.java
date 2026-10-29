package dev.localllm.harness.infrastructure.out.memory.dto;

/**
 * A tool call stored with an assistant message line.
 *
 * @param id call identifier, referenced by the result line
 * @param name tool name
 * @param argsJson arguments as a JSON object
 */
public record JsonlToolCall(String id, String name, String argsJson) {
}
