# R8 相对图片 V1 — 真机人工验收清单

**目录：** `testdata/r8_relative_asset_v1/`
**包建议：** QA（`assembleQa`）或 Debug。
**禁止：** 污染正式用户隐私数据；不要对生产库做破坏性清理。

## 准备

1. **Fixture A**：将 `fixture_A_app_owned` 打 zip 后应用内「导入包」，或复制到 app files/packages。
2. **Fixture B**：导入 `doc/entry.md` 所在树，或 file 打开（若环境允许）。
3. **Fixture C**：系统分享/打开方式 **仅选择** `only_markdown.md`（单文件），不要授权文件夹。
4. **Fixture D**：打开/导入 `absolute.md`。

## 场景与预期

| ID | 操作 | 预期 |
|----|------|------|
| A1 | 打开 A/README.md | same / child / space / 中文 / ./same 成功；missing 占位「无法访问相对图片」 |
| A2 | 打开 A/chapter/README.md | ../ 系列成功；missing 失败；https「网络图片已禁用」 |
| A3 | 多失败图滚动 | 无 Toast/Dialog 风暴；正文可读 |
| B1 | 打开 B/doc/entry.md | same、sub/child 成功 |
| B2 | ../sibling、../outside | 默认 root=doc 时越界失败（不显示 secret） |
| B3 | notimage.png / notes.txt | 失败占位，不崩溃 |
| C1 | External Open 仅 md | 正文正常；相对 neighbor 受控失败文案 |
| C2 | 取消/替换 session | External Open 生命周期不回归 |
| D1 | absolute.md | http/https 网络禁用；data/file 不相对改写；正文在 |
| R1 | 旋转 | 无重复提示风暴 |
| R2 | 返回再进 | 无权限弹窗风暴 |

## V1 外（不测）

- 文件夹授权 / OPEN_DOCUMENT_TREE
- SAF 目录级相对资源成功
- H1.5A / PDF / Tile

## 结论

- [ ] 全部通过（待人工勾选；**本代理不宣布 device_accepted**）
- [ ] 失败场景：_______
