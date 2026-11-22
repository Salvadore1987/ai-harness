package dev.localllm.harness.adapter.anthropic.mapper;

import com.anthropic.models.messages.Usage;
import org.mapstruct.Mapper;

import dev.localllm.harness.application.model.TokenUsage;

/**
 * Converts the token counters of a streamed answer.
 */
@Mapper
public interface UsageAnthropicMapper {

  /**
   * Combines the counters of {@code message_start} with the output tokens of the last
   * {@code message_delta}.
   *
   * @param startUsage counters sent when the answer starts; the prompt includes the tokens read
   *     from and written to the prompt cache
   * @param outputTokens tokens generated so far, including thinking
   * @return token counters
   */
  default TokenUsage toTokenUsage(Usage startUsage, long outputTokens) {
    long promptTokens = startUsage.inputTokens()
        + startUsage.cacheCreationInputTokens().orElse(0L)
        + startUsage.cacheReadInputTokens().orElse(0L);
    return new TokenUsage(Math.toIntExact(promptTokens), Math.toIntExact(outputTokens));
  }
}
