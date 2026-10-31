package dev.localllm.harness.infrastructure.out.routing;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.localllm.harness.application.model.LlmProvider;
import dev.localllm.harness.application.model.ProviderInfo;
import dev.localllm.harness.infrastructure.TestProperties;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderCatalogAdapterTest {

  @Test
  void listProviders_BeforeAnthropicAdapter_OffersOnlyOllamaWithConfiguredModel() {
    // Arrange
    ProviderCatalogAdapter adapter =
        new ProviderCatalogAdapter(TestProperties.create(Path.of("/tmp/harness"), false));

    // Act
    List<ProviderInfo> providers = adapter.listProviders();

    // Assert
    assertThat(providers).containsExactly(
        new ProviderInfo(LlmProvider.OLLAMA, true, "qwen3.5:27b"),
        new ProviderInfo(LlmProvider.ANTHROPIC, false, "claude-opus-5-5"));
  }
}
