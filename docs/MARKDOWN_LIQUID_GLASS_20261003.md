# Markdown 顶栏采用 Liquid Glass 分组原则

依据：[Apple Adopting Liquid Glass](https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass)，读取官方 Markdown 正文。应用其功能层、工具栏分组、节制材质、滚动可读性和辅助字号原则；本项目仍为 Android Compose，实现不调用 Apple 平台 API。

返回使用圆形玻璃，搜索 / 目录共用胶囊，更多独立圆形；同组共享按压状态，组间留 8dp。标题 / 阅读进度为前景文本，不增加玻璃背景。40dp 视觉控件保留 48dp 点击区域。使用现有 Control 配方，显式为顶栏提供正文捕获 host，捕获与玻璃为兄弟节点；控件图标不进入采样，不产生递归或重复字形。弹出菜单沿用既有跨窗口材质。

滚动边缘沿用同色渐变，顶栏收起仅保留状态栏下 16dp；不叠加另一层全宽玻璃。顶栏高度随系统标题和副标题字号增长，正文顶部留白、收起距离、标题点击区域和弹出位置共享该高度，修正 200% 字号裁掉阅读进度的问题。

验证：80 项相关 QA 单测；新增 320dp / 200% 字号布局测试检查 48dp 点击区域、组间分离、两个文本布局不重叠、搜索和目录回调；先前像素测试继续覆盖浅深色同色边缘。Release Kotlin 编译、QA 构建和 Release Lint 通过（0 errors / 158 warnings / 4 hints）。模拟器 Android 15 核对浅色、按压、搜索、深色 200% 字号。

未运行完整测试套件，未做真机验收。正式签名配置缺失，未构建或交付正式 APK。外部证据目录 C:/Users/21234/.devhub/runs/unmanaged-reader-markdown-glass-20261003，最终日志 final-checks.log；早期 dark-large.png 裁切已被 adaptive-dark-large.png 修正。
