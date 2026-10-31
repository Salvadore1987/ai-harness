package dev.localllm.harness.adapter.ollama;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import dev.localllm.harness.adapter.ollama.config.OllamaProperties;
import dev.localllm.harness.application.model.ChatChunk;
import dev.localllm.harness.application.model.ChatCompletion;
import dev.localllm.harness.application.model.ChatRequest;
import dev.localllm.harness.application.model.LlmProvider;
import dev.localllm.harness.application.model.ModelInfo;
import dev.localllm.harness.application.port.out.LlmProviderAdapters;

/**
 * The Ollama provider: chat, installed models and loaded context windows of the local server.
 *
 * <p>Ollama is always available; whether the server answers is found out on the first request.
 */
@Component
public class OllamaProviderAdapters implements LlmProviderAdapters {

  private final OllamaChatAdapter chatAdapter;
  private final OllamaModelCatalogAdapter catalogAdapter;
  private final OllamaContextBudgetAdapter contextBudgetAdapter;
  private final String defaultModel;

  /**
   * Creates the provider.
   *
   * @param chatAdapter streams answers
   * @param catalogAdapter installed models
   * @param contextBudgetAdapter context window of loaded models
   * @param properties Ollama settings with the start model
   */
  public OllamaProviderAdapters(
      OllamaChatAdapter chatAdapter,
      OllamaModelCatalogAdapter catalogAdapter,
      OllamaContextBudgetAdapter contextBudgetAdapter,
      OllamaProperties properties) {
    this.chatAdapter = chatAdapter;
    this.catalogAdapter = catalogAdapter;
    this.contextBudgetAdapter = contextBudgetAdapter;
    this.defaultModel = properties.model();
  }

  @Override
  public LlmProvider provider() {
    return LlmProvider.OLLAMA;
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public String defaultModel() {
    return defaultModel;
  }

  @Override
  public ChatCompletion stream(ChatRequest request, Consumer<ChatChunk> chunkConsumer) {
    return chatAdapter.stream(request, chunkConsumer);
  }

  @Override
  public void cancelActive() {
    chatAdapter.cancelActive();
  }

  @Override
  public List<ModelInfo> listModels() {
    return catalogAdapter.listModels();
  }

  @Override
  public Optional<ModelInfo> findModel(String name) {
    return catalogAdapter.findModel(name);
  }

  @Override
  public int contextLength(String model) {
    return contextBudgetAdapter.contextLength(model);
  }
}
