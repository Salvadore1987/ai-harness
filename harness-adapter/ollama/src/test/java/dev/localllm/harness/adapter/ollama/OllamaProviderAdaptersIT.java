package dev.localllm.harness.adapter.ollama;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.localllm.harness.adapter.ollama.fixture.OllamaTestSupport;
import dev.localllm.harness.application.model.LlmProvider;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class OllamaProviderAdaptersIT {

  @RegisterExtension
  static WireMockExtension ollama = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicPort())
      .build();

  @Test
  void provider_Always_IsAvailableOllamaWithConfiguredModel() {
    // Arrange
    OllamaProviderAdapters adapters = adapters(ollama.baseUrl());

    // Act & Assert
    assertThat(adapters.provider()).isEqualTo(LlmProvider.OLLAMA);
    assertThat(adapters.isAvailable()).isTrue();
    assertThat(adapters.defaultModel()).isEqualTo(OllamaTestSupport.MODEL);
  }

  @Test
  void contextLength_WhenModelIsLoaded_DelegatesToLoadedWindow() {
    // Arrange
    ollama.stubFor(get("/api/ps").willReturn(okJson("""
        {"models":[{"name":"qwen3.5:27b","context_length":32768}]}""")));
    OllamaProviderAdapters adapters = adapters(ollama.baseUrl());

    // Act
    int contextLength = adapters.contextLength("qwen3.5:27b");

    // Assert
    assertThat(contextLength).isEqualTo(32768);
  }

  private static OllamaProviderAdapters adapters(String baseUrl) {
    return OllamaTestSupport.providerAdapters(OllamaTestSupport.chatAdapter(baseUrl), baseUrl);
  }
}
