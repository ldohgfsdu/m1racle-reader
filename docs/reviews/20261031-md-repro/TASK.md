# TASK

TASK: 用失败单测复现 Markdown 批注漂移（F1）和独立 Markdown 相对图片失效（F2）
RISK: LOW
GOAL: 新增两份复现测试，修复前必须因正确原因失败
SCOPE: app/src/test/java/com/m1racle/reader/repro/*；本文件夹
NON-GOALS: 不改生产代码，不改/放宽其他断言，不提交、不推送，不碰设备
ACCEPTANCE: 新测试失败，失败信息指向 F1/F2；其他测试不受影响
EVIDENCE CLASS: AUTOMATED

## BASELINE
- Git 根：D:\03_M1racle Reader\reader-ui-release-20261025，HEAD 2ffce70，工作区有他人未提交 PDF 改动
- 来源：docs/reviews/20261031-reader-v93-review/STATUS.md

## VERIFY PLAN
- gradlew :app:testQaUnitTest --tests 'com.m1racle.reader.repro.*'
