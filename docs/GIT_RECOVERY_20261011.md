# Git 仓库损坏与恢复记录 — 2026-10-11

## 结论先行

`reader/.git` 于 2026-10-11 01:51 前后损坏（pack 文件丢失、分支 ref 消失）。已恢复：历史回溯到 `feature/epub-anchor`（a57c324，versionCode 32，2026-10-05），并把损坏前工作树（versionCode 56 + A1 + B1）作为单一检查点提交。当前分支 `recovery-20261011`，HEAD = `443e865`。

**v33–v56 的逐提交历史不可恢复**（其对象仅存在于损坏的 `.git`）。代码内容零丢失。

## 根因（已实验证实）

本机 git（PortableGit 1.2.0，`core.autocrlf=true`、`core.symlinks=false`、`core.fscache=true`）**写入"路径含斜杠"的 loose ref 会静默失败**：

```
git update-ref refs/heads/probe <sha>          → 成功，文件落盘，跨进程存活
git update-ref refs/heads/exp/pdf-glass-... <sha> → 返回码 0，但文件不存在，git 视而不见
```

由于本仓库所有真实分支名都带斜杠（`exp/…`、`feature/…`、`codex/…`），任何创建/更新这些分支的操作都会"成功但无效"，随后 `git gc` / 引用整理会把它们清掉——最终表现为分支 unborn、`refs/heads` 消失、`.git/objects/pack/*.pack` 被删除（gc 重打包失败后清理旧包），所有 ref 变成 invalid sha1 pointer，历史对象不可达。

验证证据：`git fsck` 报全部 ref 无效；`.git/objects/pack/` 只剩 `.idx` 与 multi-pack-index；恢复流程中 `git checkout -B exp/…` 报"Switched to a new branch"但 ref 从未落盘。

## 恢复步骤（已执行）

1. 只读取证：`git fsck`、目录清点、`git bundle list-heads`。
2. 源码保险：`backups/20261011-emergency-source.zip`（555 个源码与构建文件）。
3. 从 `backups/20261007-v51-current/reader-current-history.bundle` 克隆到 `reader-recovery/`。
4. 选基线：`origin/feature/epub-anchor`（a57c324，v32）——它是损坏提交链（`36c49a3 → b22758f → 12ff61b → a37d9ca → 11de3c1 → 07b1807 → 194dd438`）的直接祖先。
5. **用无斜杠分支名** `recovery-20261011` 建分支（`git update-ref` + `git symbolic-ref` + `git reset --hard`）。
6. 复制当前工作树（2890 文件，跳过 `.git`/构建产物/`local.properties`/日志），提交检查点 `443e865`。
7. 把恢复好的 `.git` 装回 `reader/`；损坏的原 `.git` 保留为 `reader/.git.corrupt-20261011`（取证用，已写入 `.git/info/exclude`）。
8. 立刻生成恢复后的 bundle 备份：`backups/20261011-recovered/reader-recovered-20261011.bundle`（54.2 MB，SHA-256 `ab733fa0fe6328be883430b92173d2f78ad80940949aaa653ead852ef7f6bce9`）。

## 恢复后的状态

- `git log -1` = `443e865 recovery: 重建 v56+A1 工作树（.git 损坏后的恢复检查点）`
- `app/version.properties` = `versionCode=56`
- QA JVM 全量：**1770 项 / 2 跳过 / 5 失败**，5 项失败全部是玻璃/胶囊契约测试（`GlassMaterialRegressionTest`、`CapsuleInteractionContractTest`、`ReaderChromeMaterialTest`、`ReaderChromeTokensContractTest`、`M1racleMotionContractTest`），与身份/导入层零引用交集，属 v52–v56 玻璃变体工作的既有失败，待单独修复。
- 工作树存在他方并发的未提交改动（图标 `mipmap-*/ic_launcher*.png` 与 `tools/aapt2*` 被删除），未纳入本检查点。

## 强制约定（后续所有协作者）

1. **禁止使用带斜杠的分支名**（`feature/x`、`exp/x` 等）；如需，用无斜杠名（如 `pdf-glass-20261010`）。
2. 创建分支用 `git update-ref refs/heads/<flat-name> <sha> && git symbolic-ref HEAD refs/heads/<flat-name>`，不要依赖 `git checkout -B` / `git switch -c`（在本机会静默失效）。
3. 每次重要提交后立刻 `git bundle create` 到 `D:\03_M1racle Reader\backups\<date>-<topic>\` 并生成 `.sha256`——bundle 是单文件，不受 loose ref 失效影响。
4. 怀疑 ref 异常时的自检：`git rev-parse --abbrev-ref HEAD` 是否能解析、`ls .git/refs/heads` 是否与 `git branch` 一致、`git fsck --no-progress | head`。
5. 在修复该环境问题前，避免在本机对 reader 仓库执行 `git gc` / `git prune` / `git repack`。

## 未完成 / 待决

- v33–v56 的逐提交粒度已丢失；若需重建里程碑，需按 `docs/CHANGELOG` 与各版本报告人工切分。
- `origin` 远端仍指向 `C:/Users/21234/Desktop/...` 的旧 bundle（不存在），如需推送应改指新远端。
- `reader-recovery/` 目录保留为二次保险，确认无误后可删。