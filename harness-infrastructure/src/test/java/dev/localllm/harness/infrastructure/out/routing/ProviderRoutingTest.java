package dev.localllm.harness.infrastructure.out.routing;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import dev.localllm.harness.application.exception.ProviderUnavailableException;
import dev.localllm.harness.application.fixture.FakeLlmProviderAdapters;
import dev.localllm.harness.application.fixture.FakeModelCatalogPort;
import dev.localllm.harness.application.model.ChatChunk;
import dev.localllm.harness.application.model.ChatCompletion;
import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.ChatRequest;
import dev.localllm.harness.application.model.LlmProvider;
import dev.localllm.harness.application.model.ModelInfo;
import dev.localllm.harness.application.model.ProviderInfo;
import dev.localllm.harness.application.model.Role;
import dev.localllm.harness.infrastructure.out.routing.mapper.ProviderInfoMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderRoutingTest {

  private static final String CLAUDE = "claude-opus-5-5";

  private final FakeLlmProviderAdapters ollama = new FakeLlmProviderAdapters(
      LlmProvider.OLLAMA, true, "qwen3.5:27b", 65536, FakeModelCatalogPort.QWEN);
  private final FakeLlmProviderAdapters anthropic = new FakeLlmProviderAdapters(
      LlmProvider.ANTHROPIC, true, CLAUDE, 1_000_000,
      new ModelInfo(CLAUDE, "", "", Set.of()));
  private final FakeLlmProviderAdapters anthropicWithoutKey = new FakeLlmProviderAdapters(
      LlmProvider.ANTHROPIC, false, CLAUDE, 1_000_000);

  @Test
  void stream_ForProvider_GoesToItsAdapters() {
    // Arrange
    anthropic.chatModel().respondWith(new ChatChunk.Content("from claude"));
    RoutingChatModelAdapter router = new RoutingChatModelAdapter(registry(ollama, anthropic));

    // Act
    ChatCompletion completion = router.stream(request(LlmProvider.ANTHROPIC, CLAUDE), chunk -> { });

    // Assert
    assertThat(completion.content()).isEqualTo("from claude");
    assertThat(anthropic.chatModel().requests()).hasSize(1);
    assertThat(ollama.chatModel().requests()).isEmpty();
  }

  @Test
  void cancelActive_DuringStream_GoesToStreamingProviderOnly() {
    // Arrange
    ollama.chatModel().respondWith(new ChatChunk.Content("partial"));
    RoutingChatModelAdapter router = new RoutingChatModelAdapter(registry(ollama, anthropic));

    // Act
    router.stream(request(LlmProvider.OLLAMA, "qwen3.5:27b"), chunk -> router.cancelActive());
    router.cancelActive();

    // Assert
    assertThat(ollama.chatModel().cancelCount()).isEqualTo(1);
    assertThat(anthropic.chatModel().cancelCount()).isZero();
  }

  @Test
  void stream_ForUnavailableProvider_ThrowsProviderUnavailable() {
    // Arrange
    RoutingChatModelAdapter router =
        new RoutingChatModelAdapter(registry(ollama, anthropicWithoutKey));
    ChatRequest request = request(LlmProvider.ANTHROPIC, CLAUDE);

    // Act & Assert
    assertThatThrownBy(() -> router.stream(request, chunk -> { }))
        .isInstanceOf(ProviderUnavailableException.class);
    assertThat(anthropicWithoutKey.chatModel().requests()).isEmpty();
  }

  @Test
  void listModels_ForProviderWithoutModule_ThrowsProviderUnavailable() {
    // Arrange
    RoutingModelCatalogAdapter catalog = new RoutingModelCatalogAdapter(registry(ollama));

    // Act & Assert
    assertThatThrownBy(() -> catalog.listModels(LlmProvider.ANTHROPIC))
        .isInstanceOf(ProviderUnavailableException.class);
  }

  @Test
  void findModel_ForProvider_LooksOnlyAtItsModels() {
    // Arrange
    RoutingModelCatalogAdapter catalog =
        new RoutingModelCatalogAdapter(registry(ollama, anthropic));

    // Act & Assert
    assertThat(catalog.findModel(LlmProvider.OLLAMA, "qwen3.5:27b")).isPresent();
    assertThat(catalog.findModel(LlmProvider.ANTHROPIC, "qwen3.5:27b")).isEmpty();
    assertThat(catalog.listModels(LlmProvider.ANTHROPIC)).extracting(ModelInfo::name)
        .containsExactly(CLAUDE);
  }

  @Test
  void contextLength_ForProvider_ComesFromItsAdapters() {
    // Arrange
    RoutingContextBudgetAdapter budget =
        new RoutingContextBudgetAdapter(registry(ollama, anthropic));

    // Act
    int contextLength = budget.contextLength(LlmProvider.ANTHROPIC, CLAUDE);

    // Assert
    assertThat(contextLength).isEqualTo(1_000_000);
  }

  @Test
  void listProviders_InAnyRegistrationOrder_DescribesEveryModuleInDeclarationOrder() {
    // Arrange
    ProviderCatalogAdapter providers = new ProviderCatalogAdapter(
        registry(anthropicWithoutKey, ollama), Mappers.getMapper(ProviderInfoMapper.class));

    // Act
    List<ProviderInfo> result = providers.listProviders();

    // Assert
    assertThat(result).containsExactly(
        new ProviderInfo(LlmProvider.OLLAMA, true, "qwen3.5:27b"),
        new ProviderInfo(LlmProvider.ANTHROPIC, false, CLAUDE));
  }

  @Test
  void registry_WithTwoModulesForOneProvider_FailsAtStartup() {
    // Arrange
    FakeLlmProviderAdapters secondOllama =
        new FakeLlmProviderAdapters(LlmProvider.OLLAMA, true, "llama3:8b", 8192);

    // Act & Assert
    assertThatThrownBy(() -> registry(ollama, secondOllama))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("OLLAMA");
  }

  private static LlmProviderRegistry registry(FakeLlmProviderAdapters... adapters) {
    return new LlmProviderRegistry(List.of(adapters));
  }

  private static ChatRequest request(LlmProvider provider, String model) {
    return new ChatRequest(provider, model, List.of(new ChatMessage(Role.USER, "hi")), false);
  }
}
