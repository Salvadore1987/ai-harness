package dev.localllm.harness.adapter.anthropic.toolcall;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import dev.localllm.harness.application.model.ToolSource;
import dev.localllm.harness.application.model.ToolSpec;

import static org.assertj.core.api.Assertions.assertThat;

class ToolInputValidatorTest {

  private static final ToolSpec RUN_COMMAND = new ToolSpec("run_command", "Runs a command",
      "{\"type\":\"object\",\"properties\":{\"command\":{\"type\":\"string\"},"
          + "\"timeout_seconds\":{\"type\":[\"integer\",\"null\"]}},"
          + "\"required\":[\"command\"],\"additionalProperties\":false}",
      ToolSource.BUILTIN, null);

  private final ToolInputValidator validator = new ToolInputValidator();

  @Test
  void findProblem_WithMatchingInput_FindsNone() throws Exception {
    // Act
    Optional<String> problem =
        validator.findProblem(RUN_COMMAND, json("{\"command\":\"ls\",\"timeout_seconds\":5}"));

    // Assert
    assertThat(problem).isEmpty();
  }

  @Test
  void findProblem_WithWrongType_NamesTheProperty() throws Exception {
    // Act
    Optional<String> problem =
        validator.findProblem(RUN_COMMAND, json("{\"command\":[\"ls\"]}"));

    // Assert
    assertThat(problem).contains("property \"command\" must be of type \"string\"");
  }

  @Test
  void findProblem_WithUnknownPropertyOfClosedSchema_RejectsIt() throws Exception {
    // Act
    Optional<String> problem =
        validator.findProblem(RUN_COMMAND, json("{\"command\":\"ls\",\"cwd\":\"/\"}"));

    // Assert
    assertThat(problem).contains("property \"cwd\" is not allowed");
  }

  @Test
  void findProblem_WithTypeUnion_AcceptsEveryAlternative() throws Exception {
    // Act
    Optional<String> problem = validator.findProblem(
        RUN_COMMAND, json("{\"command\":\"ls\",\"timeout_seconds\":null}"));

    // Assert
    assertThat(problem).isEmpty();
  }

  @Test
  void findProblem_WithArrayInput_SaysItIsNotAnObject() throws Exception {
    // Act
    Optional<String> problem = validator.findProblem(RUN_COMMAND, json("[1]"));

    // Assert
    assertThat(problem).contains("the input is not a JSON object");
  }

  private static JsonNode json(String text) throws Exception {
    return JsonMapper.shared().readTree(text);
  }
}
