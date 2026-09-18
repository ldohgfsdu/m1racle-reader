# PDF edit Chinese font fixture

`ChineseFixture.ttf` is a 3,980-byte, weight-400 subset of Noto Sans SC,
renamed M1racleTestChinese. It contains only `原始正文中文修改测试ABC `.
Used only by JVM tests; never packaged in the app.

Source: https://github.com/google/fonts/tree/main/ofl/notosanssc
Downloaded 2026-09-07 from `NotoSansSC[wght].ttf` on main.
Original SHA-256: `a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da`.
License: included `OFL.txt` (SIL Open Font License 1.1).

Generated with fontTools: subset the listed text with `layout_features=[]`,
instantiate `wght=400`, rename name IDs 1/3/4/6/16 to M1racleTestChinese
and IDs 2/17 to Regular, save as TrueType. The large source font and tooling
are temporary build-host inputs, not new Android dependencies.
