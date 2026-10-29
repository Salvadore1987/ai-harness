package dev.localllm.harness.bootstrap.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.model.ContextPolicy;
import dev.localllm.harness.application.port.in.CancelTurnUseCase;
import dev.localllm.harness.application.port.in.CompactSessionUseCase;
import dev.localllm.harness.application.port.in.GetContextUsageQuery;
import dev.localllm.harness.application.port.in.ListModelsQuery;
import dev.localllm.harness.application.port.in.ListSessionsQuery;
import dev.localllm.harness.application.port.in.ResumeSessionUseCase;
import dev.localllm.harness.application.port.in.SendMessageUseCase;
import dev.localllm.harness.application.port.in.StartSessionUseCase;
import dev.localllm.harness.application.port.in.SwitchModelUseCase;
import dev.localllm.harness.application.port.in.ToggleThinkingUseCase;
import dev.localllm.harness.application.port.out.ChatModelPort;
import dev.localllm.harness.application.port.out.ContextBudgetPort;
import dev.localllm.harness.application.port.out.ConversationPort;
import dev.localllm.harness.application.port.out.ModelCatalogPort;
import dev.localllm.harness.application.port.out.SessionCatalogPort;
import dev.localllm.harness.application.port.out.TurnOutputPort;
import dev.localllm.harness.application.service.CancelTurnService;
import dev.localllm.harness.application.service.CompactSessionService;
import dev.localllm.harness.application.service.ContextMeter;
import dev.localllm.harness.application.service.ConversationCompactor;
import dev.localllm.harness.application.service.GetContextUsageQueryService;
import dev.localllm.harness.application.service.ListModelsQueryService;
import dev.localllm.harness.application.service.ListSessionsQueryService;
import dev.localllm.harness.application.service.ResumeSessionService;
import dev.localllm.harness.application.service.SendMessageService;
import dev.localllm.harness.application.service.StartSessionService;
import dev.localllm.harness.application.service.SwitchModelService;
import dev.localllm.harness.application.service.ToggleThinkingService;
import dev.localllm.harness.infrastructure.config.HarnessProperties;

/**
 * Wires the framework-free application services to the infrastructure adapters.
 */
@Configuration(proxyBeanMethods = false)
public class BeanConfig {

  /**
   * Context window rules from {@code harness.context.*}.
   *
   * @param properties harness settings
   * @return context policy
   */
  @Bean
  public ContextPolicy contextPolicy(HarnessProperties properties) {
    HarnessProperties.Context context = properties.context();
    return new ContextPolicy(
        context.compactThreshold(), context.keepLastMessages(), context.charsPerToken());
  }

  /**
   * Token estimates against the loaded context window.
   *
   * @param contextBudgetPort actual context window
   * @param policy context rules
   * @return the meter
   */
  @Bean
  public ContextMeter contextMeter(ContextBudgetPort contextBudgetPort, ContextPolicy policy) {
    return new ContextMeter(contextBudgetPort, policy);
  }

  /**
   * History compaction shared by the automatic and the manual path.
   *
   * @param conversationPort conversation history storage
   * @param chatModelPort language model writing the summary
   * @param contextMeter token estimates
   * @param policy context rules
   * @return the compactor
   */
  @Bean
  public ConversationCompactor conversationCompactor(
      ConversationPort conversationPort,
      ChatModelPort chatModelPort,
      ContextMeter contextMeter,
      ContextPolicy policy) {
    return new ConversationCompactor(conversationPort, chatModelPort, contextMeter, policy);
  }

  /**
   * Chat turn use case.
   *
   * @param conversationPort conversation history storage
   * @param sessionCatalogPort session metadata storage
   * @param chatModelPort language model
   * @param turnOutputPort terminal presentation
   * @param contextMeter token estimates
   * @param compactor automatic compaction
   * @param mapper result mapper
   * @param clock source of session timestamps
   * @return the use case
   */
  @Bean
  public SendMessageUseCase sendMessageUseCase(
      ConversationPort conversationPort,
      SessionCatalogPort sessionCatalogPort,
      ChatModelPort chatModelPort,
      TurnOutputPort turnOutputPort,
      ContextMeter contextMeter,
      ConversationCompactor compactor,
      TurnApplicationMapper mapper,
      Clock clock) {
    return new SendMessageService(conversationPort, sessionCatalogPort, chatModelPort,
        turnOutputPort, contextMeter, compactor, mapper, clock);
  }

  /**
   * Turn cancellation use case.
   *
   * @param chatModelPort language model
   * @return the use case
   */
  @Bean
  public CancelTurnUseCase cancelTurnUseCase(ChatModelPort chatModelPort) {
    return new CancelTurnService(chatModelPort);
  }

  /**
   * Model switch use case.
   *
   * @param modelCatalogPort installed models
   * @param mapper result mapper
   * @return the use case
   */
  @Bean
  public SwitchModelUseCase switchModelUseCase(
      ModelCatalogPort modelCatalogPort, TurnApplicationMapper mapper) {
    return new SwitchModelService(modelCatalogPort, mapper);
  }

  /**
   * Thinking toggle use case.
   *
   * @param modelCatalogPort installed models
   * @param mapper result mapper
   * @return the use case
   */
  @Bean
  public ToggleThinkingUseCase toggleThinkingUseCase(
      ModelCatalogPort modelCatalogPort, TurnApplicationMapper mapper) {
    return new ToggleThinkingService(modelCatalogPort, mapper);
  }

  /**
   * Session start use case.
   *
   * @param mapper result mapper
   * @return the use case
   */
  @Bean
  public StartSessionUseCase startSessionUseCase(TurnApplicationMapper mapper) {
    return new StartSessionService(mapper);
  }

  /**
   * Session resume use case.
   *
   * @param sessionCatalogPort session metadata storage
   * @param mapper result mapper
   * @return the use case
   */
  @Bean
  public ResumeSessionUseCase resumeSessionUseCase(
      SessionCatalogPort sessionCatalogPort, TurnApplicationMapper mapper) {
    return new ResumeSessionService(sessionCatalogPort, mapper);
  }

  /**
   * Stored sessions query.
   *
   * @param sessionCatalogPort session metadata storage
   * @param mapper result mapper
   * @return the query
   */
  @Bean
  public ListSessionsQuery listSessionsQuery(
      SessionCatalogPort sessionCatalogPort, TurnApplicationMapper mapper) {
    return new ListSessionsQueryService(sessionCatalogPort, mapper);
  }

  /**
   * Manual compaction use case.
   *
   * @param compactor history compaction
   * @param mapper result mapper
   * @return the use case
   */
  @Bean
  public CompactSessionUseCase compactSessionUseCase(
      ConversationCompactor compactor, TurnApplicationMapper mapper) {
    return new CompactSessionService(compactor, mapper);
  }

  /**
   * Context usage query.
   *
   * @param conversationPort conversation history storage
   * @param contextMeter token estimates
   * @param mapper result mapper
   * @return the query
   */
  @Bean
  public GetContextUsageQuery getContextUsageQuery(
      ConversationPort conversationPort, ContextMeter contextMeter, TurnApplicationMapper mapper) {
    return new GetContextUsageQueryService(conversationPort, contextMeter, mapper);
  }

  /**
   * Installed models query.
   *
   * @param modelCatalogPort installed models
   * @param mapper result mapper
   * @return the query
   */
  @Bean
  public ListModelsQuery listModelsQuery(
      ModelCatalogPort modelCatalogPort, TurnApplicationMapper mapper) {
    return new ListModelsQueryService(modelCatalogPort, mapper);
  }
}
