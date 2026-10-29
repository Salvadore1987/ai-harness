package dev.localllm.harness.infrastructure.out.tools;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import dev.localllm.harness.infrastructure.config.HarnessProperties;

/**
 * Shell tool {@code run_command}: runs a command with {@code <shell> -c} in the working
 * directory and returns its exit code, stdout and stderr.
 *
 * <p>stdin is empty, so a command waiting for input fails instead of hanging. On timeout the
 * whole process tree is killed and the output collected so far is returned. Each stream keeps
 * at most {@link #MAX_STREAM_BYTES}; the rest is read and dropped so the process never blocks
 * on a full pipe.
 */
@Component
public class ShellTools {

  static final int MAX_STREAM_BYTES = 1024 * 1024;

  private static final Logger log = LoggerFactory.getLogger(ShellTools.class);
  private static final File NULL_DEVICE = new File("/dev/null");
  private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(2);

  private final String shell;
  private final Duration defaultTimeout;

  /**
   * Creates the tool.
   *
   * @param properties harness settings with the shell and the default timeout
   */
  public ShellTools(HarnessProperties properties) {
    this.shell = properties.tools().shell();
    this.defaultTimeout = properties.tools().commandTimeout();
  }

  /**
   * Runs a shell command.
   *
   * @param command command line
   * @param timeoutSec timeout in seconds, the configured default if empty
   * @param context tool context with the working directory
   * @return exit code, stdout and stderr, or the timeout and the output so far
   */
  @Tool(name = "run_command", resultConverter = PlainTextResultConverter.class, description = """
      Run a shell command with zsh -c in the working directory and return its exit code, \
      stdout and stderr. stdin is empty: do not run interactive commands. \
      The default timeout is 120 seconds.""")
  public String runCommand(
      @ToolParam(description = "Shell command line") String command,
      @ToolParam(required = false, description = "Timeout in seconds, default 120")
      @Nullable Integer timeoutSec,
      ToolContext context) {
    Duration timeout = timeoutSec == null || timeoutSec <= 0
        ? defaultTimeout : Duration.ofSeconds(timeoutSec);
    Process process = start(command, ToolContexts.workingDirectory(context));
    StreamCollector stdout = new StreamCollector(process.getInputStream());
    StreamCollector stderr = new StreamCollector(process.getErrorStream());
    boolean finished;
    try {
      finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      killTree(process);
      throw new IllegalStateException("Interrupted while running the command", e);
    }
    if (!finished) {
      killTree(process);
    }
    StringBuilder result = new StringBuilder();
    if (finished) {
      result.append("exit code: ").append(process.exitValue()).append('\n');
    } else {
      result.append("timeout after ").append(timeout.toSeconds()).append("s; the process was ")
          .append("killed\n");
    }
    section(result, "stdout", stdout.text());
    section(result, "stderr", stderr.text());
    return result.toString();
  }

  private Process start(String command, Path workingDirectory) {
    try {
      return new ProcessBuilder(shell, "-c", command)
          .directory(workingDirectory.toFile())
          .redirectInput(NULL_DEVICE)
          .start();
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot start " + shell + ": " + e.getMessage(), e);
    }
  }

  private static void killTree(Process process) {
    List<ProcessHandle> descendants = process.descendants().toList();
    process.destroyForcibly();
    descendants.forEach(ProcessHandle::destroyForcibly);
  }

  private static void section(StringBuilder result, String name, String text) {
    if (!text.isEmpty()) {
      result.append(name).append(":\n").append(text);
      if (!text.endsWith("\n")) {
        result.append('\n');
      }
    }
  }

  /**
   * Reads one output stream of the process on a virtual thread.
   */
  private static final class StreamCollector {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final Thread reader;
    private boolean dropped;

    private StreamCollector(InputStream stream) {
      this.reader = Thread.ofVirtual().start(() -> read(stream));
    }

    private void read(InputStream stream) {
      byte[] chunk = new byte[8192];
      try (stream) {
        for (int count = stream.read(chunk); count >= 0; count = stream.read(chunk)) {
          append(chunk, count);
        }
      } catch (IOException e) {
        // the stream is closed when the process is killed; keep what was read
        log.debug("Output stream of a command closed: {}", e.getMessage());
      }
    }

    private synchronized void append(byte[] chunk, int count) {
      int room = MAX_STREAM_BYTES - buffer.size();
      if (count > room) {
        dropped = true;
      }
      buffer.write(chunk, 0, Math.max(0, Math.min(room, count)));
    }

    private String text() {
      try {
        reader.join(DRAIN_TIMEOUT);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      synchronized (this) {
        String text = buffer.toString(StandardCharsets.UTF_8);
        return dropped ? text + "\n[output over 1 MB dropped]\n" : text;
      }
    }
  }
}
