package dev.localllm.harness.adapter.anthropic.toolcall;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import dev.localllm.harness.application.model.ToolSpec;

/**
 * Checks the input of a tool call against the top level of the tool's JSON Schema.
 *
 * <p>With eager input streaming the API passes the input through unchecked, so a call cut short
 * or written with a wrong argument must be caught here, before the tool runs. The check covers
 * what a truncated or malformed call breaks: the input is a JSON object, the required properties
 * are present, the declared properties have the declared {@code type}, and no unknown property is
 * sent when {@code additionalProperties} is {@code false}. Nested schemas are left to the tool.
 */
@Component
public class ToolInputValidator {

  /**
   * Finds why an input does not match the schema of its tool.
   *
   * @param tool tool with its JSON Schema
   * @param input parsed input of the call
   * @return the first problem, empty if the input matches
   */
  public Optional<String> findProblem(ToolSpec tool, JsonNode input) {
    if (!input.isObject()) {
      return Optional.of("the input is not a JSON object");
    }
    JsonNode schema = readSchema(tool.inputSchema());
    for (JsonNode required : schema.path("required")) {
      if (!input.has(required.asString())) {
        return Optional.of("required property \"" + required.asString() + "\" is missing");
      }
    }
    JsonNode properties = schema.path("properties");
    boolean closed = schema.path("additionalProperties").isBoolean()
        && !schema.path("additionalProperties").asBoolean();
    for (Map.Entry<String, JsonNode> field : input.properties()) {
      JsonNode property = properties.path(field.getKey());
      if (property.isMissingNode()) {
        if (closed) {
          return Optional.of("property \"" + field.getKey() + "\" is not allowed");
        }
        continue;
      }
      JsonNode type = property.path("type");
      if (!type.isMissingNode() && !matchesType(field.getValue(), type)) {
        return Optional.of("property \"" + field.getKey() + "\" must be of type " + type);
      }
    }
    return Optional.empty();
  }

  private static boolean matchesType(JsonNode value, JsonNode type) {
    if (!type.isArray()) {
      return matchesSingleType(value, type.asString());
    }
    for (JsonNode alternative : type) {
      if (matchesSingleType(value, alternative.asString())) {
        return true;
      }
    }
    return false;
  }

  private static boolean matchesSingleType(JsonNode value, String type) {
    return switch (type) {
      case "string" -> value.isString();
      case "integer" -> value.isIntegralNumber()
          || (value.isNumber() && value.asDouble() == Math.rint(value.asDouble()));
      case "number" -> value.isNumber();
      case "boolean" -> value.isBoolean();
      case "array" -> value.isArray();
      case "object" -> value.isObject();
      case "null" -> value.isNull();
      default -> true;
    };
  }

  private static JsonNode readSchema(String schema) {
    JsonNode empty = JsonMapper.shared().createObjectNode();
    try {
      JsonNode node = JsonMapper.shared().readTree(schema);
      return node != null && node.isObject() ? node : empty;
    } catch (JacksonException e) {
      // A tool without a readable schema accepts any object.
      return empty;
    }
  }
}
