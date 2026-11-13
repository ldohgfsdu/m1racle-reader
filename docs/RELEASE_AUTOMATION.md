# Reader 自动发版流程

## 触发方式

正式发布仅允许 GitHub `master`，满足以下任一条件：

- master 提交信息包含 `[publish]`。
- Actions → **Reader signed release** → Run workflow，选择 master 并设置 `publish=true`。

普通提交和 PR 不会发布 APK。发布条件成立后，仍须通过版本、单测、生产 Lint、生产签名、原生库对齐、远端文件回读校验。**无需为了修改文档或 CI 而升级版本。**

## 首个正式公开版本

当前 GitHub 的 `v1.0.0` 是历史 Draft，附件与现有 `1.0.0 / versionCode 97` 候选并非同一交付，不得公开旧草稿或覆盖标签。

下一次正式发行需要先在 `app/version.properties` 明确递增 `versionCode>97` 与 `versionName>1.0.0`，提交 `docs/releases/v<versionName>.md`，依次填写：

```markdown
## 新增
## 修复
## 验证
## 已知限制
```

文案必须反映真实变更和实测结论。不能以模板替代验收。新版本可用例如 1.0.1/98，但这并非自动安排发版。

## 签名设置（用户在 GitHub Secrets 中自行授权）

将现有 **Reader 正式生产签名** 安全配置为 Actions Repository Secrets：

| Secret | 内容 |
|---|---|
| `M1RACLE_RELEASE_KEYSTORE_BASE64` | 原始生产 keystore 的 Base64 |
| `M1RACLE_RELEASE_STORE_PASSWORD` | keystore 密码 |
| `M1RACLE_RELEASE_KEY_ALIAS` | 密钥别名 |
| `M1RACLE_RELEASE_KEY_PASSWORD` | 密钥密码 |

不得将密钥或 Base64 发送到聊天、加入仓库、写入 PR 或日志。GitHub 变量缺失时工作流将阻止发行，**没有 Debug/QA/unsigned 回退**。

发布脚本固定校验现有生产证书 SHA-256 `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`（历史签名记录），并在已有正式 GitHub Release 时再次校验签名连续性及版本号递增。签名材料只保存在 runner 临时目录，退出时删除。

## 不可跳过的验收

1. `tools/release_gate.py` 通过版本身份和发布说明负向测试。
2. Android Platform 37.0 / Build Tools 36.0.0 下，全量 QA / Release 单测与 `lintRelease` 通过。
3. 正式 `:app:assembleRelease` 成功，签名、Manifest、无 INTERNET、ZIP 及 ELF 16 KiB 对齐检查通过。
4. 产物与 Gradle Outbox 一致；先创建 Draft，再下载远端 APK 与 SHA-256，逐字节比较并验证签名；最后才公开正式 Release。
5. 若发生错误，停止、保留 Draft 和日志，不把失败称为发版成功。
6. 仪器测试与物理手机验收独立记录，CI 通过不等于真机流畅性通过。

历史 Draft `v1.0.0` 保留不修改，等待新的版本身份和签名配置验收。参见 `RELEASE_CHECKLIST.md`、`docs/APK_DELIVERY.md`。
