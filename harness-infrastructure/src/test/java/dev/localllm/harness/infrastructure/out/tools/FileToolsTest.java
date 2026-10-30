package dev.localllm.harness.infrastructure.out.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mapstruct.factory.Mappers;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.ToolResult;
import dev.localllm.harness.application.model.ToolSource;
import dev.localllm.harness.application.model.ToolSpec;
import dev.localllm.harness.infrastructure.TestProperties;
import dev.localllm.harness.infrastructure.out.mcp.McpTestSupport;
import dev.localllm.harness.infrastructure.out.tools.mapper.ToolSpecSpringAiMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The built-in file tools called through the registry, as the agent loop calls them.
 */
class FileToolsTest {

  @TempDir
  private Path cwd;

  private SpringAiToolExecutorAdapter executor;

  @BeforeEach
  void setUp() throws IOException {
    executor = new SpringAiToolExecutorAdapter(
        List.of(MethodToolCallbackProvider.builder().toolObjects(new FileTools()).build()),
        McpTestSupport.noServers(),
        Mappers.getMapper(ToolSpecSpringAiMapper.class),
        TestProperties.create(cwd.resolve("home"), "http://localhost:11434", false));
    write("Calc.java", "class Calc {\n  int add(int a, int b) {\n    return a + b;\n  }\n}\n");
    write("src/main/App.java", "class App {\n  // TODO start\n}\n");
    write("src/main/notes.txt", "TODO in text\n");
    write(".git/HEAD.java", "class Hidden {}\n");
  }

  @Test
  void available_ListsReadToolsWithSchemas() {
    // Act
    List<ToolSpec> tools = executor.available();

    // Assert
    assertThat(tools).extracting(ToolSpec::name)
        .containsExactlyInAnyOrder(
            "read_file", "list_dir", "glob", "grep", "write_file", "edit_file");
    assertThat(tools).extracting(ToolSpec::source).containsOnly(ToolSource.BUILTIN);
    ToolSpec readFile = tools.stream().filter(tool -> tool.name().equals("read_file"))
        .findFirst().orElseThrow();
    assertThat(readFile.inputSchema())
        .contains("\"path\"", "\"offset\"", "\"limit\"")
        .contains("\"required\" : [ \"path\" ]")
        .doesNotContain("toolContext");
  }

  @Test
  void readFile_WholeFile_ReturnsNumberedLines() {
    // Act
    ToolResult result = call("read_file", "{\"path\":\"Calc.java\"}");

    // Assert
    assertThat(result.error()).isFalse();
    assertThat(result.output()).isEqualTo("""
             1\tclass Calc {
             2\t  int add(int a, int b) {
             3\t    return a + b;
             4\t  }
             5\t}
        """);
  }

  @Test
  void readFile_WithOffsetAndLimit_ReturnsPartAndNote() {
    // Act
    ToolResult result = call("read_file", "{\"path\":\"Calc.java\",\"offset\":2,\"limit\":2}");

    // Assert
    assertThat(result.output()).isEqualTo("""
             2\t  int add(int a, int b) {
             3\t    return a + b;
        [lines 2-3 of 5; use offset to read more]
        """);
  }

  @Test
  void readFile_WithAbsolutePath_ReadsIt() {
    // Act
    ToolResult result = call("read_file",
        "{\"path\":\"" + cwd.resolve("src/main/notes.txt") + "\"}");

    // Assert
    assertThat(result.output()).contains("TODO in text");
  }

  @Test
  void readFile_MissingFile_ReturnsError() {
    // Act
    ToolResult result = call("read_file", "{\"path\":\"Nope.java\"}");

    // Assert
    assertThat(result.error()).isTrue();
    assertThat(result.output()).isEqualTo("Error: File not found: Nope.java");
  }

  @Test
  void readFile_Directory_SuggestsListDir() {
    // Act
    ToolResult result = call("read_file", "{\"path\":\"src\"}");

    // Assert
    assertThat(result.output()).isEqualTo("Error: src is a directory, use list_dir");
  }

  @Test
  void readFile_BinaryFile_ReturnsError() throws IOException {
    // Arrange
    Files.write(cwd.resolve("image.bin"), new byte[] {(byte) 0xC3, (byte) 0x28, 0, 1});

    // Act
    ToolResult result = call("read_file", "{\"path\":\"image.bin\"}");

    // Assert
    assertThat(result.output()).isEqualTo("Error: Not a UTF-8 text file: image.bin");
  }

  @Test
  void readFile_OffsetPastEnd_ReturnsError() {
    // Act
    ToolResult result = call("read_file", "{\"path\":\"Calc.java\",\"offset\":9}");

    // Assert
    assertThat(result.output()).contains("the file has 5 lines");
  }

  @Test
  void readFile_LongOutput_IsTruncatedToConfiguredSize() throws IOException {
    // Arrange
    write("big.txt", "x".repeat(100) + "\n".repeat(1) + ("line\n").repeat(3000));

    // Act
    ToolResult result = call("read_file", "{\"path\":\"big.txt\"}");

    // Assert
    assertThat(result.truncated()).isTrue();
    assertThat(result.output()).hasSizeLessThan(TestProperties.MAX_OUTPUT_CHARS + 100)
        .endsWith("characters]");
  }

  @Test
  void listDir_WithoutPath_ListsWorkingDirectorySorted() {
    // Act
    ToolResult result = call("list_dir", "{}");

    // Assert
    assertThat(result.output()).isEqualTo(".git/\nCalc.java\nsrc/\n");
  }

  @Test
  void listDir_MissingDirectory_ReturnsError() {
    // Act
    ToolResult result = call("list_dir", "{\"path\":\"missing\"}");

    // Assert
    assertThat(result.output()).isEqualTo("Error: Directory not found: missing");
  }

