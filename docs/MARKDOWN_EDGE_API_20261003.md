# Markdown 滚动边缘 API 对齐

通过浏览器读取官方 ScrollEdgeEffectStyle 和 UIScrollEdgeEffect / Style。

- https://developer.apple.com/documentation/swiftui/scrolledgeeffectstyle
- https://developer.apple.com/documentation/uikit/uiscrolledgeeffect
- https://developer.apple.com/documentation/uikit/uiscrolledgeeffect/style-swift.class

官方语义：automatic 根据平台和场景处理固定元素与滚动内容的重叠；hard 具有近乎不透明的线性分界；soft 提供柔和模糊过渡。UIKit 在各边缘分别配置 style 与 isHidden。

本项目是 Android，仅采用行为与视觉语义，不调用 Apple API。沿用用户要求的柔和顶部过渡，新增正文重叠判断：首项位移越过顶栏下的 8dp 留白或首项已移出时启用；180ms 淡入 / 淡出。顶部尚无正文重叠、加载或错误页时不显示滚动边缘。顶栏收起继续保留系统状态栏的小范围过渡。只作用于 Markdown 上边缘；玻璃控件分组、48dp 点击区域、自适应字号布局保持不变。

验证：Release Kotlin 编译、QA 构建、Release Lint 通过；81 项相关单测全部通过（verified-tests.log），其中新增实际 Compose View 像素测试依次验证无重叠时透明、发生重叠时提供阅读背景覆层、返回顶部时透明。旧灰色根因测试和窄屏 / 大字号布局测试继续通过。

本轮 QA 安装成功，模拟器被其他应用占用，停止输入后将本轮 UI 复验标为 pending，不将上一轮模拟器结果冒充本轮验收。未进行完整套件或真机验收。正式签名四项配置缺失，未交付 Release。

证据目录：C:/Users/21234/.devhub/runs/unmanaged-reader-edge-api-20261003。身份 unmanaged；runId / projectId / machineId unknown。
