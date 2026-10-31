package dev.localllm.harness.adapter.ollama.config;

import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Ollama settings bound from {@code harness.*}, next to the provider-neutral
 * {@code HarnessProperties} of the same prefix.
 *
 * @param ollamaBaseUrl address of the Ollama server, also used in error messages
 * @param context context window requested from Ollama
 * @param toolCallModes tool call mode by model name prefix; the longest matching prefix wins
 * @param defaultToolCallMode tool call mode of models no prefix matches
 */
@ConfigurationProperties("harness")
@Validated
public record OllamaProperties(
    @NotBlank String ollamaBaseUrl,
    @NotNull @Valid Context context,
    @NotNull Map<String, ToolCallModeType> toolCallModes,
    @NotNull ToolCallModeType defaultToolCallMode) {

  /**
   * Context window requested from Ollama.
   *
   * @param numCtx context window requested from Ollama; used when the loaded size is unknown
   */
  public record Context(@Min(1024) int numCtx) {
  }

  /**
   * How tools are offered to a model and how its calls are read.
   */
  public enum ToolCallModeType {
    /** Native tool calling: {@code tools} in the request, {@code tool_calls} in the answer. */
    NATIVE,
    /** Tools described in the system prompt, calls written as a {@code ```tool} block. */
    TEXT_JSON
  }
}
