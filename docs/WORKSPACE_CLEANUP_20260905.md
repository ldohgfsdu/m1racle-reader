# 工作区整理记录

日期：2026-09-05。用户目标：修复状态不一致并清理工作区。

## 处置

主目录从旧实验分支切换为基于 `691cb65` 的整理分支；未合并旧实验产品代码。修正 `.memory` 和 README 状态入口，将旧 release checklist 明确标记为历史快照。最新构建与两份集成报告纳入版本管理。

五个有未提交改动的工作树均先逐文件 SHA-256 验证外部归档，再创建具名 stash 和固定 Git archive ref，随后清理工作树。其余五个工作树原本干净。没有删除分支、tag、用户改动、迁移证据、签名材料或发布资产。归档不属于待合并的产品实现。

外部归档：`/root/code/MarkLeaf-workspace-archive-20260905/manifest.json`，记录原 HEAD、状态、文件清单、压缩包 hash 和恢复 ref；包含历史测试数据，保持本地，不推送。

| 原工作树 | 本地恢复 ref |
|---|---|
| `/root/code/MarkLeaf` | `refs/archives/workspace-20260905/MarkLeaf` |
| `/root/code/MarkLeaf-101-canonical` | `refs/archives/workspace-20260905/MarkLeaf-101-canonical` |
| `/root/code/MarkLeaf-r55-integration` | `refs/archives/workspace-20260905/MarkLeaf-r55-integration` |
| `/root/code/MarkLeaf-r60` | `refs/archives/workspace-20260905/MarkLeaf-r60` |
| `/root/code/MarkLeaf-r62` | `refs/archives/workspace-20260905/MarkLeaf-r62` |

恢复时在独立工作树 checkout manifest 中的原 HEAD，然后运行 `git stash apply <恢复 ref>`；不要在当前候选上直接应用旧实验改动。固定 refs 保证普通 stash 清理不会让归档失去 Git 引用。

## 验证

- 清理前所有归档逐文件 hash 与原件一致；每次 stash 后检查该工作树为空。
- 当前 `app/`、Gradle 配置与 `691cb65` 无差异；本次不需要重新执行产品测试或构建。
- 最新候选 outbox 与 Download 的 SHA-256 一致；主目录旧 latest 另存外部归档后同步最新候选，未重新签名。
- GitHub API：只有 v1.0.0 Draft，未发布；远端与本地 tag 指向 a43633a，无 v1.0.1 tag。
- 本次提交完成后检查全部十个工作树，要求 tracked/untracked 变更为零。忽略的 SDK 配置、构建缓存、APK 与签名材料不属于 Git 清理范围。

本轮没有安装设备、发布或修复产品行为。最新候选真机验收及 lint 异常仍按 CURRENT_STATUS 标记 pending。
