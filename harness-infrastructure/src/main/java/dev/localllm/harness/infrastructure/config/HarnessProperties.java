package dev.localllm.harness.infrastructure.config;

import java.nio.file.Path;

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
    @NotNull @Valid Tools tools) {

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
   */
  public record Tools(@Min(256) int maxOutputChars) {
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
