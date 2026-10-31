package dev.localllm.harness.adapter.ollama;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import dev.localllm.harness.adapter.ollama.config.OllamaProperties;
import dev.localllm.harness.adapter.ollama.fixture.OllamaTestProperties;
import dev.localllm.harness.application.model.LlmProvider;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class OllamaContextBudgetAdapterIT {

  @RegisterExtension
  static WireMockExtension ollama = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicPort())
      .build();

  @Test
  void contextLength_WhenModelIsLoaded_ReturnsItsLoadedWindow() {
    // Arrange
    ollama.stubFor(get("/api/ps").willReturn(okJson("""
        {"models":[{"name":"qwen3.5:27b","context_length":32768,"runner":"mlx"}]}""")));

    // Act
    int contextLength = adapter(ollama.baseUrl()).contextLength(LlmProvider.OLLAMA, "qwen3.5:27b");

    // Assert
    assertThat(contextLength).isEqualTo(32768);
  }

  @Test
  void contextLength_WhenModelIsNotLoaded_ReturnsConfiguredWindow() {
    // Arrange
    ollama.stubFor(get("/api/ps").willReturn(okJson("{\"models\":[]}")));

    // Act
    int contextLength = adapter(ollama.baseUrl()).contextLength(LlmProvider.OLLAMA, "qwen3.5:27b");

    // Assert
    assertThat(contextLength).isEqualTo(OllamaTestProperties.NUM_CTX);
  }

  @Test
  void contextLength_WhenServerDown_ReturnsConfiguredWindow() {
    // Act
    int contextLength = adapter("http://localhost:1").contextLength(LlmProvider.OLLAMA, "qwen3.5:27b");

    // Assert
    assertThat(contextLength).isEqualTo(OllamaTestProperties.NUM_CTX);
  }

  private static OllamaContextBudgetAdapter adapter(String baseUrl) {
    OllamaProperties properties = OllamaTestProperties.create(baseUrl);
    return new OllamaContextBudgetAdapter(
        new OllamaPsClient(RestClient.builder(), properties), properties);
  }
}
