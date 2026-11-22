package dev.localllm.harness.adapter.anthropic.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the Claude API bound from {@code harness.anthropic.*}.
 *
 * <p>An empty API key leaves the provider unavailable instead of failing the start. The key never
 * appears in {@link #toString()}, so it cannot reach a log line or an error message.
 *
 * @param apiKey Anthropic API key, empty if not set
 * @param baseUrl address of the Claude API
 * @param model model a new Anthropic session starts with
 * @param maxTokens upper limit of the output tokens of one answer
 * @param effort how thoroughly the model thinks and answers
 */
@ConfigurationProperties("harness.anthropic")
@Validated
public record AnthropicProperties(
    @NotNull String apiKey,
    @NotBlank String baseUrl,
    @NotBlank String model,
    @Min(1) @Max(128_000) int maxTokens,
    @NotNull Effort effort) {

  @Override
  public String toString() {
    return "AnthropicProperties[apiKey=" + (apiKey.isBlank() ? "<empty>" : "<set>")
        + ", baseUrl=" + baseUrl + ", model=" + model + ", maxTokens=" + maxTokens
        + ", effort=" + effort + "]";
  }

  /**
   * Effort level of the Messages API ({@code output_config.effort}).
   */
  public enum Effort {
    /** Fewest tokens; for simple tasks. */
    LOW,
    /** Default of Claude Opus 5.5 when the request does not set an effort. */
    MEDIUM,
    /** Default of the harness: thorough enough for the agent loop. */
    HIGH,
    /** Between high and max; for long agentic and coding work. */
    XHIGH,
    /** Highest effort when correctness matters more than cost. */
    MAX
  }
}
