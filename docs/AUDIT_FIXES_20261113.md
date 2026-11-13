# 2026-11-13 审查修复与 Release 96

范围为用户提供的 A1、A2、B1–B23 清单。用户已授权全部修复与正式 Release 打包，并指定“站起来蹬”子代理；执行路由为 `group/group`。在 `chore/deps-prune-20261108`、HEAD `e2c8cb4` 的现有工作树上保留 95 版未提交改动继续修复，旧工作树保留。

## 逐项处理

| 项目 | 实现与验证入口 |
|---|---|
| A1 | Reading 快回归将迁移过滤器单独交给 `:core:database:testReleaseUnitTest`；其余五项仍在 app。核对实际 XML suite。 |
| A2 | 活动对象不再保留漂移的 `qa_1459`，最终测试数量取本轮 XML 汇总。 |
| B1 | 快照独立递增序号、活动 token 和 WebView 实例校验；回归超时 A、请求 B、A 迟到及 B 正常回复。 |
| B2 | 保存只建立一份块映射；Hirschberg 分治取代完整 LCS 矩阵，线性空间；重复段落、大文档与随机 LCS oracle 回归。共同前后缀减少普通局部编辑的工作量，无共同内容时直接返回；最坏时间仍为 O(n×m)，不再按每条标注重复计算。 |
| B3 | 安装前通过 aapt2 读取 APK Manifest；缺少或伪造 metadata 均不能放行生产包。 |
| B4 | 保存事务同步高亮、笔记及其搜索投影；数据库失败时回滚。 |
| B5 | 手柄未超过拖动阈值保持原选区；移动从字符锚点补偿手柄外移和按下偏移。 |
| B6 | 长按期间使用最新文本状态，文本就绪后保留原手势生命周期；覆盖手指仍按下的 ready、move、up 时序。 |
| B7 | 原始 URL 先分离查询和片段，再单次百分号解码；字面文本整体转 UTF-8，保留 emoji 与编码标点；维持路径隔离。 |
| B8 | Markdown 书签定义为内容位置，随编辑映射；保留书签独立的段内偏移和视口信息。 |
| B9 | 独立 pdf-tool 返回逐文件清理结果；取消期间 NonCancellable 清理，删除 false 或异常时显示残留文件。该模块不进入 Reader APK。 |
| B10 | 本机默认发布入口加入 `:app:testReleaseUnitTest`。 |
| B11 | 恢复脚本按实际 QA Room 检查结果输出 `verified/not_run`；生产分支不能宣称 Room 已验证。 |
| B12 | 冻结范围纳入 Full 验证涉及的 pdf-tool，保留生成物和签名秘密排除规则。 |
| B13 | 编辑器 README 从当前仓库根执行，校验 `app/version.properties`，推到github，不再导航到 82 版旧树。 |
| B14 | 明确 Windows / Linux 宿主使用在线 ADB push；Android 本机实际可访问共享存储时才 cp；无设备时记录手机传输 pending。 |
| B15 | 渲染工具分别报告精确像素差、容差差、尺寸与缺页；差异/缺失返回失败，输入错误返回 2。 |
| B16 | Markdown 跳转改用带独立 cookie 的异步 Trace；finally 关闭，取消继续传播；覆盖并发挂起、取消、完成和异常。 |
| B17 | 位移、纸面颜色与阴影延迟至绘制/图层阶段读取，复用原有唯一翻页公式；保留叠放、释放和取消视觉；重排收缩时退场图层不再访问范围已失效的页号。受控宿主采样组合次数 87→3，真机帧率仍待验。 |
| B18 | 入口规则和功能地图说明现行 Markdown 编辑、有限 EPUB 与当前事实源；品牌、离线和数据保护规则保留。 |
| B19 | Wiki 更新源码与测试导航、迁移路径和当前引用校验；历史 `validation.json` 原样保留，当前结果另存。 |
| B20 | 75 项候选逐项扫描所有源集、测试与资源引用：删除 61 项无引用字符串，12 项迁入 QA，2 项因仪器测试使用保留。详见 `resources-audit.json`。 |
| B21 | 锚点回归使用仓库内最小 fixture，不依赖 Android 绝对路径，不移除原有断言。 |
| B22 | 未找到可验证的本机 impeccable 入口，规则不再声称可直接调用 `/impeccable`；明确定位实际 SKILL.md 后再用，设计禁止项继续生效。 |
| B23 | 16 份 JVM 崩溃/重放日志迁入 `output/diagnostics/legacy-jvm-20261113/`，逐份 SHA-256 核对并记录原路径；现存审查、恢复与源码检查点保留，不批量删除 output。 |

## 证据与边界

本轮证据目录：`output/audit-fixes-20261113/`。编译输入冻结包含 app、core、benchmark、pdf-tool 与编辑器，构建和测试结束后重新核对全部字节。签名秘密仅由本机 helper 安全注入，不进入源码或报告。

物理手机未连接。API35 软件图形模拟器首次冷启动出现 System UI ANR，不能把该状态的采样当有效性能或手感结论；已关闭以释放构建内存。后续有效的模拟器运行单独记录，不能借用历史设备结论。

