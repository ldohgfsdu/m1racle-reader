# 墨阅 MarkLeaf 测试样例

这是一份用于本地验收的 Markdown 样例文档，覆盖标题、段落、引用、列表、分割线、搜索与代码高亮。

请在手机文件管理器中把本文件导入墨阅，验证导入、分块渲染、目录跳转、搜索与阅读位置恢复。

## 基础段落与强调

这里是一段普通中文段落。墨阅的目标是在 Android 上提供**本地优先**、*轻量*、排版舒适的 Markdown 阅读体验，不依赖账号和网络。

下面这一段稍长一些，用来检查中文换行与行高：本地阅读意味着文件始终停留在你的设备上，App 只通过系统文件选择器获得你主动授权的 URI，并在需要时申请持久读取权限；删除最近阅读记录时，只会移除数据库中的历史条目，不会删除磁盘上的真实文件。

## 引用块

> 本地优先、启动快、阅读舒服、排版漂亮、无账号、无广告、无联网依赖。
>
> —— 墨阅 MarkLeaf PRD

## 无序列表

- 导入 `.md` / `.markdown` / `.txt`
- 最近阅读列表
- 基础 Markdown 分块渲染
- TOC 目录跳转
- 阅读位置保存与恢复
- 当前文档搜索
- 代码语法高亮

## 有序列表

1. 打开 App 进入主页
2. 点击右下角 FAB 选择文件
3. 在最近阅读中点开文档
4. 使用目录快速跳转章节
5. 退出后再打开应恢复阅读位置

## 代码高亮样例

### Kotlin

```kotlin
fun main() {
    val message = "Hello MarkLeaf"
    // 超长单行（横向滚动）：
    val longLine = "abcdefghijklmnopqrstuvwxyz0123456789_ABCDEFGHIJKLMNOPQRSTUVWXYZ_this_is_a_very_long_code_line_that_should_not_break_the_page_layout_and_must_scroll_horizontally_inside_the_code_block_container"
    println(message)
    return
}
```

### Python

```python
def hello(name: str) -> None:
    # 中文注释
    print(f"Hello, {name}")
    for i in range(3):
        if i > 0:
            continue
    return None
```

### JavaScript

```javascript
const answer = 42;
function greet(name) {
  // comment
  return `hello ${name}`;
}
console.log(greet("MarkLeaf"));
```

### JSON

```json
{
  "name": "MarkLeaf",
  "version": 1,
  "localOnly": true,
  "tags": ["markdown", "android"]
}
```

### XML

```xml
<!-- sample -->
<reader name="MarkLeaf" local="true">
  <feature id="search">document search</feature>
</reader>
```

### Bash / Shell

```bash
#!/usr/bin/env bash
# install debug apk
echo "Hello MarkLeaf"
if [ -f app-debug.apk ]; then
  echo "found apk"
fi
```

### Markdown（嵌套展示）

```markdown
# Heading
- list item
**bold** text
```

### 未知语言

```foobar
this language is unknown
should render as plain text
```

### 无语言标记

```
plain text code block
line 2
line 3
```

### 空代码块

```
```

---

## 分割线上方的内容

上面应出现一条细分割线。

### 边界情况

#  

## 空标题正文也允许

### 结尾

验收通过标准：能打开、能滚动、目录可用、搜索可用、代码高亮可读、返回主页进度有更新。
