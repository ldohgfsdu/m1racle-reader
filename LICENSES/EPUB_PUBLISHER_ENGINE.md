# EPUB 字体来源

当前EPUB恢复43版原生分页器；Readium依赖和随包许可已随移除的引擎退役。没有复制Readest / Lumi / Morealm应用源码。

内置Noto Serif CJK SC Regular完整简体中文OTF字体，SIL Open Font License 1.1。随包许可位于 `assets/licenses/NotoSerifCJK-OFL.txt`。来源：https://github.com/notofonts/noto-cjk/blob/main/Serif/OTF/SimplifiedChinese/NotoSerifCJKsc-Regular.otf 。大小24,543,080字节，SHA-256 `2a2eae2628df83556c54018c41e20fa532c1b862c5256ae8b3f23feb918d12ca`。

内置字体在选择“宋体”时由Android原生字体加载。用户导入的TTF / OTF复制到私有字体库，通过同一TextStyle测量与绘制；不从网络下载。
