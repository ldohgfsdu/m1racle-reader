# r26.1 — Liquid-only UI + Basic→Advanced anchor fix + Parent 220dp + Home

## 1) Frosted / material-switch removal
- Removed `ReaderChromeMaterialStyle` (FROSTED/LIQUID enum)
- Removed `PdfChromePrefs` material key / get/set
- Removed ViewModel `chromeMaterialStyle` / `setChromeMaterialStyle`
- Removed 阅读界面 rows「毛玻璃磨砂 / 液态玻璃」
- Liquid is the only chrome surface language

## 2) Unique Liquid surface architecture
- New: `ui/common/MarkLeafLiquidSurface.kt`
  - `markLeafLiquidSurface(shape, level, exportForChild)`
  - `LiquidSurfaceLevel`: Control / Parent / Foreground
  - `LiquidBackdropHost` + `LocalLiquidBackdropHost`
  - Tokens: `LiquidGlassTokens` (kept r26 liquid numbers)
- Reader entry: `readerChromeSurface` → `markLeafLiquidSurface`
- Home: top bar + FAB use same primitive

## 3) Menu state machine
Exclusive `ReaderMoreState`:
- `MAIN`, `INLINE_*` (Basic)
- `PREPARING_CHILD_*` (two-phase)
- `CHILD_*` (Advanced open)
- Never Basic + Advanced simultaneously

`onOpenChild(Basic, advanced)` → `PREPARING_*`  
`onPreparingReady` → `CHILD_*`

## 4) canonicalMainBounds
- Updated only when `state == MAIN` and both inline progresses are 0
- Rows 2–5 stored from `boundsInRoot`
- Preparing/Child prefer canonical over transient expanded Y

## 5) Basic→Advanced two-phase
1. State → PREPARING → snap Basic progress 0, Parent height = six-row
2. Wait layout stable (height ≈ base, row bounds present)
3. Freeze source from canonical Main bounds
4. Promote → CHILD → morph from correct row

## 6) ParentCard width
- `MoreMenuWidth = 220.dp`
- Row pad 18dp, icon col 24dp, gap 14dp, row 52dp

## 7) Home
- **Never immersive**: on enter `wic.show(all)`; PDF `DisposableEffect` restores bars on leave
- Liquid top bar + liquid FAB via `LocalLiquidBackdropHost` + `layerBackdrop` on list content

## 8) Tests
- Matrix: every Basic → every Advanced goes PREPARING then CHILD
- Mutual exclusion, geometry 220dp, no Frosted residues, home liquid + show bars

## 9) Device
- adb empty at build time → screenshots/recordings PENDING

## Verification
- Unit: PASS (ReaderMore / ReaderChrome / R25 / AdvancedChild filters)
- assembleRelease: see outbox
