package dev.localllm.harness.infrastructure.out.mcp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MCP content lists turned into plain text.
 */
class McpTextToolCallbackTest {

  @Test
  void toText_TextItems_JoinsTexts() {
    // Act
    String text = McpTextToolCallback.toText(
        "[{\"text\":\"first\"},{\"type\":\"text\",\"text\":\"line\\nnext\"}]");

    // Assert
    assertThat(text).isEqualTo("first\n\nline\nnext");
  }

  @Test
  void toText_ImageItem_KeepsJson() {
    // Arrange
    String content = "[{\"text\":\"chart\"},{\"data\":\"iVBOR\",\"mimeType\":\"image/png\"}]";

    // Act
    String text = McpTextToolCallback.toText(content);

    // Assert
    assertThat(text).isEqualTo(content);
  }

  @Test
  void toText_TypedNonTextItemWithText_KeepsJson() {
    // Arrange
    String content = "[{\"type\":\"resource\",\"text\":\"x\"}]";

    // Act & Assert
    assertThat(McpTextToolCallback.toText(content)).isEqualTo(content);
  }

  @Test
  void toText_NotAList_KeepsContent() {
    // Act & Assert
    assertThat(McpTextToolCallback.toText("{\"a\":1}")).isEqualTo("{\"a\":1}");
    assertThat(McpTextToolCallback.toText("[]")).isEqualTo("[]");
    assertThat(McpTextToolCallback.toText("plain")).isEqualTo("plain");
  }

  @Test
  void errorText_ContentRecords_ExtractsTexts() {
    // Act
    String text = McpTextToolCallback.errorText("[TextContent[annotations=null, text=first, "
        + "meta=null], TextContent[annotations=null, text=a, b\nc, meta={}]]");

    // Assert
    assertThat(text).isEqualTo("first\n\na, b\nc");
  }

  @Test
  void errorText_OtherContent_KeepsIt() {
    // Act & Assert
    assertThat(McpTextToolCallback.errorText("[ImageContent[data=x]]"))
        .isEqualTo("[ImageContent[data=x]]");
  }
}
