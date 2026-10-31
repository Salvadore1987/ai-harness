package dev.localllm.harness.adapter.ollama.mapper;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.ai.ollama.api.OllamaApi;

import dev.localllm.harness.application.model.ModelCapability;
import dev.localllm.harness.application.model.ModelInfo;

/**
 * Converts Ollama model metadata into the core model description.
 */
@Mapper
public interface ModelInfoOllamaMapper {

  /** Placeholder for metadata the server did not report. */
  String UNKNOWN = "?";

  /**
   * Combines the model tag with the {@code /api/show} answer.
   *
   * @param name model tag as requested
   * @param response metadata returned by Ollama
   * @return model description
   */
  @Mapping(target = "name", source = "name")
  @Mapping(
      target = "parameterSize",
      source = "response.details.parameterSize",
      defaultValue = UNKNOWN)
  @Mapping(
      target = "quantization",
      source = "response.details.quantizationLevel",
      defaultValue = UNKNOWN)
  @Mapping(target = "capabilities", source = "response.capabilities")
  ModelInfo toModelInfo(String name, OllamaApi.ShowModelResponse response);

  /**
   * Keeps the capabilities the harness cares about.
   *
   * @param capabilities capability names reported by Ollama, such as {@code thinking}
   * @return known capabilities
   */
  default Set<ModelCapability> toCapabilities(@Nullable List<String> capabilities) {
    Set<ModelCapability> result = EnumSet.noneOf(ModelCapability.class);
    if (capabilities == null) {
      return result;
    }
    for (String capability : capabilities) {
      switch (capability) {
        case "thinking" -> result.add(ModelCapability.THINKING);
        case "tools" -> result.add(ModelCapability.TOOLS);
        default -> {
          // other capabilities (completion, vision, insert) are not used by the harness
        }
      }
    }
    return result;
  }
}
