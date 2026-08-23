# 首页 content-first 信息层级重构

## 1）重构前后层级说明

| 层级 | 重构前 | 重构后 |
|------|--------|--------|
| 顶部 | 可折叠大板 **Liquid 整块 header**（沉浸式 Reader 感） | **轻量品牌头**：app 名 + 副标题 +「最近文件」，普通 background，无大面积 liquid |
| 列表 | 文件卡与 chrome 视觉权重接近；曾受 liquid 统一压力 | **最近文件是主角**：tonal `surfaceContainerLow` 实体卡，无 blur/refraction |
| 浮动 | FAB liquid + header liquid + 可能多层同权 | **仅 FAB（+ 三点菜单）用 liquid**；列表卡永不 glass |
| 系统栏 | 可能继承 Reader IMMERSIVE 顶栏卡住 | 进入首页强制 `show` 全部 system bars |

**设计原则：** Home 与 Reader 共享 design language/token 家族，**不**共享「所有层都 liquid」的材质分布。Reader = immersive overlay-heavy；Home = structured content-heavy。

## 2）新首页结构图

```
┌─────────────────────────────────────┐
│ status bar (always visible)         │
│ 墨阅 MarkLeaf          (22sp Semi)  │  ← Layer 1 轻头部
│ 本地 Markdown / PDF 阅读器 (13sp)   │
│ 最近文件               (15sp Med)   │
├─────────────────────────────────────┤
│ ┌─────────────────────────────────┐ │
│ │ 标题…(≤2行 20sp)        ⋮      │ │  ← Layer 2 列表卡
│ │ PDF / Markdown          (13sp)  │ │     tonal surface
│ │ 时间 · 大小 · 进度      (12sp)  │ │     非 liquid
│ │ ▬▬▬▬▬░░░░  进度条               │ │
│ └─────────────────────────────────┘ │
│ …更多卡片 spacing 12dp…             │
│                          [ + FAB ]  │  ← Layer 3 liquid only
└─────────────────────────────────────┘
```

## 3）Spacing / Typography / Radius tokens

定义于 `HomeDesignTokens`：

| Token | 值 |
|-------|-----|
| PageHorizontal | 16dp |
| SectionGap | 16dp |
| CardGap | 12dp |
| CardPadding | 16dp |
| Card 内 6/8/12 | CardGapTight / Mid / Loose |
| FabClearance | 88dp（列表底安全区） |
| FabSize | 56dp |
| RadiusCard | 24dp |
| RadiusFab | 16dp |
| RadiusMenu | 14dp |
| AppName | 22sp SemiBold |
| Subtitle | 13sp |
| Section | 15sp Medium |
| FileTitle | 20sp SemiBold，最多 2 行 |
| Type | 13sp |
| Meta | 12sp |

## 4）各组件最终参数

### 顶部区
- 背景：`colorScheme.background`（非 liquid）
- 左右：16dp，与列表对齐
- 内容：app 名 / 副标题 / section title 三行，高度收紧
- 无 StatusBarProtection 厚洗、无折叠大玻璃板

### 文件卡
- `surfaceContainerLow` + `outlineVariant` 细边框（alpha ~0.45）
- 圆角 24dp，内边距 16dp，卡间距 12dp
- 结构：标题+三点 → 类型标签 → `时间 · 大小 · 进度` → 底进度条 3dp
- **禁止** `markLeafLiquidSurface` 套在卡片上

### 三点菜单
- 锚点在卡右上 40dp 命中区
- 可 liquid Control 级（浮动操作层），宽 148–200dp，仅「删除记录」

### FAB
- 唯一明显 liquid floating control
- 56dp，`LiquidSurfaceLevel.Control`，右下 Scaffold 槽位
- 列表 bottom inset = nav + 88dp，最后一项不被挡住

## 5）为何首页不应像 Reader 大面积 Liquid

1. **任务不同**：Reader 在 PDF 上叠 chrome；Home 的任务是扫读文件列表。  
2. **可读性**：列表卡若 refraction/blur，标题与元数据噪声上升，扫读变慢。  
3. **层级竞争**：header+card+menu+FAB 全 liquid = 四层同权，无主角。  
4. **性能**：列表滚动时不应对每张卡做 backdrop 采样。  
5. **统一方式**：共享 token/圆角/间距语言即可；材质分布按场景分工。

## 6）截图

当前环境 `adb devices` 为空 → **device 截图 PENDING**。  
安装 APK 后请拍：

1. 首页静态（列表 + 轻头部 + FAB）  
2. 三点菜单展开  
3. 滚到底（最后一张卡完整 + FAB 不遮挡）

## 验证

- Unit: `HomeLayoutContractTest` + 既有 `HomeHeaderScrollStateTest`  
- assembleRelease → Download `markleaf-release-latest.apk`
