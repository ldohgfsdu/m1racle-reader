# 阅读流程小幅完善

2026-09-06。用户在展开菜单加厚期间要求继续完善功能。本轮围绕阅读定位完善已有输入流程，不启动范围外功能。

本轮包含已完成的[展开菜单底板加厚](EXPANDED_READER_MENU_FROST.md)，以及 PDF 跳页输入完善：

- 原实现用 String 保存预填页码，没有显式全选，用户输入目标页前需要清空当前数字。现在用 TextFieldValue 明确保留选区，打开时选中整段当前页码，直接输入即可替换。
- 保留数字过滤和长度约束，并按过滤后的字符位置维护光标/选区；不改变 ViewModel 的 1-based 页码检查与跳转链路。
- 输入框始终显示 PDF 页码范围，出错时原位置显示错误提示；错误提示允许换行，便于大字体显示。输入框补充读屏名称。
- 保留展开动画结束后的延迟聚焦、IME Go、取消与错误后继续编辑流程。

回归用实际 Compose 输入验证直接键入替换当前页、越界不提交、修正为最后一页后通过键盘提交。保留胶囊交互、菜单材质和采样层级检查。

本轮没有再次探测 ADB；同一活动任务已探测为空。device_verified: NOT_RUN；需手动检查键盘、实际选区和展开面板观感。

证据目录：`.external_outbox/reader-flow-20260906/`。

implemented：展开菜单加厚与跳页输入完善。automated_verified：JumpPageBodyTest / CapsuleInteractionContractTest 及既有五组菜单材质/采样回归共 64 / 0 / 0 / 0；git diff --check 通过。首次编译缺少语义扩展 import，补齐后回归成功。

签名 Release 构建成功，6,702,822 bytes，SHA-256 `32115988161b072fdf2526a12e4685b5fad8c7e83e792e86d0873760db0e040d`；production 签名验证通过，已覆盖 Download，摘要与 outbox 一致。已有 lintVital ASM 异常仍出现，不声明完整 lint 通过。device_verified: NOT_RUN。
