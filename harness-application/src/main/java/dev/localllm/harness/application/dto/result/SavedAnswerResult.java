package dev.localllm.harness.application.dto.result;

import java.nio.file.Path;

/**
 * A saved answer.
 *
 * @param file absolute path of the written file
 * @param characters length of the saved text
 */
public record SavedAnswerResult(Path file, int characters) {
}
