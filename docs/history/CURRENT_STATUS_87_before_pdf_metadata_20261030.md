# 当前状态

2026-10-30：正式签名 Release **1.0.0 / 87，Room17**。当前仓库 D:/03_M1racle Reader/reader-ui-release-20261025；本机入口统一读取 reader-env-local.ps1 的 $readerRepository。原 reader 与 82 工作树保留。

首页使用纯 M 母标与 Reader、独立继续阅读及最多五条最近预览，查看全部进入完整文库并重置类型筛选。底栏恢复完整 Radix 蓝色并降低叠层泛白，编辑器与原生共用色板，精简文案与转场。启动图标按用户追加要求匹配 Note 的 72dp 品牌画板，整体放大 12.5%，七条原路径不变；设置母标及单次 Reader 保留。继承 85 的编辑器 / PDF 旋转及尺寸修复。见[本轮实现与验证](HOME_POLISH_20261029.md)。

唯一交付 D:/03_M1racle Reader/apk/M1racle-Reader-1.0.0-87.apk，27605878 字节（26.33 MiB），SHA-256 1F7D32B36DF7A8A8EF24F7F7628CB1B5F4EE717FA95A8B1ED382CC15A25DBD78。构建、Outbox、交付、已安装包与模拟器 Download 摘要一致；生产签名、16KiB、R8 / 资源压缩、35 个 assets 校验通过，无 INTERNET。

最终 87 同源完整 Release 1686 项，0 failures / 0 errors，2 项既有环境跳过；lint 0 errors / 179 warnings / 6 hints。87 相对 86 没有主 Kotlin / 编辑器源码变化；另有 86 界面阶段完整 QA 1843 项及原生 10/10 的独立记录，未充作 87 新跑。图标几何、编译缩放、真实启动器，以及正式界面的首页、筛选重置、MD 单行和深浅主题已验证。

仅 API35 模拟器在线，已保留数据覆盖安装正式 87。物理手机未连接，手机安装、物理输入法与用户视觉 / 手感验收 pending；85 六项扩展原生旧失败仍待独立核查。本轮 READY_WITH_LIMITATIONS；未提交、未推送、未公开发布。

证据 D:/03_M1racle Reader/output/home-polish-20261029/v87；85 状态见[历史](history/CURRENT_STATUS_85_before_home_polish_20261029.md)。

源码另有尚未打包的 [Markdown 文件头 BOM 解析修复](MARKDOWN_BOM_20261030.md)及 [PDF 页面重复打开优化](PDF_RENDER_METADATA_20261030.md)。PDF 定向 Release 648 项和四轮模拟器渲染专项通过，耗时证据及限制见报告。当前 87 交付包不含这两项修改；新包、完整阅读性能及物理手机验证 pending。