  @Test
  void glob_RecursivePattern_MatchesTopLevelAndNestedFilesButSkipsGit() {
    // Act
    ToolResult result = call("glob", "{\"pattern\":\"**/*.java\"}");

    // Assert
    assertThat(result.output()).isEqualTo("Calc.java\nsrc/main/App.java\n");
  }

  @Test
  void glob_WithoutMatches_SaysSo() {
    // Act
    ToolResult result = call("glob", "{\"pattern\":\"*.kt\"}");

    // Assert
    assertThat(result.output()).isEqualTo("No files match *.kt");
  }

  @Test
  void grep_WithNameGlob_ReturnsMatchingLinesOfMatchingFiles() {
    // Act
    ToolResult result = call("grep", "{\"pattern\":\"TODO\",\"glob\":\"*.java\"}");

    // Assert
    assertThat(result.output()).isEqualTo("src/main/App.java:2: // TODO start\n");
  }

  @Test
  void grep_InSubdirectory_SearchesOnlyThere() {
    // Act
    ToolResult result = call("grep", "{\"pattern\":\"a \\\\+ b\",\"path\":\"src\"}");

    // Assert
    assertThat(result.output()).isEqualTo("No matches for a \\+ b");
  }

  @Test
  void grep_InvalidRegex_ReturnsError() {
    // Act
    ToolResult result = call("grep", "{\"pattern\":\"(unclosed\"}");

    // Assert
    assertThat(result.error()).isTrue();
    assertThat(result.output()).startsWith("Error: Unclosed group");
  }

  @Test
  void writeFile_NewFileInMissingDirectory_CreatesIt() throws IOException {
    // Act
    ToolResult result = call("write_file",
        "{\"path\":\"out/new/Hello.txt\",\"content\":\"one\\ntwo\\n\"}");

    // Assert
    assertThat(result.error()).isFalse();
    assertThat(result.output()).isEqualTo("Created out/new/Hello.txt (2 lines)");
    assertThat(Files.readString(cwd.resolve("out/new/Hello.txt"))).isEqualTo("one\ntwo\n");
  }

  @Test
  void writeFile_ExistingFile_OverwritesIt() throws IOException {
    // Act
    ToolResult result = call("write_file", "{\"path\":\"Calc.java\",\"content\":\"x\"}");

    // Assert
    assertThat(result.output()).isEqualTo("Overwrote Calc.java (1 lines)");
    assertThat(Files.readString(cwd.resolve("Calc.java"))).isEqualTo("x");
  }

  @Test
  void writeFile_Directory_ReturnsError() {
    // Act
    ToolResult result = call("write_file", "{\"path\":\"src\",\"content\":\"x\"}");

    // Assert
    assertThat(result.error()).isTrue();
    assertThat(result.output()).isEqualTo("Error: src is a directory");
  }

  @Test
  void editFile_UniqueText_ReplacesItAndReturnsDiff() throws IOException {
    // Act
    ToolResult result = call("edit_file", """
        {"path":"Calc.java","oldText":"return a + b;","newText":"return a - b;"}""");

    // Assert
    assertThat(result.error()).isFalse();
    assertThat(result.output()).isEqualTo("""
        Edited Calc.java
        @@ -1,5 +1,5 @@
         class Calc {
           int add(int a, int b) {
        -    return a + b;
        +    return a - b;
           }
         }
        """);
    assertThat(Files.readString(cwd.resolve("Calc.java"))).contains("return a - b;");
  }

  @Test
  void editFile_TextOccursTwice_ReturnsErrorAndKeepsFile() throws IOException {
    // Act
    ToolResult result = call("edit_file", """
        {"path":"Calc.java","oldText":"int","newText":"long"}""");

    // Assert
    assertThat(result.error()).isTrue();
    assertThat(result.output()).isEqualTo(
        "Error: oldText occurs 3 times in Calc.java; add surrounding lines to make it unique");
    assertThat(Files.readString(cwd.resolve("Calc.java"))).doesNotContain("long");
  }

  @Test
  void editFile_TextMissing_ReturnsError() {
    // Act
    ToolResult result = call("edit_file", """
        {"path":"Calc.java","oldText":"multiply","newText":"x"}""");

    // Assert
    assertThat(result.error()).isTrue();
    assertThat(result.output()).startsWith("Error: oldText not found in Calc.java");
  }

  @Test
  void editFile_EmptyOldText_ReturnsError() {
    // Act
    ToolResult result = call("edit_file", """
        {"path":"Calc.java","oldText":"","newText":"x"}""");

    // Assert
    assertThat(result.error()).isTrue();
    assertThat(result.output()).startsWith("Error: oldText is empty");
  }

  @Test
  void editFile_MissingFile_ReturnsError() {
    // Act
    ToolResult result = call("edit_file", """
        {"path":"Nope.java","oldText":"a","newText":"b"}""");

    // Assert
    assertThat(result.output()).isEqualTo("Error: File not found: Nope.java");
  }

  @Test
  void execute_UnknownTool_ReturnsRejectedResult() {
    // Act
    ToolResult result = call("rm_rf", "{}");

    // Assert
    assertThat(result.error()).isTrue();
    assertThat(result.output()).isEqualTo("Error: unknown tool rm_rf");
  }

  @Test
  void execute_InvalidArgumentsJson_ReturnsError() {
    // Act
    ToolResult result = call("read_file", "not json");

    // Assert
    assertThat(result.error()).isTrue();
    assertThat(result.output()).startsWith("Error: ");
  }

  private ToolResult call(String name, String argumentsJson) {
    return executor.execute(new ToolCall("c1", name, argumentsJson), SessionId.newId(), cwd);
  }

  private void write(String relative, String content) throws IOException {
    Path file = cwd.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
