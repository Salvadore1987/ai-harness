package dev.localllm.harness.application.port.out;

import java.util.List;

import dev.localllm.harness.application.model.ProviderInfo;

/**
 * Tells which LLM providers exist and whether they can be used.
 */
public interface ProviderCatalogPort {

  /**
   * Returns every provider the harness is built with, available or not.
   *
   * @return providers in the order of {@link dev.localllm.harness.application.model.LlmProvider}
   */
  List<ProviderInfo> listProviders();
}
