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
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Provider-neutral harness settings bound from {@code harness.*}; the Ollama settings of the same
 * prefix are bound by {@code OllamaProperties}.
 *
 * @param model default model tag used on startup
 * @param home directory for sessions, history, logs and user configuration
 * @param thinking thinking mode defaults
 * @param context context window and compaction rules
 * @param agent limits of the agent loop
 * @param tools settings of the built-in tools
 * @param mcp settings of the MCP servers
 */
@ConfigurationProperties("harness")
@Validated
public record HarnessProperties(
    @NotBlank String model,
    @NotNull Path home,
    @NotNull @Valid Thinking thinking,
    @NotNull @Valid Context context,
    @NotNull @Valid Agent agent,
    @NotNull @Valid Tools tools,
    @DefaultValue @NotNull @Valid Mcp mcp) {

  /**
   * Directory of the JSONL session files.
   *
   * @return sessions directory
   */
  public Path sessionsDirectory() {
    return home.resolve("sessions");
  }

  /**
   * File with the stdio MCP servers in the Claude Desktop format.
   *
   * @return path of {@code mcp.json}
   */
  public Path mcpServersFile() {
    return home.resolve("mcp.json");
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
   * @param compactThreshold share of the window that triggers automatic compaction
   * @param keepTailShare share of the window the most recent messages may take and still be
   *     kept verbatim by a compaction
   * @param charsPerToken average characters per token used for estimates
   */
  public record Context(
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
   * Settings of the MCP servers.
   *
   * @param headers HTTP headers by Streamable HTTP server name, sent with every request; values
   *     usually come from environment variables through placeholders
   */
  public record Mcp(@DefaultValue @NotNull Map<String, Map<String, String>> headers) {
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
