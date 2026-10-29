package dev.localllm.harness.application.service;

import dev.localllm.harness.application.dto.query.ContextUsageCriteria;
import dev.localllm.harness.application.dto.result.ContextUsageResult;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.port.in.GetContextUsageQuery;
import dev.localllm.harness.application.port.out.ConversationPort;

/**
 * Measures the history of a session against the model context window.
 */
public class GetContextUsageQueryService implements GetContextUsageQuery {

  private final ConversationPort conversationPort;
  private final ContextMeter contextMeter;
  private final TurnApplicationMapper mapper;

  /**
   * Creates the service.
   *
   * @param conversationPort conversation history storage
   * @param contextMeter estimates the tokens
   * @param mapper converts the usage into the result
   */
  public GetContextUsageQueryService(
      ConversationPort conversationPort, ContextMeter contextMeter, TurnApplicationMapper mapper) {
    this.conversationPort = conversationPort;
    this.contextMeter = contextMeter;
    this.mapper = mapper;
  }

  @Override
  public ContextUsageResult execute(ContextUsageCriteria criteria) {
    return mapper.toContextUsageResult(contextMeter.measure(
        conversationPort.history(criteria.sessionId()), criteria.model()));
  }
}
