# 墨阅 MarkLeaf

**一句话：** 面向 Android 的本地优先、无账号、无广告、无联网依赖的 Markdown 阅读器。

当前版本：**v0.1.0 MVP**（`versionName=0.1.0`）

## 隐私与安全

- **不申请 `INTERNET` 权限**，不发起网络请求  
- **无账号、无广告、无统计 SDK、无云同步**  
- 文件仅在你主动通过系统选择器授权后可读  
- 删除「最近阅读」只删 App 内 Room 记录，**不删除**手机上的真实文件  

## MVP 已支持

| 能力 | 说明 |
|------|------|
| 本地导入 | SAF 选择 `.md` / `.markdown` / `.txt`，持久读权限 |
| 最近阅读 | 文件名、时间、大小、进度；可删历史 |
| 基础 Markdown | 标题 H1–H6、段落、引用、列表、分割线 |
| Inline 样式 | 加粗、斜体、行内代码、链接文本（不联网预览） |
| 代码块 | 语言标签、复制、横向滚动、轻量语法高亮 |
| 表格 | 标准 pipe table、对齐、横向滚动 |
| 图片 | `asset://` 内置样例；`content://` / `file://` 在有权限时可读；网络/相对路径安全占位 |
| TOC | H1–H3 目录抽屉、点击跳转、当前标题高亮 |
| 文档内搜索 | 当前文档关键词、上一个/下一个、block 高亮 |
| 阅读位置 | 保存/恢复滚动位置与进度 |
| 阅读显示 | 字号 / 行高 / 边距（DataStore 持久化） |
| 主题 | 跟随系统深浅色，Material 3 Dynamic Color |

## 明确不支持（当前版本）

- Markdown **编辑器**  
- **全文索引** / 跨文档搜索  
- **云同步**（WebDAV / GitHub 等）  
- **账号** / 登录 / 社区  
- **网络图片**加载（`http`/`https` 显示「网络图片已禁用」）  
- **相对路径图片**完整解析（如 `images/a.png` 暂不支持）  
- 图片点击放大、文件夹批量导入  
- 完整编译器级语法高亮、复杂 Excel 表格  
- 行内图片、链接点击打开浏览器（可后续迭代）  

## 安装方式

### 已构建的 Debug APK

```text
app/build/outputs/apk/debug/app-debug.apk
```

本机调试时也可能复制到：

```text
/storage/emulated/0/Download/markleaf-mvp-debug.apk
```

用文件管理器打开 APK 安装即可（需允许未知来源）。

### 从源码构建

```bash
export ANDROID_HOME=/path/to/Android/Sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew :app:assembleDebug
# 或
./gradlew :app:installDebug
```

- **最低系统：** Android 12（API 31）  
- **包名：** `com.markleaf.app`  
- Android Studio：打开仓库根目录 → Run `app`  

### arm64 本机构建说明

若 aarch64 主机上 AGP 自带 aapt2 不可用，可用仓库内 `tools/aapt2`。  
`gradle.properties` 中 `android.aapt2FromMavenOverride` 需为本机绝对路径；x86_64 / 常规 Android Studio 可删除该行。

## 如何导入 Markdown

1. 打开 App → 点右下角 **导入**（FAB）  
2. 系统文件选择器中选 `.md` / `.markdown` / `.txt`  
3. 导入成功后进入阅读页；返回主页可见「最近阅读」  

也可先把测试文档拷到手机 Download 目录再选择。

## 测试样例

| 文件 | 说明 |
|------|------|
| [docs/sample-large.md](docs/sample-large.md) | 综合验收样例（标题/列表/代码/表格/图片/搜索） |
| [docs/PRD.md](docs/PRD.md) | 产品需求文档 |
| [docs/ACCEPTANCE.md](docs/ACCEPTANCE.md) | MVP 验收清单记录 |

样例 **不会** 作为默认文档自动导入；请通过「导入文件」选择。  
图片验证请使用样例中的：

```markdown
![内置本地图片](asset://sample-image.png "本地加载验证")
```

（内置资源，无需存储权限。）

## 技术栈

- Kotlin · Jetpack Compose · Material 3  
- Navigation Compose · Room · DataStore  
- Coroutines / Flow · SAF  
- 自研 Markdown 块解析 / Inline / 轻量代码高亮 / 表格 / 本地图加载  

## 文档

- [产品 PRD](docs/PRD.md)  
- [验收清单 ACCEPTANCE](docs/ACCEPTANCE.md)  
- [测试样例](docs/sample-large.md)  

## 版本说明

| 标记 | 含义 |
|------|------|
| **v0.1.0 MVP** | 功能完整、可日常本地阅读；部分能力（相对路径图、编辑、同步）明确不做 |
| debug APK | 未做正式签名商店发布；适合自测与内部分发 |

欢迎在本地使用与反馈；后续迭代以隐私与阅读体验为先。
