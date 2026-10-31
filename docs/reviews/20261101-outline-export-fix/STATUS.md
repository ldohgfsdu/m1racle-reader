# 20261101 H1/H2 目录导出修复（未提交）

- H1：ExportRangeResolver.siblingsOf 改为先按 startPageIndex 排序，再按 sortOrder、title 排序；upsertOutlineNode 的 sortOrder 改为 Int?，编辑时保留原值，新节点取同级最大值 + 1；PdfViewerViewModel.saveOutlineNode 的默认值改为 null（WIP 文件里只改了这一行）。
- H2：父节点 end<=start，或有子节点起点超出父节点 end 时，用父节点计算出的结束页（effectiveParentEnd）代替存储值；用户手工设定的范围不再按父节点裁剪；裁剪后若范围无效，保留原范围，不再塌缩到父节点起始页。
- 测试：repro/OutlineExportRangeReproTest（修复前 3/3 失败，见 evidence/repro-before-fix.log）；修复后全量 testQaUnitTest 1890 个全部通过，0 失败（evidence/full-suite-after-fix.log）。