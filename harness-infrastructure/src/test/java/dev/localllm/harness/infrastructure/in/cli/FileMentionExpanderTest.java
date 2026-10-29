package dev.localllm.harness.infrastructure.in.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class FileMentionExpanderTest {

  @TempDir
  private Path workspace;

  private final FileMentionExpander expander = new FileMentionExpander();

  @Test
  void expand_WithMentionedFile_AppendsItsContentWithRelativePath() throws IOException {
    // Arrange
    Files.createDirectories(workspace.resolve("src"));
    Files.writeString(workspace.resolve("src/App.java"), "class App {}");

    // Act
    FileMentionExpander.Expansion expansion = expander.expand("explain @src/App.java", workspace);

    // Assert
    assertThat(expansion.text()).isEqualTo(
        "explain @src/App.java\n\n<file path=\"src/App.java\">\nclass App {}\n</file>");
    assertThat(expansion.problems()).isEmpty();
  }

  @Test
  void expand_WithoutMentions_ReturnsTextUnchanged() {
    // Act
    FileMentionExpander.Expansion expansion = expander.expand("mail me at a@b.c", workspace);

    // Assert
    assertThat(expansion.text()).isEqualTo("mail me at a@b.c");
    assertThat(expansion.problems()).isEmpty();
  }

  @Test
  void expand_WithPathOutsideWorkspace_ReportsAndSkipsIt() {
    // Act
    FileMentionExpander.Expansion expansion = expander.expand("read @../secret.txt", workspace);

    // Assert
    assertThat(expansion.text()).isEqualTo("read @../secret.txt");
    assertThat(expansion.problems()).singleElement().asString()
        .contains("вне рабочей директории");
  }

  @Test
  void expand_WithMissingAndTooLargeFiles_ReportsBoth() throws IOException {
    // Arrange
    Files.write(workspace.resolve("big.log"), new byte[FileMentionExpander.MAX_FILE_BYTES + 1]);

    // Act
    FileMentionExpander.Expansion expansion = expander.expand("@missing.txt @big.log", workspace);

    // Assert
    assertThat(expansion.problems()).containsExactly(
        "@missing.txt: файл не найден", "@big.log: файл больше 256 КБ");
  }

  @Test
  void expand_WithBinaryFile_ReportsIt() throws IOException {
    // Arrange
    Files.write(workspace.resolve("image.bin"), new byte[] {(byte) 0xC3, (byte) 0x28});

    // Act
    FileMentionExpander.Expansion expansion = expander.expand("@image.bin", workspace);

    // Assert
    assertThat(expansion.problems()).singleElement().asString().contains("не текстовый");
  }
}
