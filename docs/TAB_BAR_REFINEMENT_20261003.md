# Tab Bar 参考与独立导入加号

## 范围与参考

用户提供 Apple [Tab bars](https://developer.apple.com/design/human-interface-guidelines/tab-bars) 和独立搜索按钮示例，随后明确要求按钮改为加号。保留首页 / 文库 / 书签 / 设置四个稳定标签，末尾单独放导入动作，不变成第五个标签。首页 / 文库原搜索入口保留，没有增加底栏搜索或自动聚焦行为。

## 当前实现

默认 60dp 高度、4dp 内缩，标签区与 60dp 圆形加号之间留 8dp。图标统一为 24dp 实心样式，未选中为中性前景，选中沿用蓝色；按系统字高适配栏高。保留点击、跟手拖动、松手提交与取消复原；缩小装饰膨胀，透镜拾起 14%、文字图标轻微 6% 放大。

加号与空首页导入按钮共用导航层持有的 OpenDocument launcher、HomeViewModel 和既有 DocumentRepository 导入管线。导入忙时禁用；取消不改变标签，成功沿既有 openDocument 打开对应阅读器；错误走统一 Snackbar。没有新增业务导入分支或内容格式。

原图标捕获 / 前景切孔会让字形进入高折射边缘，模拟器按压截图出现重复文字碎片。本轮移除底栏图标采样与清孔层，选中透镜放在可读前景之前，只采样真实页面；玻璃仍实时折射背景，文字图标独立清晰。PDF Capsule 与分段控件的配方不变。

## 验证与限制

116 项针对性 QA 单测（ui.hub / ui.home / ui.material）通过；Release 源码编译、QA 构建及 Release Lint 通过（0 errors / 158 warnings / 4 hints）。新增覆盖独立加号不改变标签、导入忙禁用、320dp 窄屏 + 200% 标签完整；首页布局回归按新的共享底栏导入入口验证，未删减忙态或避让断言。采样契约更新为页面来源与前景不被捕获。

模拟器 emulator-5554：拖动至设置、加号打开 SAF、取消留在设置；选择隔离 Markdown 测试文件后进入阅读器；浅深色 / 200% 字号、按压无文字碎片核对。仅安装 QA。测试夹具及证据在机器外部账本，不操作用户生产包数据。

迭代中的编译失败（错误的 padding 重载）与旧测试对顶部导入 / 图标采样的断言失败保留在日志；最终依据成功的最终检查。Release 签名门禁仍因缺少 M1RACLE_RELEASE_STORE_FILE / STORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD 退出 1；未交付 Release、未跑完整套件或真机验收。

本会话 unmanaged，runId / projectId / machineId unknown。日志、APK 元数据与截图位于 C:/Users/21234/.devhub/runs/unmanaged-reader-tabbar-20261003。
