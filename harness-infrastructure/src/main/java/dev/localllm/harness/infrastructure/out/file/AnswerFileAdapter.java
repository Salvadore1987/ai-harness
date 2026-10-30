package dev.localllm.harness.infrastructure.out.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

import dev.localllm.harness.application.port.out.AnswerFilePort;

/**
 * Writes saved answers as UTF-8 files.
 */
@Component
public class AnswerFileAdapter implements AnswerFilePort {

  @Override
  public boolean isDirectory(Path path) {
    return Files.isDirectory(path);
  }

  @Override
  public void write(Path file, String text) {
    try {
      Path parent = file.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(file, text);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot write " + file + ": " + e.getMessage(), e);
    }
  }
}
