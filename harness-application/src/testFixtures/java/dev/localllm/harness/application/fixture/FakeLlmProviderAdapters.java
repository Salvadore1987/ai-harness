package dev.localllm.harness.application.fixture;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import dev.localllm.harness.application.model.ChatChunk;
import dev.localllm.harness.application.model.ChatCompletion;
import dev.localllm.harness.application.model.ChatRequest;
import dev.localllm.harness.application.model.LlmProvider;
import dev.localllm.harness.application.model.ModelInfo;
import dev.localllm.harness.application.port.out.LlmProviderAdapters;

/**
 * Provider adapters backed by a scripted chat model, a fixed model list and a fixed context
 * window.
 */
public final class FakeLlmProviderAdapters implements LlmProviderAdapters {

  private final LlmProvider provider;
  private final boolean available;
  private final String defaultModel;
  private final FakeChatModelPort chatModel = new FakeChatModelPort();
  private final List<ModelInfo> models;
  private final int contextLength;

  /**
   * Creates the fake.
   *
   * @param provider provider served
   * @param available whether the provider can be used
   * @param defaultModel model a new session starts with
   * @param contextLength context window of every model
   * @param models models of the provider
   */
  public FakeLlmProviderAdapters(LlmProvider provider, boolean available, String defaultModel,
      int contextLength, ModelInfo... models) {
    this.provider = provider;
    this.available = available;
    this.defaultModel = defaultModel;
    this.contextLength = contextLength;
    this.models = List.of(models);
  }

  /**
   * Scripted chat model that answers {@link #stream} and counts {@link #cancelActive} calls.
   *
   * @return chat model
   */
  public FakeChatModelPort chatModel() {
    return chatModel;
  }

  @Override
  public LlmProvider provider() {
    return provider;
  }

  @Override
  public boolean isAvailable() {
    return available;
  }

  @Override
  public String defaultModel() {
    return defaultModel;
  }

  @Override
  public ChatCompletion stream(ChatRequest request, Consumer<ChatChunk> chunkConsumer) {
    return chatModel.stream(request, chunkConsumer);
  }

  @Override
  public void cancelActive() {
    chatModel.cancelActive();
  }

  @Override
  public List<ModelInfo> listModels() {
    return models;
  }

  @Override
  public Optional<ModelInfo> findModel(String name) {
    return models.stream().filter(model -> model.name().equals(name)).findFirst();
  }

  @Override
  public int contextLength(String model) {
    return contextLength;
  }
}
