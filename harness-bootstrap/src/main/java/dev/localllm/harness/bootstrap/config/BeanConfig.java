package dev.localllm.harness.bootstrap.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.model.AgentPolicy;
import dev.localllm.harness.application.model.ContextPolicy;
import dev.localllm.harness.application.port.in.CancelTurnUseCase;
import dev.localllm.harness.application.port.in.CompactSessionUseCase;
import dev.localllm.harness.application.port.in.GetContextUsageQuery;
import dev.localllm.harness.application.port.in.ListModelsQuery;
import dev.localllm.harness.application.port.in.ListRunningModelsQuery;
import dev.localllm.harness.application.port.in.ListSessionsQuery;
import dev.localllm.harness.application.port.in.PurgeSessionsUseCase;
import dev.localllm.harness.application.port.in.ListToolsQuery;
import dev.localllm.harness.application.port.in.ResumeSessionUseCase;
import dev.localllm.harness.application.port.in.RunAgentTurnUseCase;
import dev.localllm.harness.application.port.in.SaveAnswerUseCase;
import dev.localllm.harness.application.port.in.SendMessageUseCase;
import dev.localllm.harness.application.port.in.StartSessionUseCase;
import dev.localllm.harness.application.port.in.SwitchModelUseCase;
import dev.localllm.harness.application.port.in.ToggleThinkingUseCase;
import dev.localllm.harness.application.port.out.AnswerFilePort;
import dev.localllm.harness.application.port.out.ChatModelPort;
import dev.localllm.harness.application.port.out.ContextBudgetPort;
import dev.localllm.harness.application.port.out.ConversationPort;
import dev.localllm.harness.application.port.out.ModelCatalogPort;
import dev.localllm.harness.application.port.out.RunningModelsPort;
import dev.localllm.harness.application.port.out.SessionCatalogPort;
import dev.localllm.harness.application.port.out.ToolExecutorPort;
import dev.localllm.harness.application.port.out.TurnOutputPort;
import dev.localllm.harness.application.service.AgentLoopService;
import dev.localllm.harness.application.service.CancelTurnService;
import dev.localllm.harness.application.service.CompactSessionService;
import dev.localllm.harness.application.service.ContextMeter;
import dev.localllm.harness.application.service.ConversationCompactor;
import dev.localllm.harness.application.service.GetContextUsageQueryService;
import dev.localllm.harness.application.service.ListModelsQueryService;
import dev.localllm.harness.application.service.ListRunningModelsQueryService;
import dev.localllm.harness.application.service.ListSessionsQueryService;
import dev.localllm.harness.application.service.PurgeSessionsService;
import dev.localllm.harness.application.service.ListToolsQueryService;
import dev.localllm.harness.application.service.ResumeSessionService;
import dev.localllm.harness.application.service.SaveAnswerService;
import dev.localllm.harness.application.service.SendMessageService;
import dev.localllm.harness.application.service.SessionRegistrar;
import dev.localllm.harness.application.service.StartSessionService;
import dev.localllm.harness.application.service.SwitchModelService;
import dev.localllm.harness.application.service.ToggleThinkingService;
import dev.localllm.harness.application.service.TurnCancellation;
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
        context.compactThreshold(), context.keepTailShare(), context.charsPerToken());
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
   * @param contextMeter token estimates and the budget of the kept tail
   * @return the compactor
   */
  @Bean
  public ConversationCompactor conversationCompactor(
      ConversationPort conversationPort,
      ChatModelPort chatModelPort,
      ContextMeter contextMeter) {
    return new ConversationCompactor(conversationPort, chatModelPort, contextMeter);
  }

  /**
   * Stores the session metadata on the first answered turn.
   *
   * @param sessionCatalogPort session metadata storage
   * @param clock source of session timestamps
   * @return the registrar
   */
  @Bean
  public SessionRegistrar sessionRegistrar(SessionCatalogPort sessionCatalogPort, Clock clock) {
    return new SessionRegistrar(sessionCatalogPort, clock);
  }

  /**
   * Cancellation request shared by the cancel use case and the agent loop.
   *
   * @return the cancellation state
   */
  @Bean
  public TurnCancellation turnCancellation() {
    return new TurnCancellation();
  }

  /**
   * Agent loop limits from {@code harness.agent.*}.
   *
   * @param properties harness settings
   * @return agent policy
   */
  @Bean
  public AgentPolicy agentPolicy(HarnessProperties properties) {
    HarnessProperties.Agent agent = properties.agent();
    return new AgentPolicy(agent.maxIterations(), agent.maxParseFailures());
  }

  /**
   * Chat turn use case.
   *
   * @param conversationPort conversation history storage
   * @param sessionRegistrar stores the session metadata on the first turn
   * @param chatModelPort language model
   * @param turnOutputPort terminal presentation
   * @param compactor automatic compaction
   * @param mapper result mapper
   * @return the use case
   */
  @Bean
  public SendMessageUseCase sendMessageUseCase(
      ConversationPort conversationPort,
      SessionRegistrar sessionRegistrar,
      ChatModelPort chatModelPort,
      TurnOutputPort turnOutputPort,
      ConversationCompactor compactor,
      TurnApplicationMapper mapper) {
    return new SendMessageService(conversationPort, sessionRegistrar, chatModelPort,
        turnOutputPort, compactor, mapper);
  }

  /**
   * Agent turn use case.
   *
   * @param conversationPort conversation history storage
   * @param sessionRegistrar stores the session metadata on the first turn
   * @param chatModelPort language model
   * @param toolExecutorPort tool registry
   * @param turnOutputPort terminal presentation
   * @param compactor automatic compaction
   * @param cancellation cancellation state checked between steps
   * @param policy agent loop limits
   * @param mapper result mapper
   * @return the use case
   */
  @Bean
  public RunAgentTurnUseCase runAgentTurnUseCase(
      ConversationPort conversationPort,
      SessionRegistrar sessionRegistrar,
      ChatModelPort chatModelPort,
      ToolExecutorPort toolExecutorPort,
      TurnOutputPort turnOutputPort,
      ConversationCompactor compactor,
      TurnCancellation cancellation,
      AgentPolicy policy,
      TurnApplicationMapper mapper) {
    return new AgentLoopService(conversationPort, sessionRegistrar, chatModelPort,
        toolExecutorPort, turnOutputPort, compactor, cancellation, policy, mapper);
  }

  /**
   * Turn cancellation use case.
   *
   * @param chatModelPort language model
   * @param cancellation cancellation state checked by the agent loop
   * @return the use case
   */
  @Bean
  public CancelTurnUseCase cancelTurnUseCase(
      ChatModelPort chatModelPort, TurnCancellation cancellation) {
    return new CancelTurnService(chatModelPort, cancellation);
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
   * Stored sessions purge use case.
   *
   * @param sessionCatalogPort session metadata storage
   * @param mapper result mapper
   * @return the use case
   */
  @Bean
  public PurgeSessionsUseCase purgeSessionsUseCase(
      SessionCatalogPort sessionCatalogPort, TurnApplicationMapper mapper) {
    return new PurgeSessionsService(sessionCatalogPort, mapper);
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
   * Answer saving use case.
   *
   * @param conversationPort conversation history storage
   * @param answerFilePort writes the file
   * @param clock source of generated file names
   * @param mapper result mapper
   * @return the use case
   */
  @Bean
  public SaveAnswerUseCase saveAnswerUseCase(ConversationPort conversationPort,
      AnswerFilePort answerFilePort, Clock clock, TurnApplicationMapper mapper) {
    return new SaveAnswerService(conversationPort, answerFilePort, clock, mapper);
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

  /**
   * Loaded models query.
   *
   * @param runningModelsPort models loaded by the model server
   * @param mapper result mapper
   * @return the query
   */
  @Bean
  public ListRunningModelsQuery listRunningModelsQuery(
      RunningModelsPort runningModelsPort, TurnApplicationMapper mapper) {
    return new ListRunningModelsQueryService(runningModelsPort, mapper);
  }

  /**
   * Available tools query.
   *
   * @param toolExecutorPort tool registry
   * @param mapper result mapper
   * @return the query
   */
  @Bean
  public ListToolsQuery listToolsQuery(
      ToolExecutorPort toolExecutorPort, TurnApplicationMapper mapper) {
    return new ListToolsQueryService(toolExecutorPort, mapper);
  }
}
