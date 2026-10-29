package dev.localllm.harness.infrastructure.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Harness settings bound from {@code harness.*}.
 *
 * @param model default model tag used on startup
 * @param ollamaBaseUrl address of the Ollama server, also used in error messages
 * @param home directory for sessions, history, logs and user configuration
 * @param thinking thinking mode defaults
 * @param context context window and compaction rules
 * @param agent limits of the agent loop
 * @param tools settings of the built-in tools
 * @param toolCallModes tool call mode by model name prefix; the longest matching prefix wins
 * @param defaultToolCallMode tool call mode of models no prefix matches
 */
@ConfigurationProperties("harness")
@Validated
public record HarnessProperties(
    @NotBlank String model,
    @NotBlank String ollamaBaseUrl,
    @NotNull Path home,
    @NotNull @Valid Thinking thinking,
    @NotNull @Valid Context context,
    @NotNull @Valid Agent agent,
    @NotNull @Valid Tools tools,
    @NotNull Map<String, ToolCallModeType> toolCallModes,
    @NotNull ToolCallModeType defaultToolCallMode) {

  /**
   * Directory of the JSONL session files.
   *
   * @return sessions directory
   */
  public Path sessionsDirectory() {
    return home.resolve("sessions");
  }

  /**
   * Thinking mode defaults.
   *
   * @param enabled whether thinking is requested from models that support it
   * @param display how reasoning is shown in the terminal
   * @param persist whether reasoning is written to the session file for debugging
   */
  public record Thinking(boolean enabled, @NotNull ThinkingDisplay display, boolean persist) {
  }

  /**
   * Context window and compaction rules.
   *
   * @param numCtx context window requested from Ollama; used when the loaded size is unknown
   * @param compactThreshold share of the window that triggers automatic compaction
   * @param keepTailShare share of the window the most recent messages may take and still be
   *     kept verbatim by a compaction
   * @param charsPerToken average characters per token used for estimates
   */
  public record Context(
      @Min(1024) int numCtx,
      @DecimalMin("0.1") @DecimalMax("0.95") double compactThreshold,
      @DecimalMin("0.0") @DecimalMax("0.5") double keepTailShare,
      @DecimalMin("1.0") double charsPerToken) {
  }

  /**
   * Limits of the agent loop.
   *
   * @param maxIterations model requests allowed in one agent turn
   * @param maxParseFailures consecutive steps with only unknown tool calls before the loop stops
   */
  public record Agent(@Min(1) int maxIterations, @Min(1) int maxParseFailures) {
  }

  /**
   * Settings of the built-in tools.
   *
   * @param maxOutputChars tool output sent back to the model is cut to this many characters
   * @param shell shell that runs {@code run_command} with {@code -c}
   * @param commandTimeout default timeout of {@code run_command}
   * @param httpTimeout connect and read timeout of {@code http_fetch}
   */
  public record Tools(
      @Min(256) int maxOutputChars,
      @NotBlank String shell,
      @NotNull Duration commandTimeout,
      @NotNull Duration httpTimeout) {
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

  /**
   * Presentation of the model reasoning.
   */
  public enum ThinkingDisplay {
    /** Stream the reasoning text in a dim style. */
    STREAM,
    /** Show a single line with the reasoning duration and size. */
    SUMMARY
  }
}
