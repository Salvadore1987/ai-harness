package dev.localllm.harness.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import dev.localllm.harness.application.model.ChatChunk;
import dev.localllm.harness.application.model.ChatCompletion;
import dev.localllm.harness.application.model.ChatRequest;
import dev.localllm.harness.application.model.LlmProvider;
import dev.localllm.harness.application.model.ModelInfo;

/**
 * Everything the harness needs from one LLM provider: chat, models, context window and whether
 * the provider can be used.
 *
 * <p>Each provider module implements this interface once. The adapters of {@link ChatModelPort},
 * {@link ModelCatalogPort}, {@link ContextBudgetPort} and {@link ProviderCatalogPort} route every
 * call to the implementation of the requested provider, so the methods here take no provider.
 */
public interface LlmProviderAdapters {

  /**
   * The provider these adapters serve.
   *
   * @return provider
   */
  LlmProvider provider();

  /**
   * Whether requests can be sent to the provider in this run, for example whether its API key
   * is set.
   *
   * @return availability
   */
  boolean isAvailable();

  /**
   * Model a new session of this provider starts with.
   *
   * @return model name
   */
  String defaultModel();

  /**
   * Generates the next assistant message, as {@link ChatModelPort#stream}.
   *
   * @param request model, history and thinking flag
   * @param chunkConsumer receives every thinking and content fragment as it arrives
   * @return the outcome of the turn
   */
  ChatCompletion stream(ChatRequest request, Consumer<ChatChunk> chunkConsumer);

  /**
   * Cancels the stream of this provider that is currently running, if any.
   */
  void cancelActive();

  /**
   * Returns all models of the provider.
   *
   * @return models with their capabilities
   */
  List<ModelInfo> listModels();

  /**
   * Looks up one model of the provider.
   *
   * @param name model name
   * @return the model, or empty if the provider does not offer it
   */
  Optional<ModelInfo> findModel(String name);

  /**
   * Returns the context window of a model.
   *
   * @param model model name
   * @return context window in tokens
   */
  int contextLength(String model);
}
