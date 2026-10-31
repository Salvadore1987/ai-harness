package dev.localllm.harness.infrastructure.out.ollama;

import java.util.Map;

import dev.localllm.harness.infrastructure.out.ollama.config.OllamaProperties;
import dev.localllm.harness.infrastructure.out.ollama.config.OllamaProperties.ToolCallModeType;

/**
 * Ollama settings for tests.
 */
public final class OllamaTestProperties {

  /** Context window configured in tests. */
  public static final int NUM_CTX = 65536;

  private OllamaTestProperties() {
  }

  /**
   * Settings with the given Ollama address and the default tool call modes.
   *
   * @param baseUrl Ollama address
   * @return settings
   */
  public static OllamaProperties create(String baseUrl) {
    return create(baseUrl, Map.of("codellama", ToolCallModeType.TEXT_JSON),
        ToolCallModeType.NATIVE);
  }

  /**
   * Settings with the given Ollama address and tool call modes.
   *
   * @param baseUrl Ollama address
   * @param modes tool call mode by model name prefix
   * @param defaultMode tool call mode of models no prefix matches
   * @return settings
   */
  public static OllamaProperties create(String baseUrl, Map<String, ToolCallModeType> modes,
      ToolCallModeType defaultMode) {
    return new OllamaProperties(baseUrl, new OllamaProperties.Context(NUM_CTX), modes,
        defaultMode);
  }
}
