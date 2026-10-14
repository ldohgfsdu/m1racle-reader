# M1racle Reader 发布门禁

当前产物和验证结论唯一入口为 [CURRENT_STATUS](docs/CURRENT_STATUS.md)。本清单定义每个新候选必须重新满足的条件；历史版本通过不能自动继承。[本轮稳定性证据与验收矩阵](docs/STABILIZATION_20261013.md)记录执行结果。旧清单已[归档](docs/history/RELEASE_CHECKLIST_before_stabilization_20261013.md)。

## 源码与版本

- [ ] 保存 HEAD、未提交状态、完整源码检查点及 SHA-256；测试、构建与交付须绑定同一候选源码。
- [ ] 核对 app/version.properties；每次分发新产物单调增加 versionCode，不能覆盖同版本身份的新二进制。
- [ ] 核对 CHANGELOG、版本说明与实际用户可见行为，明确未验收事项。

## 自动验证

- [ ] 全量 :app:testQaUnitTest，记录实际执行数、失败、错误、跳过、命令、退出码与源码摘要。
- [ ] :app:testReleaseUnitTest 覆盖生产变体契约；QA 专有代码不代替生产约束。
- [ ] :app:lintRelease 无错误；警告须保留实际数量与报告，不禁用检查制造通过。
- [ ] 相关原生回归通过。底栏光学包括日夜主题、左右端帽、细色散、图文局部变色、真实来源、拖动/释放/取消；不放宽断言。
- [ ] 核心阅读回归覆盖 Markdown / PDF / EPUB、书签与位置恢复、批注/搜索、导入/文件操作、PDF 工具、字体/重排、系统滚动截图。

所有 instrumentation 仅针对 com.m1racle.reader.qa；禁止 connectedDebug 或其他生产包连接测试。仅任务确需时构建 QA；禁止主动构建/交付 Debug。

## 数据与迁移

当前 Room **17**，以源码 AppDatabase 与 app/schemas 为准；公开基线自 12 起，现有迁移链为 12 → 13 → 14 → 15 → 16 → 17。后续修改 schema 必须补必要迁移与回归，不编造早期 schema。

- [ ] 当前迁移及历史数据回填回归通过。
- [ ] 正式包只 adb install -r；升级前后核对文档、标注、字体和阅读位置，禁止卸载或清库。
- [ ] 物理手机验证打开/恢复 Markdown、PDF、有限 EPUB；模拟器结果须明确标注。

## 签名、产物与交付

- [ ] 四项 M1RACLE_RELEASE_* 环境变量安全注入，正式 :app:assembleRelease 成功；不使用临时签名或未签名包。
- [ ] apksigner verify --verbose --print-certs 通过，与现有正式证书一致，native 库满足 16 KiB 对齐。
- [ ] Manifest：包名 com.m1racle.reader、minSdk 31 / targetSdk 35、无 INTERNET；保留 profileable android:shell=true 并在发布说明中说明。
- [ ] 本次构建 APK、Outbox 和交付文件的实际字节数与 SHA-256 一致；旧包不能作为本轮产物。
- [ ] 每次成功 assembleRelease 后自动复制到唯一在线设备 /storage/emulated/0/Download/m1racle-reader-release-latest.apk；设备缺失须记录交付 pending。
- [ ] 正式签名材料在仓库外安全备份，不写入源码或报告。

## 人工验收与结论

- [ ] 物理手机按参考媒体核对底栏整栏颜色/透色、左右留白、文字可读性、连续拖动与取消回弹。
- [ ] 物理手机核对 EPUB 长段落、目录、字号重排/字体/图片、PDF 两种阅读模式与编辑返回。
- [ ] 资源、滚动性能和系统厂商长截图面板流程通过；单次截图不能证明动态手感。
- [ ] 报告分别记录 implemented / automated_verified / device_verified / pending；模拟器和物理手机证据分列，所有硬门禁通过后才宣布验收完成。

构建环境：JDK 17、Gradle Wrapper 9.5.0、Android Platform 37、Build Tools 36.0.0。运行方法见 [Code Wiki](docs/code-wiki/README.md)。
