package dev.localllm.harness.infrastructure.out.ollama.toolcall;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.localllm.harness.application.model.ToolSource;
import dev.localllm.harness.application.model.ToolSpec;

import static org.assertj.core.api.Assertions.assertThat;

class ToolBlockParserTest {

  private static final List<ToolSpec> TOOLS = List.of(
      new ToolSpec("read_file", "Read a file", """
          {"type":"object","properties":{"path":{"type":"string"},"limit":{"type":"integer"}},\
          "required":["path"]}""", ToolSource.BUILTIN),
      new ToolSpec("write_file", "Write a file", """
          {"type":"object","properties":{"path":{"type":"string"},\
          "content":{"type":"string"}},"required":["path","content"]}""", ToolSource.BUILTIN),
      new ToolSpec("list_dir", "List a directory",
          "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}}}",
          ToolSource.BUILTIN));

  private final ToolBlockParser parser = new ToolBlockParser();

  @Test
  void parse_ValidBlockAfterText_ReturnsCallAndTextBeforeIt() {
    // Act
    ToolBlock block = parser.parse("""
        Let me read it.

        ```tool
        {"name": "read_file", "args": {"path": "Calc.java", "limit": 20}}
        ```""", TOOLS);

    // Assert
    assertThat(block.error()).isNull();
    assertThat(block.text()).isEqualTo("Let me read it.");
    assertThat(block.call()).isNotNull();
    assertThat(block.call().name()).isEqualTo("read_file");
    assertThat(block.call().argumentsJson()).isEqualTo("{\"path\":\"Calc.java\",\"limit\":20}");
    assertThat(block.call().id()).isNotBlank();
  }

  @Test
  void parse_WithoutBlock_ReturnsTextOnly() {
    // Act
    ToolBlock block = parser.parse("Here is the answer:\n```java\nint x;\n```", TOOLS);

    // Assert
    assertThat(block).isEqualTo(
        new ToolBlock("Here is the answer:\n```java\nint x;\n```", null, null));
  }

  @Test
  void parse_StringsWithFencesBracesAndEscapes_KeepsThemInArguments() {
    // Act
    ToolBlock block = parser.parse("""
        ```tool
        {"name":"write_file","args":{"path":"README.md",\
        "content":"# T\\n```java\\nclass A { \\"x\\" }\\n```\\n"}}
        ```
        TOOL_RESULT write_file: invented""", TOOLS);

    // Assert
    assertThat(block.error()).isNull();
    assertThat(block.text()).isEmpty();
    assertThat(block.call()).isNotNull();
    assertThat(block.call().argumentsJson()).isEqualTo(
        "{\"path\":\"README.md\",\"content\":\"# T\\n```java\\nclass A { \\\"x\\\" }\\n```\\n\"}");
  }

  @Test
  void parse_SeveralBlocks_TakesOnlyTheFirst() {
    // Act
    ToolBlock block = parser.parse("""
        ```tool
        {"name": "list_dir", "args": {}}
        ```
        ```tool
        {"name": "read_file", "args": {"path": "A.java"}}
        ```""", TOOLS);

    // Assert
    assertThat(block.call()).isNotNull();
    assertThat(block.call().name()).isEqualTo("list_dir");
  }

  @Test
  void parse_WithoutArgsForToolWithoutRequiredArguments_UsesEmptyObject() {
    // Act
    ToolBlock block = parser.parse("```tool\n{\"name\": \"list_dir\"}\n```", TOOLS);

    // Assert
    assertThat(block.call()).isNotNull();
    assertThat(block.call().argumentsJson()).isEqualTo("{}");
  }

  @Test
  void parse_BrokenJson_ReturnsErrorAndWholeText() {
    // Arrange
    String content = "Reading\n```tool\n{\"name\": \"read_file\", \"args\": {\"path\": }\n```";

    // Act
    ToolBlock block = parser.parse(content, TOOLS);

    // Assert
    assertThat(block.call()).isNull();
    assertThat(block.text()).isEqualTo(content);
    assertThat(block.error()).startsWith("invalid JSON in the tool block: ");
  }

  @Test
  void parse_TextInsteadOfObject_ReturnsError() {
    // Act
    ToolBlock block = parser.parse("```tool\nread_file Calc.java\n```", TOOLS);

    // Assert
    assertThat(block.error()).isEqualTo("the tool block must contain one JSON object");
  }

  @Test
  void parse_UnknownTool_ReturnsErrorWithAvailableTools() {
    // Act
    ToolBlock block = parser.parse("```tool\n{\"name\": \"rm\", \"args\": {}}\n```", TOOLS);

    // Assert
    assertThat(block.error())
        .isEqualTo("unknown tool rm; available tools: list_dir, read_file, write_file");
  }

  @Test
  void parse_MissingName_ReturnsError() {
    // Act
    ToolBlock block = parser.parse("```tool\n{\"args\": {}}\n```", TOOLS);

    // Assert
    assertThat(block.error()).isEqualTo("the tool block has no \"name\"");
  }

  @Test
  void parse_MissingRequiredArgument_ReturnsError() {
    // Act
    ToolBlock block = parser.parse(
        "```tool\n{\"name\": \"write_file\", \"args\": {\"path\": \"a.txt\"}}\n```", TOOLS);

    // Assert
    assertThat(block.error())
        .isEqualTo("missing required argument \"content\" of write_file");
  }

  @Test
  void parse_WrongArgumentName_ReportsMissingRequiredArgument() {
    // Act
    ToolBlock block = parser.parse(
        "```tool\n{\"name\": \"read_file\", \"args\": {\"file\": \"a.txt\"}}\n```", TOOLS);

    // Assert
    assertThat(block.error()).isEqualTo("missing required argument \"path\" of read_file");
  }

  @Test
  void parse_ExtraArgument_ReturnsError() {
    // Act
    ToolBlock block = parser.parse("""
        ```tool
        {"name": "read_file", "args": {"path": "a.txt", "encoding": "utf-8"}}
        ```""", TOOLS);

    // Assert
    assertThat(block.error())
        .isEqualTo("unknown argument \"encoding\" of read_file; arguments: path, limit");
  }

  @Test
  void parse_ArgsNotAnObject_ReturnsError() {
    // Act
    ToolBlock block = parser.parse(
        "```tool\n{\"name\": \"read_file\", \"args\": \"Calc.java\"}\n```", TOOLS);

    // Assert
    assertThat(block.error()).isEqualTo("\"args\" of read_file must be a JSON object");
  }

  @Test
  void isClosed_TracksTheBlockAsItStreams() {
    // Arrange
    String opened = "Text\n```tool\n{\"name\": \"list_dir\", \"args\": {\"path\": \"}```\"";

    // Act & Assert
    assertThat(parser.isClosed("Text\n```java\nx\n```")).isFalse();
    assertThat(parser.isClosed(opened)).isFalse();
    assertThat(parser.isClosed(opened + "}}\n")).isFalse();
    assertThat(parser.isClosed(opened + "}}\n``")).isFalse();
    assertThat(parser.isClosed(opened + "}}\n```")).isTrue();
    assertThat(parser.isClosed("```tool\n{\"name\": }\n```")).isFalse();
  }
}
