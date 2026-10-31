package dev.localllm.harness.infrastructure.out.tools;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mapstruct.factory.Mappers;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.ToolResult;
import dev.localllm.harness.infrastructure.TestProperties;
import dev.localllm.harness.infrastructure.config.HarnessProperties;
import dev.localllm.harness.infrastructure.out.mcp.McpTestSupport;
import dev.localllm.harness.infrastructure.out.tools.mapper.ToolSpecSpringAiMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code run_command} called through the registry with the real shell.
 */
class ShellToolsTest {

  private static final Pattern PID = Pattern.compile("pid=(\\d+)");

  @TempDir
  private Path cwd;

  private SpringAiToolExecutorAdapter executor;

  @BeforeEach
  void setUp() {
    HarnessProperties properties =
        TestProperties.create(cwd.resolve("home"), false);
    executor = new SpringAiToolExecutorAdapter(
        List.of(MethodToolCallbackProvider.builder()
            .toolObjects(new ShellTools(properties)).build()),
        McpTestSupport.noServers(), Mappers.getMapper(ToolSpecSpringAiMapper.class), properties);
  }

  @Test
  void runCommand_Success_ReturnsExitCodeAndStdoutFromWorkingDirectory() throws IOException {
    // Act
    ToolResult result = run("{\"command\":\"pwd\"}");

    // Assert
    assertThat(result.error()).isFalse();
    assertThat(result.output()).isEqualTo(
        "exit code: 0\nstdout:\n" + cwd.toRealPath().toString() + "\n");
  }

  @Test
  void runCommand_FailingCommand_ReturnsExitCodeAndStderr() {
    // Act
    ToolResult result = run("{\"command\":\"echo out; echo oops >&2; exit 3\"}");

    // Assert
    assertThat(result.error()).isFalse();
    assertThat(result.output()).isEqualTo("exit code: 3\nstdout:\nout\nstderr:\noops\n");
  }

  @Test
  void runCommand_ReadingStdin_GetsEndOfInputInsteadOfHanging() {
    // Act
    ToolResult result = run("{\"command\":\"cat; echo done\",\"timeoutSec\":5}");

    // Assert
    assertThat(result.output()).isEqualTo("exit code: 0\nstdout:\ndone\n");
  }

  @Test
  void runCommand_Timeout_KillsProcessTreeAndReturnsOutputSoFar() {
    // Act
    ToolResult result = run("""
        {"command":"echo started; sleep 30 & echo pid=$!; wait","timeoutSec":1}""");

    // Assert
    assertThat(result.output()).startsWith("timeout after 1s; the process was killed\nstdout:\n")
        .contains("started");
    assertThat(result.duration()).isLessThan(Duration.ofSeconds(10));
    Matcher pid = PID.matcher(result.output());
    assertThat(pid.find()).isTrue();
    Optional<ProcessHandle> child = ProcessHandle.of(Long.parseLong(pid.group(1)));
    child.ifPresent(handle -> handle.onExit().orTimeout(5, TimeUnit.SECONDS).join());
    assertThat(ProcessHandle.of(Long.parseLong(pid.group(1))).map(ProcessHandle::isAlive))
        .isNotEqualTo(Optional.of(true));
  }

  private ToolResult run(String argumentsJson) {
    return executor.execute(new ToolCall("c1", "run_command", argumentsJson), SessionId.newId(),
        cwd);
  }
}