## 最终验证

| 范围 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| app QA | 1483 | 0 | 0 | 1 |
| app Release | 1304 | 0 | 0 | 1 |
| core/database | 48 | 0 | 0 | 0 |
| core/pdf | 465 | 0 | 0 | 0 |
| pdf-tool | 51 | 0 | 0 | 0 |

core/model 无 JVM 测试源。两项 app 跳过均为同一符号链接防逃逸用例：Windows 环境不支持创建符号链接；没有删除用例或取消断言。Laozi 最小锚点 fixture 已实际执行通过。

全部模块 lint 零错误。Reader 98 warnings、6 hints；core/model 0、core/database 4、core/pdf 8、pdf-tool 16 warnings。PowerShell 契约 11/11、Python 渲染工具 8/8、定向宿主 66/66 通过，Web 编辑器 28 个文件契约通过。完整 XML、lint XML 和数量汇总分别保存在 `unit-results/`、各 `*-lint-release.xml` 与 `verification-summary.json`。

`MigrationV15ToV16Test` 实际执行 1 项，通过，位于 core/database XML。真实 Room 事务回归在高亮、笔记、书签及标注搜索投影都已更新后，才在最终 DOCUMENT 索引写入注入异常，验证整笔保存回滚。快照测试覆盖超时 A→B→A 迟到→B 真回复及 WebView 替换。PDF 继续原指针流测试明确先推进宿主时钟确认长按已启动；手柄拖动使用外移后的真实手柄位置，保留原预期选区。

EPUB API34 NATIVE 图形宿主采样：8 个受控拖动帧及收敛过程，composition 读取为 87 次组合，延迟读取为 3 次初始组合，图层读取 89 次、真实 Canvas 绘制 48 次；结果见 `epub-host-phase-sampling.json`。既有前向、反向、释放、取消、重抓、分页重排和锚点回归均通过。这是重组与绘制证据，不能推导物理手机 FPS。

原始子代理差分草稿未通过随机 LCS oracle，已替换为精确 Hirschberg 对齐。回归保留 2000 组随机 DP-LCS 对照和 5000 块文档测试。首轮取消用例补上任务已启动的同步条件，真实 Room setup 明确返回 Unit；宿主绘制显式执行 Canvas，旧调用链契约同步到真实 helper 名称，没有削弱行为断言。

## 同源与构建

最终编译输入为 `candidate-3.zip` / `candidate-3-sha256.json`：762 项，core 141 项，含完整 pdf-tool。`source-final-verification.json` 为 unchanged=true，零新增、零缺失、零变化。core 与 pdf-tool 在全模块第二轮通过后未再改变，连续性见 `verified-module-continuity.json`；应用的最终全量 QA / Release / lint 见 `release-attempt-3.log`。

第三轮打包在默认 1 GiB heap / 384 MiB metaspace 下持续出现回收压力，仅终止本任务已确认 PID 的 Gradle 进程，未停止其他用户程序。保持源码冻结，以本轮命令行 2 GiB heap / 768 MiB metaspace 重跑 `:app:assembleRelease`，`release-package-4.log` 明确 BUILD SUCCESSFUL；没有修改仓库默认内存配置，没有关闭 R8 或资源压缩。签名由 `reader-build-local.ps1 -Release` 安全注入。旧 APK 没有作为本轮产物交付。

## 正式包与设备

正式交付：`D:\03_M1racle Reader\apk\M1racle-Reader-1.0.0-96.apk`，1.0.0 / 96，27732545 bytes（26.45 MiB）。SHA-256：`C21099A31C65A4B08B8D96630ECF5D08D2C81C4C3E4E213AD13150D002DBB33A`。证书 SHA-256：`2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`，与 95 一致。签名验证、ZIP 16 KiB 对齐、8 份原生库 ELF LOAD 对齐、包名、minSdk 31 / targetSdk 35、无 INTERNET 均通过；保留 `profileable android:shell=true`。摘要旁存 `.apk.sha256`，详情见 `apk-96-verification.json`。

本轮 API35 / Swiftshader 模拟器以保留数据的 `adb install -r` 升级成功，冷启动 Status=ok；首页、三页 PDF、Markdown 阅读、进入真实 WebView 编辑器与未改正文的完成返回通过，崩溃缓冲为空。构建输出、Outbox、本机交付、模拟器 Download 和安装后的 base.apk 均为同一摘要。结果、UI tree 和截图见 `emulator-96-smoke.json` 及 `*-96.xml/png`。测试分辨率 540×1200 / density210；结束后恢复默认显示参数并关闭本轮启动的模拟器。

结论为 implemented 完成、automated_verified 通过且保留环境跳过、device_verified 仅限上述模拟器范围、physical acceptance pending，状态 READY_WITH_LIMITATIONS。手机未连接，手机 Download 传输、安装、真实 PDF 选字与复制、IME、手感、密集大文档和完整人工发布门禁未完成，不能宣称达到 Adobe 手感或已获实机验收。
