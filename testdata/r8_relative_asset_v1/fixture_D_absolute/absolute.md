# Fixture D — 绝对资源

![http](http://example.com/a.png)
![https](https://example.com/a.png)
![data](data:image/png;base64,iVBORw0KGgo=)
![file_abs](file:///system/etc/hosts)

预期：http/https 显示「网络图片已禁用」；data/file 不被相对解析器改写；受控失败；正文仍在。
