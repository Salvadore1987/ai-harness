package dev.localllm.harness.infrastructure.out.ollama;

import java.time.Instant;
import java.util.List;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mapstruct.factory.Mappers;
import org.springframework.web.client.RestClient;

import dev.localllm.harness.application.exception.LlmResponseException;
import dev.localllm.harness.application.exception.LlmUnavailableException;
import dev.localllm.harness.application.model.RunningModelInfo;
import dev.localllm.harness.infrastructure.out.ollama.mapper.RunningModelOllamaMapper;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaRunningModelsAdapterIT {

  @RegisterExtension
  static WireMockExtension ollama = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicPort())
      .build();

  @Test
  void listRunning_WhenModelIsLoaded_ReturnsItsFootprintContextAndUnloadTime() {
    // Arrange: answer recorded from Ollama with qwen3.5:27b on the MLX runner
    ollama.stubFor(get("/api/ps").willReturn(okJson("""
        {"models":[{"name":"qwen3.5:27b","model":"qwen3.5:27b","size":19744124896,
        "digest":"2d2e4b8fc7c0","details":{"parent_model":"","format":"safetensors",
        "family":"","families":null,"parameter_size":"","quantization_level":"nvfp4"},
        "expires_at":"2026-10-29T21:32:51.639539+05:00","size_vram":19744124896,
        "context_length":32768,"runner":"mlx"}]}""")));

    // Act
    List<RunningModelInfo> models = adapter(ollama.baseUrl()).listRunning();

    // Assert
    assertThat(models).containsExactly(new RunningModelInfo("qwen3.5:27b", 19_744_124_896L,
        19_744_124_896L, 32768, Instant.parse("2026-10-29T16:32:51.639539Z")));
  }

  @Test
  void listRunning_WhenFieldsAreMissing_ReturnsZeroSizesAndSkipsNamelessEntries() {
    // Arrange
    ollama.stubFor(get("/api/ps").willReturn(okJson("""
        {"models":[{"name":"llama3:8b"},{"size":1}]}""")));

    // Act
    List<RunningModelInfo> models = adapter(ollama.baseUrl()).listRunning();

    // Assert
    assertThat(models).containsExactly(new RunningModelInfo("llama3:8b", 0L, 0L, null, null));
  }

  @Test
  void listRunning_WhenNothingIsLoaded_ReturnsEmptyList() {
    // Arrange
    ollama.stubFor(get("/api/ps").willReturn(okJson("{\"models\":[]}")));

    // Act
    List<RunningModelInfo> models = adapter(ollama.baseUrl()).listRunning();

    // Assert
    assertThat(models).isEmpty();
  }

  @Test
  void listRunning_WhenServerDown_ThrowsLlmUnavailable() {
    // Act & Assert
    assertThatThrownBy(() -> adapter("http://localhost:1").listRunning())
        .isInstanceOf(LlmUnavailableException.class);
  }

  @Test
  void listRunning_WhenServerFails_ThrowsLlmResponse() {
    // Arrange
    ollama.stubFor(get("/api/ps").willReturn(serverError()));

    // Act & Assert
    assertThatThrownBy(() -> adapter(ollama.baseUrl()).listRunning())
        .isInstanceOf(LlmResponseException.class);
  }

  private static OllamaRunningModelsAdapter adapter(String baseUrl) {
    return new OllamaRunningModelsAdapter(
        new OllamaPsClient(RestClient.builder(), OllamaTestProperties.create(baseUrl)),
        Mappers.getMapper(RunningModelOllamaMapper.class),
        OllamaTestSupport.translator(baseUrl));
  }
}
