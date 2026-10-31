package dev.localllm.harness.infrastructure.out.routing.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import dev.localllm.harness.application.model.ProviderInfo;
import dev.localllm.harness.application.port.out.LlmProviderAdapters;

/**
 * Describes provider adapters as provider state.
 */
@Mapper
public interface ProviderInfoMapper {

  /**
   * Reads the state of a provider from its adapters.
   *
   * @param adapters adapters of one provider
   * @return provider state
   */
  @Mapping(target = "provider", expression = "java(adapters.provider())")
  @Mapping(target = "available", expression = "java(adapters.isAvailable())")
  @Mapping(target = "defaultModel", expression = "java(adapters.defaultModel())")
  ProviderInfo toProviderInfo(LlmProviderAdapters adapters);
}
