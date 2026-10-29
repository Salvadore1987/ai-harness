package dev.localllm.harness.application.dto.result;

/**
 * State of the thinking mode after a toggle.
 *
 * @param enabled whether thinking is now enabled
 */
public record ThinkingToggleResult(boolean enabled) {
}
