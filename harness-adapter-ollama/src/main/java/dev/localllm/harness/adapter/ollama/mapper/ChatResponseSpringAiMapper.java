package dev.localllm.harness.adapter.ollama.mapper;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.mapstruct.Mapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import dev.localllm.harness.application.model.ChatChunk;
import dev.localllm.harness.application.model.TokenUsage;

/**
 * Converts streamed Spring AI responses into core chunks and token counters.
 */
@Mapper
public interface ChatResponseSpringAiMapper {

  /** Metadata key under which Spring AI puts the Ollama {@code thinking} field. */
  String THINKING_METADATA_KEY = "thinking";

  /**
   * Splits one streamed response into its reasoning and answer fragments.
   *
   * @param response streamed response chunk
   * @return fragments in arrival order, empty if the chunk carries no text
   */
  default List<ChatChunk> toChunks(ChatResponse response) {
    Generation generation = response.getResult();
    if (generation == null) {
      return List.of();
    }
    AssistantMessage output = generation.getOutput();
    List<ChatChunk> chunks = new ArrayList<>(2);
    if (output.getMetadata().get(THINKING_METADATA_KEY) instanceof String thinking
        && !thinking.isEmpty()) {
      chunks.add(new ChatChunk.Thinking(thinking));
    }
    String text = output.getText();
    if (text != null && !text.isEmpty()) {
      chunks.add(new ChatChunk.Content(text));
    }
    return chunks;
  }

  /**
   * Reads the token counters of a response; only the final chunk carries non-zero values.
   *
   * @param usage usage metadata of the response
   * @return token counters
   */
  TokenUsage toTokenUsage(@Nullable Usage usage);
}
