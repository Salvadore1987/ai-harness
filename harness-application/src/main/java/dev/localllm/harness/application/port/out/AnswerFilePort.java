package dev.localllm.harness.application.port.out;

import java.nio.file.Path;

/**
 * Writes saved answers to files.
 */
public interface AnswerFilePort {

  /**
   * Tells whether a path is an existing directory.
   *
   * @param path absolute path
   * @return whether the path is a directory
   */
  boolean isDirectory(Path path);

  /**
   * Writes a UTF-8 text file, creating missing parent directories and replacing the contents.
   *
   * @param file absolute file path
   * @param text file contents
   */
  void write(Path file, String text);
}
