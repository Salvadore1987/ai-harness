package dev.localllm.harness.infrastructure.out.tools;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextDiffTest {

  @Test
  void unified_ChangeInLongFile_KeepsThreeLinesOfContext() {
    // Arrange
    String before = lines(1, 20);
    String after = before.replace("line 10\n", "line ten\nline 10.5\n");

    // Act
    String diff = TextDiff.unified(before, after);

    // Assert
    assertThat(diff).isEqualTo("""
        @@ -7,7 +7,8 @@
         line 7
         line 8
         line 9
        -line 10
        +line ten
        +line 10.5
         line 11
         line 12
         line 13
        """);
  }

  @Test
  void unified_DeletedFirstLine_StartsAtLineOne() {
    // Act
    String diff = TextDiff.unified("a\nb\nc\n", "b\nc\n");

    // Assert
    assertThat(diff).isEqualTo("@@ -1,3 +1,2 @@\n-a\n b\n c\n");
  }

  @Test
  void unified_ChangeWithinLine_ShowsWholeLine() {
    // Act
    String diff = TextDiff.unified("x = 1;\n", "x = 2;\n");

    // Assert
    assertThat(diff).isEqualTo("@@ -1,1 +1,1 @@\n-x = 1;\n+x = 2;\n");
  }

  @Test
  void unified_EqualTexts_IsEmpty() {
    // Act & Assert
    assertThat(TextDiff.unified("same\n", "same\n")).isEmpty();
  }

  private static String lines(int from, int to) {
    return IntStream.rangeClosed(from, to).mapToObj(i -> "line " + i + "\n")
        .collect(Collectors.joining());
  }
}
