package dev.localllm.harness.bootstrap;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import dev.localllm.harness.infrastructure.fixture.EchoMcpServer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the native executable against a stubbed Ollama and a stdio MCP server, so that missing
 * reachability metadata shows up before a release rather than at the user.
 *
 * <p>Started with {@code ./gradlew :harness-bootstrap:test -PincludeTags=native}, which builds
 * the image first.
 */
@Tag("native")
class NativeImageSmokeIT {

  @RegisterExtension
  static WireMockExtension ollama = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicPort())
      .build();

  private static final Path BINARY = Path.of(System.getProperty("harness.native.binary", ""));
  private static final String MODEL = "qwen3.5:27b";
  private static final String CHUNK =
      "{\"model\":\"" + MODEL + "\",\"message\":{\"role\":\"assistant\",%s},%s}\n";
  private static final String DONE =
      "\"done\":true,\"done_reason\":\"stop\",\"prompt_eval_count\":20,\"eval_count\":3";

  @TempDir
  private Path home;

  @TempDir
  private Path cwd;

  @BeforeEach
  void stubOllama() throws IOException {
    ollama.stubFor(post("/api/show").willReturn(okJson(
        "{\"details\":{\"parameter_size\":\"27B\"},"
            + "\"capabilities\":[\"completion\",\"tools\",\"thinking\"]}")));
    ollama.stubFor(get("/api/ps").willReturn(okJson(
        "{\"models\":[{\"name\":\"" + MODEL + "\",\"context_length\":65536}]}")));
    ollama.stubFor(get("/api/tags").willReturn(okJson(
        "{\"models\":[{\"name\":\"" + MODEL + "\",\"model\":\"" + MODEL + "\"}]}")));
    ollama.stubFor(post("/api/chat").atPriority(10).willReturn(stream(answer("готово"))));
    Files.writeString(cwd.resolve("Calc.java"), "class Calc {}\n", StandardCharsets.UTF_8);
    String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    Files.writeString(home.resolve("mcp.json"), """
        {"mcpServers": {"echo": {"command": "%s", "args": ["-cp", "%s", "%s"]}}}
        """.formatted(java, System.getProperty("java.class.path"),
        EchoMcpServer.class.getName()), StandardCharsets.UTF_8);
  }

  @Test
  void version_StartsWithinASecond() {
    // Arrange: macOS checks a freshly built executable on its first launch, which takes ~2 s
    run(List.of("--version"), "");

    // Act
    Result result = run(List.of("--version"), "");

    // Assert
    assertThat(result.exitCode()).isZero();
    assertThat(result.stdout()).contains("harness 0.1.0");
    assertThat(result.elapsed()).isLessThan(Duration.ofSeconds(1));
  }

  @Test
  void repl_SlashCommands_RunWithoutReflectionErrors() {
    // Act
    Result result = run(List.of(),
        "/help\n/tools\n/model\n/context\n/sessions\n/think off\n/mode agent\n/exit\n");

    // Assert
    assertThat(result.exitCode()).isZero();
    assertThat(result.stdout())
        .contains("/compact", "read_file", "mcp__echo__echo", MODEL, "65536", "Режим: agent");
  }

  @Test
  void printMode_ChatTurn_AnswersAndStoresSession() throws IOException {
    // Act
    Result result = run(List.of("-p", "привет"), "");

    // Assert
    assertThat(result.exitCode()).isZero();
    assertThat(result.stdout()).contains("готово");
    try (Stream<Path> sessions = Files.list(home.resolve("sessions"))) {
      assertThat(sessions).singleElement();
    }
  }

  @Test
  void printMode_AgentTurnWithBuiltinAndMcpTools_SendsToolResultsBack() {
    // Arrange
    ollama.stubFor(post("/api/chat").inScenario("agent")
        .whenScenarioStateIs(Scenario.STARTED).willSetStateTo("called")
        .willReturn(stream(String.format(CHUNK, "\"content\":\"\",\"tool_calls\":["
            + "{\"function\":{\"name\":\"read_file\",\"arguments\":{\"path\":\"Calc.java\"}}},"
            + "{\"function\":{\"name\":\"mcp__echo__echo\",\"arguments\":{\"text\":\"hi\"}}}]",
            "\"done\":false") + String.format(CHUNK, "\"content\":\"\"", DONE))));

    // Act
    Result result = run(List.of("--mode", "agent", "-p", "прочитай Calc.java"), "");

    // Assert
    assertThat(result.exitCode()).isZero();
    assertThat(result.stdout()).contains("готово");
    ollama.verify(postRequestedFor(urlEqualTo("/api/chat"))
        .withRequestBody(matchingJsonPath("$.messages[3].role", equalTo("tool")))
        .withRequestBody(matchingJsonPath("$.messages[3].content", containing("class Calc {}")))
        .withRequestBody(matchingJsonPath("$.messages[4].content", equalTo("echo: hi\n\ndone"))));
  }

  @Test
  void printMode_AgentTurnInTextJsonMode_ReadsTheToolBlock() {
    // Arrange
    ollama.stubFor(post("/api/chat").inScenario("text-json")
        .whenScenarioStateIs(Scenario.STARTED).willSetStateTo("called")
        .willReturn(stream(answer("Читаю.\\n```tool\\n{\\\"name\\\":\\\"read_file\\\","
            + "\\\"args\\\":{\\\"path\\\":\\\"Calc.java\\\"}}\\n```"))));

    // Act
    Result result = run(List.of("--mode", "agent", "-p", "прочитай Calc.java"), "",
        Map.of("HARNESS_DEFAULT_TOOL_CALL_MODE", "text-json"));

    // Assert
    assertThat(result.exitCode()).isZero();
    assertThat(result.stdout()).contains("готово").doesNotContain("```tool");
    ollama.verify(postRequestedFor(urlEqualTo("/api/chat"))
        .withRequestBody(matchingJsonPath("$.messages[3].content",
            containing("TOOL_RESULT read_file:"))));
  }

  @Test
  void repl_CompactAndResume_RewriteAndReadTheSession() {
    // Arrange
    Result first = run(List.of(), "первое\nвторое\n/compact\n/exit\n");

    // Act
    Result resumed = run(List.of("--resume", "-p", "третье"), "");

    // Assert
    assertThat(first.exitCode()).isZero();
    assertThat(resumed.exitCode()).isZero();
    assertThat(resumed.stdout()).contains("готово");
  }

  @Test
  void printMode_OllamaDown_ReportsTheErrorAndFails() {
    // Act
    Result result = run(List.of("-p", "привет"), "",
        Map.of("HARNESS_OLLAMA_BASE_URL", "http://localhost:1"));

    // Assert
    assertThat(result.exitCode()).isNotZero();
    assertThat(result.stdout() + result.stderr()).contains("✗");
  }

  private static String answer(String text) {
    return String.format(CHUNK, "\"content\":\"" + text + "\"", "\"done\":false")
        + String.format(CHUNK, "\"content\":\"\"", DONE);
  }

  private static ResponseDefinitionBuilder stream(String body) {
    return aResponse().withHeader("Content-Type", "application/x-ndjson").withBody(body);
  }

  private Result run(List<String> args, String stdin) {
    return run(args, stdin, Map.of());
  }

  private Result run(List<String> args, String stdin, Map<String, String> env) {
    List<String> command = new ArrayList<>();
    command.add(BINARY.toString());
    command.add("--cwd");
    command.add(cwd.toString());
    command.addAll(args);
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.environment().put("HARNESS_HOME", home.toString());
    builder.environment().put("HARNESS_OLLAMA_BASE_URL", ollama.baseUrl());
    builder.environment().putAll(env);
    try {
      Path out = Files.createTempFile(home, "stdout", ".txt");
      Path err = Files.createTempFile(home, "stderr", ".txt");
      builder.redirectOutput(out.toFile()).redirectError(err.toFile());
      long startedAt = System.nanoTime();
      Process process = builder.start();
      process.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
      process.getOutputStream().close();
      assertThat(process.waitFor(60, TimeUnit.SECONDS)).as("finished in time").isTrue();
      Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
      Result result = new Result(process.exitValue(),
          Files.readString(out, StandardCharsets.UTF_8),
          Files.readString(err, StandardCharsets.UTF_8), elapsed);
      assertThat(result.stdout() + result.stderr())
          .doesNotContain("MissingReflectionRegistrationError", "MissingResourceRegistration",
              "MissingJNIRegistration", "UnsupportedFeatureError", "Exception");
      return result;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private record Result(int exitCode, String stdout, String stderr, Duration elapsed) {
  }
}
