package dev.localllm.harness.infrastructure.out.ollama;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.localllm.harness.application.exception.LlmUnavailableException;
import dev.localllm.harness.application.model.ModelCapability;
import dev.localllm.harness.application.model.ModelInfo;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaModelCatalogAdapterIT {

  @RegisterExtension
  static WireMockExtension ollama = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicPort())
      .build();

  private static final String QWEN_SHOW = """
      {"details":{"parameter_size":"27B","quantization_level":"Q4_K_M","family":"qwen35"},
       "capabilities":["completion","vision","thinking","tools"]}
      """;

  @Test
  void findModel_WhenInstalled_ReturnsMetadataAndKnownCapabilities() {
    // Arrange
    stubShow("qwen3.5:27b", QWEN_SHOW);
    OllamaModelCatalogAdapter adapter = OllamaTestSupport.catalogAdapter(ollama.baseUrl());

    // Act
    Optional<ModelInfo> model = adapter.findModel("qwen3.5:27b");

    // Assert
    assertThat(model).contains(new ModelInfo("qwen3.5:27b", "27B", "Q4_K_M",
        Set.of(ModelCapability.THINKING, ModelCapability.TOOLS)));
  }

  @Test
  void findModel_WithoutDetails_UsesPlaceholders() {
    // Arrange
    stubShow("qwen3.5:27b", "{\"capabilities\":[\"completion\"]}");
    OllamaModelCatalogAdapter adapter = OllamaTestSupport.catalogAdapter(ollama.baseUrl());

    // Act
    Optional<ModelInfo> model = adapter.findModel("qwen3.5:27b");

    // Assert
    assertThat(model).hasValueSatisfying(info -> {
      assertThat(info.parameterSize()).isEqualTo("?");
      assertThat(info.capabilities()).isEmpty();
    });
  }

  @Test
  void findModel_WhenMissing_ReturnsEmpty() {
    // Arrange
    ollama.stubFor(post("/api/show").willReturn(aResponse()
        .withStatus(404)
        .withHeader("Content-Type", "application/json")
        .withBody("{\"error\":\"model 'missing' not found\"}")));
    OllamaModelCatalogAdapter adapter = OllamaTestSupport.catalogAdapter(ollama.baseUrl());

    // Act
    Optional<ModelInfo> model = adapter.findModel("missing");

    // Assert
    assertThat(model).isEmpty();
  }

  @Test
  void listModels_WithInstalledModels_ReturnsEachWithCapabilities() {
    // Arrange
    ollama.stubFor(get("/api/tags").willReturn(okJson("""
        {"models":[{"name":"qwen3.5:27b","model":"qwen3.5:27b"},
                   {"name":"codellama:7b","model":"codellama:7b"}]}
        """)));
    stubShow("qwen3.5:27b", QWEN_SHOW);
    stubShow("codellama:7b", "{\"capabilities\":[\"completion\"]}");
    OllamaModelCatalogAdapter adapter = OllamaTestSupport.catalogAdapter(ollama.baseUrl());

    // Act
    List<ModelInfo> models = adapter.listModels();

    // Assert
    assertThat(models).extracting(ModelInfo::name).containsExactly("qwen3.5:27b", "codellama:7b");
    // codellama has no native tools but is configured for the text-json tool call mode
    assertThat(models.getLast().capabilities()).containsExactly(ModelCapability.TOOLS);
  }

  @Test
  void listModels_WhenServerDown_ThrowsLlmUnavailable() {
    // Arrange
    OllamaModelCatalogAdapter adapter = OllamaTestSupport.catalogAdapter("http://localhost:1");

    // Act & Assert
    assertThatThrownBy(adapter::listModels).isInstanceOf(LlmUnavailableException.class);
  }

  private static void stubShow(String model, String body) {
    ollama.stubFor(post("/api/show")
        .withRequestBody(equalToJson("{\"model\":\"" + model + "\"}", true, true))
        .willReturn(okJson(body)));
  }
}
