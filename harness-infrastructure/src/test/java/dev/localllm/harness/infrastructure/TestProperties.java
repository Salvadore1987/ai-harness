package dev.localllm.harness.infrastructure;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import dev.localllm.harness.infrastructure.config.HarnessProperties;

/**
 * Harness settings for tests.
 */
public final class TestProperties {

  /** Tool output limit configured in tests. */
  public static final int MAX_OUTPUT_CHARS = 8000;

  private TestProperties() {
  }

  /**
   * Settings with the given home directory.
   *
   * @param home harness home directory
   * @param persistThinking whether reasoning is written to session files
   * @return settings
   */
  public static HarnessProperties create(Path home, boolean persistThinking) {
    return new HarnessProperties("qwen3.5:27b", home,
        new HarnessProperties.Thinking(true, HarnessProperties.ThinkingDisplay.STREAM,
            persistThinking),
        new HarnessProperties.Context(0.8, 0.25, 3.5),
        new HarnessProperties.Agent(25, 2),
        new HarnessProperties.Tools(MAX_OUTPUT_CHARS, "/bin/zsh", Duration.ofSeconds(120),
            Duration.ofSeconds(30)),
        new HarnessProperties.Mcp(Map.of()));
  }
}
