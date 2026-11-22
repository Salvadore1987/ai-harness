package dev.localllm.harness.adapter.anthropic.config;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

class AnthropicPropertiesTest {

  private static final String KEY = "sk-ant-test-secret";

  @Test
  void bind_WithKebabCaseKeys_ReadsEverySetting() {
    // Arrange
    Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
        "harness.anthropic.api-key", KEY,
        "harness.anthropic.base-url", "https://api.anthropic.com",
        "harness.anthropic.model", "claude-opus-5-5",
        "harness.anthropic.max-tokens", "64000",
        "harness.anthropic.effort", "xhigh")));

    // Act
    AnthropicProperties properties =
        binder.bindOrCreate("harness.anthropic", AnthropicProperties.class);

    // Assert
    assertThat(properties).isEqualTo(new AnthropicProperties(KEY, "https://api.anthropic.com",
        "claude-opus-5-5", 64_000, AnthropicProperties.Effort.XHIGH));
  }

  @Test
  void toString_WithApiKey_HidesTheKey() {
    // Arrange
    AnthropicProperties properties = new AnthropicProperties(KEY, "https://api.anthropic.com",
        "claude-opus-5-5", 64_000, AnthropicProperties.Effort.HIGH);

    // Act
    String text = properties.toString();

    // Assert
    assertThat(text).doesNotContain(KEY).contains("apiKey=<set>", "model=claude-opus-5-5");
  }

  @Test
  void toString_WithoutApiKey_SaysItIsEmpty() {
    // Arrange
    AnthropicProperties properties = new AnthropicProperties("", "https://api.anthropic.com",
        "claude-opus-5-5", 64_000, AnthropicProperties.Effort.HIGH);

    // Act & Assert
    assertThat(properties.toString()).contains("apiKey=<empty>");
  }
}
