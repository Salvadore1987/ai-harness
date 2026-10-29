package dev.localllm.harness.bootstrap.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.port.in.CancelTurnUseCase;
import dev.localllm.harness.application.port.in.ListModelsQuery;
import dev.localllm.harness.application.port.in.SendMessageUseCase;
import dev.localllm.harness.application.port.in.StartSessionUseCase;
import dev.localllm.harness.application.port.in.SwitchModelUseCase;
import dev.localllm.harness.application.port.in.ToggleThinkingUseCase;
import dev.localllm.harness.application.port.out.ChatModelPort;
import dev.localllm.harness.application.port.out.ConversationPort;
import dev.localllm.harness.application.port.out.ModelCatalogPort;
import dev.localllm.harness.application.port.out.TurnOutputPort;
import dev.localllm.harness.application.service.CancelTurnService;
import dev.localllm.harness.application.service.ListModelsQueryService;
import dev.localllm.harness.application.service.SendMessageService;
import dev.localllm.harness.application.service.StartSessionService;
import dev.localllm.harness.application.service.SwitchModelService;
import dev.localllm.harness.application.service.ToggleThinkingService;

/**
 * Wires the framework-free application services to the infrastructure adapters.
 */
@Configuration(proxyBeanMethods = false)
public class BeanConfig {

  /**
   * Chat turn use case.
   *
   * @param conversationPort conversation history storage
   * @param chatModelPort language model
   * @param turnOutputPort terminal presentation
   * @param mapper result mapper
   * @return the use case
   */
  @Bean
  public SendMessageUseCase sendMessageUseCase(
      ConversationPort conversationPort,
      ChatModelPort chatModelPort,
      TurnOutputPort turnOutputPort,
      TurnApplicationMapper mapper) {
    return new SendMessageService(conversationPort, chatModelPort, turnOutputPort, mapper);
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
   * @param conversationPort conversation history storage
   * @param mapper result mapper
   * @return the use case
   */
  @Bean
  public StartSessionUseCase startSessionUseCase(
      ConversationPort conversationPort, TurnApplicationMapper mapper) {
    return new StartSessionService(conversationPort, mapper);
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
