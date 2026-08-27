# 真机安装与 ADB 测试协议（Agent 必读）

**Audience:** 任意后续 Agent / 人工验收
**目的:** 打新包后如何判断能否自动安装与测试；连不上 ADB 时如何交付给人手动装。
**更新:** 2026-07-20

---

## 0. 一句话规则

1. **先判断 ADB 是否在线**（`adb devices` 有 `device`）。
2. **在线** → 自动 `install -r` + 冷启动冒烟。
3. **离线** → **不要假装已测**；把 APK 拷到手机 `Download/`，写明路径，请人手动安装。
4. 日常交付优先 **release（minify，~10–11MB）**，不要默认塞 70MB+ 的 QA/debug。

---

## 1. 判断有没有连上 ADB（无线调试 / USB）

### 1.1 标准检测

```bash
adb start-server
adb devices -l
```

| 输出 | 含义 | Agent 动作 |
|------|------|------------|
| 有一行 `… device` | **已连接** | 可 install + 自动化点测 |
| 仅 `unauthorized` | 未授权调试 | 提示人在手机点「允许 USB/无线调试」 |
| 空列表 / 只有 offline | **未连接** | 走手动安装路径，**禁止**声称 device test 通过 |
| `adb: not found` | 环境无 adb | 同上 + 说明缺工具 |

**判定代码语义（伪代码）：**

```text
ONLINE = (adb devices | awk 'NR>1 && $2=="device"') 非空
if ONLINE → auto install & smoke
else → manual delivery only
```

### 1.2 无线调试（Wi‑Fi）

历史会话曾用：`198.18.0.1:<port>`（端口会变，如 44441 / 38861 / 37173，**不要写死**）。

```bash
# 人从「开发者选项 → 无线调试」提供 IP:端口 后：
adb connect <ip>:<port>
adb devices -l   # 再次确认 state=device
```

若 `Connection refused` / 超时：视为 **未连接**，走手动安装。

### 1.3 一键脚本（已提供）

```bash
# 有 device 时安装 outbox 最新 release 并尝试启动
bash scripts/install-release-outbox.sh

# 可选：指定 APK
bash scripts/install-release-outbox.sh /path/to/app.apk
```

脚本行为：

- 无 adb / 无 device → **exit 非 0**，打印如何 `adb connect`
- 有 device → `adb install -r` + 启动 `com.m1racle.reader`

---

## 2. APK 从哪来（打完包去哪）

### 2.1 构建

| 变体 | Gradle | 包名 | 典型体积 | 用途 |
|------|--------|------|----------|------|
| **release** | `:app:assembleRelease` | `com.m1racle.reader` | **~10–11MB**（R8 on） | **交付 / 人工验收默认** |
| qa | `:app:assembleQa` | `com.m1racle.reader.qa` | ~70MB | 隔离测试沙箱 |
| debug | `:app:assembleDebug` | `com.m1racle.reader` | ~70MB | 调试 |
| benchmark | `:app:assembleBenchmark` | `com.m1racle.reader.benchmark` | 较大 | Macrobenchmark |

**Release 已开启** `isMinifyEnabled` + `isShrinkResources`（见 `app/build.gradle.kts`）。
**禁止** 对 APK 使用 `jarsigner` 二次签名（破坏 16KB page-align 的 native 库）。

### 2.2 自动导出到发件箱（outbox）

`assembleDebug|Qa|Release|Benchmark` **成功后** Gradle 会自动拷贝到：

```text
.external_outbox/
  m1racle-reader-<variant>-v<version>-yyyyMMdd-HHmmss.apk
  m1racle-reader-<variant>-latest.apk
  *.sha256
```

仅导出、不重编：

```bash
./gradlew :app:exportApksToOutbox
```

- `.external_outbox/` **已 gitignore**
- 菜单 **发件箱** 也会扫该目录
- **不要**把整仓/7z 大备份丢进手机公共 inbox（见清理约定）

### 2.3 推荐交付路径（ADB 离线时）

| 步骤 | 动作 |
|------|------|
| 1 | 确认有 ` .external_outbox/m1racle-reader-release-latest.apk`（或先 `assembleRelease`） |
| 2 | 复制到手机：`/storage/emulated/0/Download/m1racle-reader-release-latest.apk`（或历史名 `m1racle-reader-release.apk`） |
| 3 | 在报告里写：**ADB=offline；已放 Download；请手动安装** |
| 4 | **不要**把「未装机」写成 PASS |

```bash
# 示例：ADB 离线时的交付
cp -f .external_outbox/m1racle-reader-release-latest.apk \
  /storage/emulated/0/Download/m1racle-reader-release-latest.apk
```

---

## 3. 连上 ADB 之后怎么测（完整剧本）

> **你现在在外面连不了也没关系。** 回家/有 Wi‑Fi 调试后，Agent 或人按本节从 §3.1 顺序执行即可。

### 3.1 接上设备（无线优先）

手机：

1. 开发者选项 → **无线调试** 打开
2. 记下 **IP:端口**（或先配对码配对，再 connect）

电脑 / proot：

```bash
adb kill-server
adb start-server
adb connect <ip>:<port>     # 例：adb connect 192.168.1.8:37891
adb devices -l              # 必须出现 xxxxx  device
```

