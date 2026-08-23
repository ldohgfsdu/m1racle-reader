# 墨阅 MarkLeaf — MVP 验收清单（Release Gate）

> **历史记录（2026-07-10，Markdown MVP 时期验收）**：仅证明当时基线；当前功能范围与验证状态见 `.memory/CURRENT_STATE.md` 与 docs/ 下各专项报告。

- **版本：** v0.1.0（`versionCode=1`，`versionName=0.1.0`）  
- **验收日：** 2026-07-10  
- **结论：** **通过** — 可称为 **v0.1.0 MVP** 发布候选（debug APK）  

## 构建与安全

| 项 | 结果 |
|----|------|
| `./gradlew :app:assembleDebug` | PASS |
| APK | `app/build/outputs/apk/debug/app-debug.apk` |
| `INTERNET` 权限 | 无（仅注释说明） |
| WebView | 无 |
| 广告 / 统计 / 账号 / 同步 SDK | 无 |
| 网络请求 | 无（图片 http(s) 显式禁用） |

## 主流程

| # | 项 | 结果 | 备注 |
|---|-----|------|------|
| 1 | 首次空状态 + FAB | PASS | 多轮实机验收 |
| 2 | SAF 导入 md/txt + 非法格式提示 | PASS | |
| 3 | 最近阅读 / 进度 / 删历史不删文件 | PASS | Room only |
| 4 | 阅读页滚动 / 大文档 | PASS | 200KB 预览上限仍为已知限制 |
| 5 | 基础 Markdown + Inline | PASS | |
| 6 | 代码块高亮 / 复制 / 横滑 | PASS | Step 6C–7 |
| 7 | 表格 pipe / 对齐 / 横滑 | PASS | Step 8 |
| 8 | 图片 asset 真显示 + 安全降级 | PASS | Step 9A / 9A.1 实机 |
| 9 | TOC 跳转 | PASS | |
| 10 | 当前文档搜索 + 返回先关搜索 | PASS | BackHandler |
| 11 | 阅读位置保存恢复 | PASS | |
| 12 | 阅读显示设置 + DataStore | PASS | Step 6B |
| 13 | 深浅色 | PASS | |
| 14 | 隐私 | PASS | |

## 已知限制（非阻塞）

1. 相对路径图片（`images/a.png`）暂不支持  
2. 网络图片禁用（有意为之）  
3. 文档过长时有 200KB 预览截断  
4. 段落内 inline 图片不渲染为图（仅独立行 Image block）  
5. 空 source 图片语法 `![]()` 降级为普通段落文本  
6. 无正式商店签名 / 未做完整自动化 UI 测试  

## 关键样例

- `docs/sample-large.md`  
- 内置图：`asset://sample-image.png`（`app/src/main/assets/sample-image.png`）  

## 判定

在「本地导入 + 阅读 + 搜索 + 目录 + 进度 + 显示设置 + 代码/表格/安全图片」路径上已达到 **可日常使用** 的 MVP 标准。  
建议版本号：**v0.1.0**（若强调内测可写 **v0.1.0-alpha**，与 `versionName` 一致即可）。
