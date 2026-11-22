package dev.localllm.harness.adapter.anthropic.mapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import org.mapstruct.Mapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import dev.localllm.harness.application.model.ToolSpec;

/**
 * Converts the tools offered to the model into Messages API tool definitions.
 *
 * <p>Every tool streams its input eagerly ({@code eager_input_streaming}): large arguments such
 * as file contents arrive as they are generated, and the adapter checks the input against the
 * schema itself, because the API no longer does.
 */
@Mapper
public interface ToolSpecAnthropicMapper {

  /** Schema keys the SDK sets through dedicated builder methods. */
  Set<String> TYPED_SCHEMA_KEYS = Set.of("type", "properties", "required");

  /**
   * Converts the tools, keeping their order.
   *
   * @param tools core tool descriptions
   * @return tool definitions
   */
  List<Tool> toTools(List<ToolSpec> tools);

  /**
   * Converts one tool.
   *
   * @param tool core tool description with its JSON Schema
   * @return tool definition with eager input streaming
   */
  default Tool toTool(ToolSpec tool) {
    return Tool.builder()
        .name(tool.name())
        .description(tool.description())
        .inputSchema(toInputSchema(tool.inputSchema()))
        .eagerInputStreaming(true)
        .build();
  }

  /**
   * Converts a JSON Schema of an object into the SDK schema type.
   *
   * @param schema JSON Schema text
   * @return schema; an object without properties if the text is not a JSON object
   */
  default Tool.InputSchema toInputSchema(String schema) {
    Tool.InputSchema.Builder builder = Tool.InputSchema.builder();
    Map<?, ?> node = readSchema(schema);
    if (node.get("properties") instanceof Map<?, ?> properties) {
      Tool.InputSchema.Properties.Builder propertiesBuilder =
          Tool.InputSchema.Properties.builder();
      properties.forEach((name, property) -> propertiesBuilder.putAdditionalProperty(
          String.valueOf(name), JsonValue.from(property)));
      builder.properties(propertiesBuilder.build());
    }
    if (node.get("required") instanceof List<?> required) {
      builder.required(required.stream().map(String::valueOf).toList());
    }
    node.forEach((key, value) -> {
      if (!TYPED_SCHEMA_KEYS.contains(String.valueOf(key))) {
        builder.putAdditionalProperty(String.valueOf(key), JsonValue.from(value));
      }
    });
    return builder.build();
  }

  private static Map<?, ?> readSchema(String schema) {
    try {
      return JsonMapper.shared().readValue(schema, Object.class) instanceof Map<?, ?> object
          ? object : Map.of();
    } catch (JacksonException e) {
      // An unreadable schema is sent as an object without properties.
      return Map.of();
    }
  }
}
