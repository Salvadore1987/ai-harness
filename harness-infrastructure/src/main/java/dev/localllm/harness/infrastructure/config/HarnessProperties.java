package dev.localllm.harness.infrastructure.config;

import java.nio.file.Path;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Harness settings bound from {@code harness.*}.
 *
 * @param model default model tag used on startup
 * @param ollamaBaseUrl address of the Ollama server, also used in error messages
 * @param home directory for history, logs and user configuration
 * @param thinking thinking mode defaults
 */
@ConfigurationProperties("harness")
@Validated
public record HarnessProperties(
    @NotBlank String model,
    @NotBlank String ollamaBaseUrl,
    @NotNull Path home,
    @NotNull @Valid Thinking thinking) {

  /**
   * Thinking mode defaults.
   *
   * @param enabled whether thinking is requested from models that support it
   * @param display how reasoning is shown in the terminal
   */
  public record Thinking(boolean enabled, @NotNull ThinkingDisplay display) {
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