失败常见原因：不在同一网段、端口过期、未点允许调试、VPN。
**未出现 `device` 就停，不要装包。**

### 3.2 确认包在 outbox（没有就先编）

```bash
cd /root/code/m1racle-reader   # 或本机仓库根
ls -lh .external_outbox/m1racle-reader-release-latest.apk

# 没有或要最新代码：
./gradlew :app:assembleRelease
# 成功后会自动进 .external_outbox/
```

### 3.3 一键安装 + 启动（推荐）

```bash
bash scripts/install-release-outbox.sh
```

等价手写：

```bash
DEV=$(adb devices | awk 'NR>1 && $2=="device"{print $1; exit}')
APK=.external_outbox/m1racle-reader-release-latest.apk

adb -s "$DEV" install -r "$APK"
adb -s "$DEV" shell am force-stop com.m1racle.reader
adb -s "$DEV" shell am start -n com.m1racle.reader/.MainActivity
```

- 包名 **release**：`com.m1racle.reader`
- 若装的是 QA：`com.m1racle.reader.qa`（日常交付不要用 QA）

### 3.4 自动冒烟（装后 1～2 分钟）

```bash
DEV=$(adb devices | awk 'NR>1 && $2=="device"{print $1; exit}')

# 焦点是否在 M1racle Reader
adb -s "$DEV" shell dumpsys window | rg -i 'mCurrentFocus|mFocusedApp' | head -5

# 崩溃缓冲（应无 M1racle Reader FATAL）
adb -s "$DEV" logcat -d -b crash 2>/dev/null | rg -i 'm1racle-reader|AndroidRuntime' | tail -30

# 可选：清一次再冷启
adb -s "$DEV" shell am force-stop com.m1racle.reader
adb -s "$DEV" shell am start -n com.m1racle.reader/.MainActivity
sleep 3
adb -s "$DEV" shell dumpsys window | rg -i 'mCurrentFocus' | head -3
```

**自动能判 PASS 的：** 安装成功、Activity 起来、crash 缓冲干净。
**自动不能替人的：** 滚动手感、目录对不对、导出 ZIP 内容——见下节人工点。

### 3.5 人工点测清单（release 最小）

| # | 操作 | 期望 |
|---|------|------|
| 1 | 看首页 | 最近文件列表出来，不白屏 |
| 2 | 打开一本 **Markdown** | 能滚、返回回首页 |
| 3 | 打开一本 **PDF** | 出页、能滑/翻 |
| 4 | PDF 目录 | 有内置书签则提示导入或可「扫描 PDF 内置目录」；失败有 Snackbar 文案 |
| 5 | （可选）章节导出 | 能走到导出流程不崩 |
| 6 | 后台再进 | 进程还在或冷启正常 |

### 3.6 测完怎么写报告

```markdown
### Device install
- ADB: online
- Endpoint: <serial 或 ip:port>
- APK: .external_outbox/m1racle-reader-release-latest.apk
- SHA-256: <sha256sum 输出>
- Install: adb install -r OK
- Smoke auto: focus=MainActivity, crash=empty
- Smoke human: 1–4 PASS/FAIL
- Notes: ...
```

有专项（120Hz / PDF 手感）再跟对应 `docs/R*_*.md`，**不要**用本节冒烟代替正式 gate。

---

## 4. QA 包 vs Release（别装错）

| | Release | QA |
|--|---------|-----|
| applicationId | `com.m1racle.reader` | `com.m1racle.reader.qa` |
| 用户数据 | 生产/日常数据 | **隔离沙箱**（仪器测试用） |
| connected 测试 | **禁止**对 release 跑 connected 清数据 | 仅 `connectedQaAndroidTest` |

详见：`docs/ANDROID_TEST_DATA_SAFETY.md`。

---

## 5. Agent 报告模板（装机相关）

```markdown
### Device install
- ADB: online | offline
- Endpoint: <serial 或 ip:port 或 n/a>
- APK: <path> SHA-256: <hash>
- Install: adb install -r | manual Download | skipped
- Smoke: PASS | FAIL | NOT_RUN (reason)
- Notes: ...
```

**offline 时 `Smoke` 必须为 `NOT_RUN`，不得写 PASS。**

---

## 6. 相关路径速查

| 用途 | 路径 |
|------|------|
| 发件箱（自动导出） | `.external_outbox/` |
| 安装脚本 | `scripts/install-release-outbox.sh` |
| Release 构建输出 | `app/build/outputs/apk/release/app-release.apk` |
| 手机手动安装目录 | `/storage/emulated/0/Download/` |
| 收件箱（输入，非 APK） | `/storage/emulated/0/inbox/M1racle Reader/` |
| 交付规则（摘要） | `docs/APK_DELIVERY.md`（与 `dist/APK_DELIVERY.md` 同步意图） |
| 数据安全 | `docs/ANDROID_TEST_DATA_SAFETY.md` |

---

## 7. 反例（禁止）

- 未 `adb devices` 就写「真机已验证」
- 默认安装 70MB QA 当日常交付
- 把 `M1racle Reader.7z` / 整仓备份放进公共 `inbox`
- `jarsigner` 重签 release APK
- 对 `com.m1racle.reader` 跑 connected 测试清用户库
