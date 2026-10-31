package dev.remotegpt.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.remotegpt.app.ui.TimelineCard
import dev.remotegpt.app.ui.WorkbenchTheme
import dev.remotegpt.client.TimelineItem

/** Offline examples for inspecting wrapping, scrolling and rich text on phones. */
@Preview(widthDp = 360, heightDp = 800, showBackground = true)
@Composable internal fun MarkdownGallery() {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TimelineCard(TimelineItem("markdown", "preview", "agentMessage", SAMPLE, true), {})
    }
}

private val SAMPLE = """
## Markdown · 阅读与表格
**强调文字**、*斜体*、~~删除线~~ 和 `inline_code`。

| 项目 | 说明 | 状态 |
| :--- | :--- | ---: |
| 表格换行 | 中文长句应该在单元格中完整换行，不截成一行，也不隐藏结尾。 | 已完成 |
| Long English | Long explanations should wrap completely, with every sentence available to read. | Ready |
| 样式 | **Bold** and `code` with [a link](https://example.com). | OK |

> 引用内容应当清晰，并保留换行后的阅读顺序。

1. 第一层列表
   - 第二层的长文字内容要正确缩进，不能挤占过多横向空间。
2. 第二项

```python
# Preserve the original indentation when copying.
def hello():
    if True:
        print("hello 中文")
```

[Reference link][docs]

[docs]: https://example.com
""".trimIndent()
