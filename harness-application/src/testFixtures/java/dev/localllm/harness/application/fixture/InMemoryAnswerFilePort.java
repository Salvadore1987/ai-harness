package dev.localllm.harness.application.fixture;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import dev.localllm.harness.application.port.out.AnswerFilePort;

/**
 * Answer files kept in a map; directories exist only when declared.
 */
public final class InMemoryAnswerFilePort implements AnswerFilePort {

  private final Map<Path, String> files = new HashMap<>();
  private final Set<Path> directories = new HashSet<>();

  /**
   * Declares an existing directory.
   *
   * @param directory absolute directory path
   * @return this fake
   */
  public InMemoryAnswerFilePort withDirectory(Path directory) {
    directories.add(directory);
    return this;
  }

  @Override
  public boolean isDirectory(Path path) {
    return directories.contains(path);
  }

  @Override
  public void write(Path file, String text) {
    files.put(file, text);
  }

  /**
   * Written files by path.
   *
   * @return copy of the written files
   */
  public Map<Path, String> files() {
    return Map.copyOf(files);
  }
}
