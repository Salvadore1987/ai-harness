package dev.localllm.harness.infrastructure.out.ollama;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import dev.localllm.harness.application.model.ChatChunk;
import dev.localllm.harness.application.model.ChatCompletion;
import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.ChatRequest;
import dev.localllm.harness.application.model.ModelCapability;
import dev.localllm.harness.application.model.Role;
import dev.localllm.harness.application.model.TurnStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against the local Ollama with {@code qwen3.5:27b}.
 *
 * <p>Started with {@code ./gradlew test -PincludeTags=live}.
 */
@Tag("live")
class OllamaLiveIT {

  private static final String BASE_URL = "http://localhost:11434";

  @Test
  void stream_WithRealModel_DeliversFirstChunkLongBeforeTheEnd() {
    // Arrange
    OllamaChatAdapter adapter = OllamaTestSupport.chatAdapter(BASE_URL);
    List<Long> arrivals = new ArrayList<>();
    long startedAt = System.nanoTime();
    ChatRequest request = new ChatRequest(OllamaTestSupport.MODEL,
        List.of(new ChatMessage(Role.USER, "Count from 1 to 30, one number per line.")), false);

    // Act
    ChatCompletion completion =
        adapter.stream(request, chunk -> arrivals.add(System.nanoTime() - startedAt));

    // Assert
    long total = System.nanoTime() - startedAt;
    assertThat(completion.status()).isEqualTo(TurnStatus.COMPLETED);
    assertThat(completion.content()).contains("30");
    assertThat(arrivals).hasSizeGreaterThan(10);
    assertThat(arrivals.getFirst()).isLessThan(total / 2);
  }

  @Test
  void stream_WithThinkingEnabled_DeliversThinkingChunks() {
    // Arrange
    OllamaChatAdapter adapter = OllamaTestSupport.chatAdapter(BASE_URL);
    List<ChatChunk> chunks = new ArrayList<>();
    ChatRequest request = new ChatRequest(OllamaTestSupport.MODEL,
        List.of(new ChatMessage(Role.USER, "Is 17 prime? Answer yes or no.")), true);

    // Act
    adapter.stream(request, chunks::add);

    // Assert
    assertThat(chunks).anyMatch(ChatChunk.Thinking.class::isInstance);
    assertThat(chunks).anyMatch(ChatChunk.Content.class::isInstance);
  }

  @Test
  void findModel_WithRealServer_ReportsThinkingAndTools() {
    // Arrange
    OllamaModelCatalogAdapter adapter = OllamaTestSupport.catalogAdapter(BASE_URL);

    // Act & Assert
    assertThat(adapter.findModel(OllamaTestSupport.MODEL)).hasValueSatisfying(model ->
        assertThat(model.capabilities())
            .contains(ModelCapability.THINKING, ModelCapability.TOOLS));
  }
}
