package dev.localllm.harness.infrastructure.out.routing;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import dev.localllm.harness.application.model.ChatChunk;
import dev.localllm.harness.application.model.ChatCompletion;
import dev.localllm.harness.application.model.ChatRequest;
import dev.localllm.harness.application.port.out.ChatModelPort;
import dev.localllm.harness.application.port.out.LlmProviderAdapters;

/**
 * Streams from the provider named in the request; cancellation goes to the provider of the
 * stream that is running.
 */
@Component
public class RoutingChatModelAdapter implements ChatModelPort {

  private final LlmProviderRegistry registry;
  private final AtomicReference<LlmProviderAdapters> streaming = new AtomicReference<>();

  /**
   * Creates the adapter.
   *
   * @param registry provider adapters
   */
  public RoutingChatModelAdapter(LlmProviderRegistry registry) {
    this.registry = registry;
  }

  @Override
  public ChatCompletion stream(ChatRequest request, Consumer<ChatChunk> chunkConsumer) {
    LlmProviderAdapters provider = registry.available(request.provider());
    streaming.set(provider);
    try {
      return provider.stream(request, chunkConsumer);
    } finally {
      streaming.compareAndSet(provider, null);
    }
  }

  @Override
  public void cancelActive() {
    LlmProviderAdapters provider = streaming.get();
    if (provider != null) {
      provider.cancelActive();
    }
  }
}
