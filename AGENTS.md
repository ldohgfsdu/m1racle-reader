# MarkLeaf

Android 本地优先 Markdown 阅读器（墨阅）。

## Scope

Follow `docs/PRD.md`. MVP is local Markdown reading only.

- No INTERNET permission
- No ads / analytics / accounts / cloud sync
- Do not expand beyond the current task scope

## Inbox

External files may appear under `/storage/emulated/0/inbox/MarkLeaf/`.

## Agent startup / handoff

Before implementing, read in order:

1. `.memory/active_object.yaml` — current active object (id, priority, status, scope)
2. `.memory/CURRENT_STATE.md` — project state, APK, last build, git notes
3. If active object is PDF viewer stability: **`docs/PDF_VIEWER_HANDOFF.md`** (executable technical handoff)

Do not expand beyond the active object scope. Do not migrate PDFium unless explicitly tasked.
