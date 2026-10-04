# EPUB 第 1 步：阅读位置文本锚点（2026-10-04）

按工程师方案 `EPUB_ANCHOR_PLAN.md` 实施；分支 `feature/epub-anchor`（基于 `e7aba38`），本地提交未推送。

## 实现（implemented）

- 锚点模型 `DocumentAnchor.EpubText`（spineHref、章内 blockIndex、块内 charStart、可选 charEnd、parserVersion、约 32 字规范化 quote）；`AnchorCodec` 新增 `epub:<href>|<block>|<start>,<end 或 ->|v<n>|<quote>`，href / quote 经 URL 编码，解码失败返回 null、不抛异常。
- 恢复函数 `com.m1racle.reader.epub.EpubReadingAnchors.restore(book, saved | document)`：纯 Kotlin，只依赖解析后的 `EpubBook`，不涉及 Compose / pager / 排版。返回 `EpubRestoreResult`（`tier` + `location` / `progress` / 旧行与像素）。顺序：Exact → 本章最近 Quote → ChapterStart（降级并记日志，不落书首）→ Progress（章节不存在或为非线性项）；无锚点且有旧位置时为 LegacyPosition。
- `EpubBlock.readingRuns()/readingText()` 成为块文本单一来源：排版行与锚点共用字符偏移。分页阅读器经 `EpubRowIndex` 在扁平行与章内坐标间换算，`restoreEpubText` 只在旧位置迁移和百分比兜底时用到页面几何。
- 数据库 14 → 15：`documents.readingAnchor TEXT NULL`、`bookmarks.anchor TEXT NULL`，导出 `15.json`。保存时同一条 UPDATE 写入 readingAnchor 与 lastScrollIndex/Offset、readingProgress；恢复只读 readingAnchor。
- 旧记录一次性尽力迁移：首次打开时按当前排版用 `pageForPosition` 求页，以该页首字生成锚点并立即保存；之后不再走迁移。旧像素误差会带入，CHANGELOG 已注明。
- `BookmarkLocationKind.EPUB_TEXT` 与 `EpubTextAnchorResolver`（排序键为 spine 序号，由写入方经 `projectInBook` 提供）；本步无书签 / 高亮 UI。
- 版本 1.0.0 / 32。

## 与方案的差异

- 方案提到的“ZIP 备份带新列”：应用内没有数据库备份 / 导出（`allowBackup=false`，仓库内 ZIP 仅用于源码与 APK 的离线备份），因此无可改之处；新列随数据库本身迁移，老库升级后新列为空并走一次性迁移。
- `EpubTextAnchorResolver.project(anchor)` 无书目上下文时明确失败，必须用 `projectInBook(anchor, spineIndex)`，避免写入错误排序键。

## 自动化验证（automated_verified）

- 新增测试：`EpubTextAnchorCodecTest`（编解码、损坏输入、注册）、`EpubReadingAnchorsTest`（各恢复层级、前章多拆块、parserVersion / 越界、重复片段、章首降级、非线性与缺失章节、旧记录迁移、长段落分片 / 续行 / 列表第 N 项 / 纯图片 / 空章节）、`EpubRowIndexTest`（字体族 / 字号 / 行距 / 页边距组合下锚点、恢复与书签解析不变）、`EpubAnchorReflowHostTest`（真实测量的十种排版下落页包含锚点字符；五次改排版冷启动回到同一文字、锚点不变；旧记录打开即迁移、再次打开走锚点）、`MigrationV14ToV15Test`（真 SQLite，用 14.json 建库、迁移后与 15.json 结构逐表一致）。
- 完整 QA 单测（PC，本机工具链）：1684 项，1 失败，2 跳过（既有环境跳过）。唯一失败 `ReaderChromeTokensContractTest.every product role has a complete bounded recipe` 在 `e7aba38`（未含本步改动）上同样失败，来自此前未完成的玻璃材质改动，本步未修改。
- Release Lint：0 errors / 159 warnings / 4 hints。

## 产物

- `.external_outbox/m1racle-reader-release-latest.apk`：7,076,188 bytes，SHA-256 `bf27e92f5ceba70b4997a4d16597572652e5ee708ab07a692457c45dae07bb17`，与 `app/build/outputs/apk/release/app-release.apk` 一致；versionCode 32 / 1.0.0；apksigner v2 校验通过，证书 SHA-256 `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`（与原正式证书一致）；16 KiB 对齐检查通过；无 INTERNET。

## 待办（pending）

- 未安装到模拟器或手机，未复制到设备 Download（本轮明确不触碰模拟器）。真机三项（大字号读到中间杀进程改小字号重开、目录跳转后退出重开、老版本覆盖安装后打开已有进度的书）未做。
