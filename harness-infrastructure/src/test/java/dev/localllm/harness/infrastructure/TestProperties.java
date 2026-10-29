package dev.localllm.harness.infrastructure;

import java.nio.file.Path;

import dev.localllm.harness.infrastructure.config.HarnessProperties;

/**
 * Harness settings for tests.
 */
public final class TestProperties {

  /** Context window configured in tests. */
  public static final int NUM_CTX = 65536;

  private TestProperties() {
  }

  /**
   * Settings with the given home directory and Ollama address.
   *
   * @param home harness home directory
   * @param ollamaBaseUrl Ollama address
   * @param persistThinking whether reasoning is written to session files
   * @return settings
   */
  public static HarnessProperties create(Path home, String ollamaBaseUrl, boolean persistThinking) {
    return new HarnessProperties("qwen3.5:27b", ollamaBaseUrl, home,
        new HarnessProperties.Thinking(true, HarnessProperties.ThinkingDisplay.STREAM,
            persistThinking),
        new HarnessProperties.Context(NUM_CTX, 0.8, 4, 3.5));
  }
}
