# M1racle Reader 图标

按用户提供的 Note 图标与 M1racle 母标统一：原母标 + Reader，黑色 #181818、白底，同款 Inter Medium 500 / opsz32。两张输入均与 Note 正式资产 SHA256 一致；母标复用既有矢量描摹，不重绘笔势，原始 PNG 仍是权威源。

- svg/icon-512-day.svg：512px 圆角方形完整图标，字标已转轮廓。
- svg/adaptive-circle-preview.svg：Android 圆形蒙版预览。
- png/：相应渲染结果，不作为 Android 的光栅依赖。
- source/：原母标、复用的 trace、来源摘要。
- fonts/：原 Inter 可变字体及 OFL。
- scripts/branding/build_reader_icon.py：从仓库内源文件再生成 SVG 与原生 VectorDrawable，需要 FontTools / Pillow；不用手工描字，不读取 Note 的项目文件。

Android 使用108dp前景，与 Note 一致的72dp品牌画板居中，移除原64dp画板的额外留白。母标与字标整体放大12.5%，系统决定最终裁切形状；未在前景重复烘焙圆角或白底。正常、圆形与系统着色图标共用同一完整前景。原生母标 evenOdd、字形 nonZero，与相应 SVG 填充规则一致。包内 source/provenance.json 与静态检查记录实际来源和安全区。

这是本轮 Reader 图标实现及可复现源文件，不更改 Note 或母品牌权威源。
