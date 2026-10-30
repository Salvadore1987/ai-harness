package dev.localllm.harness.infrastructure.out.ollama;

import java.nio.file.Path;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import dev.localllm.harness.infrastructure.TestProperties;
import dev.localllm.harness.infrastructure.config.HarnessProperties;

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
    int contextLength = adapter(ollama.baseUrl()).contextLength("qwen3.5:27b");

    // Assert
    assertThat(contextLength).isEqualTo(32768);
  }

  @Test
  void contextLength_WhenModelIsNotLoaded_ReturnsConfiguredWindow() {
    // Arrange
    ollama.stubFor(get("/api/ps").willReturn(okJson("{\"models\":[]}")));

    // Act
    int contextLength = adapter(ollama.baseUrl()).contextLength("qwen3.5:27b");

    // Assert
    assertThat(contextLength).isEqualTo(TestProperties.NUM_CTX);
  }

  @Test
  void contextLength_WhenServerDown_ReturnsConfiguredWindow() {
    // Act
    int contextLength = adapter("http://localhost:1").contextLength("qwen3.5:27b");

    // Assert
    assertThat(contextLength).isEqualTo(TestProperties.NUM_CTX);
  }

  private static OllamaContextBudgetAdapter adapter(String baseUrl) {
    HarnessProperties properties =
        TestProperties.create(Path.of("build/harness-home"), baseUrl, false);
    return new OllamaContextBudgetAdapter(
        new OllamaPsClient(RestClient.builder(), properties), properties);
  }
}
