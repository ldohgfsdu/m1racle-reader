# R7 test classification

This file prevents legacy and structural checks from being reported as R7 runtime proof.

## R6_LEGACY

Historical R5/R6 LazyColumn, overlay, lock-session, hit-test, and handoff coverage:

- `R5ContinuousScrollTest`
- `ContinuousTransformMathTest`
- `ContinuousZoomHandoffTest`
- `R6PdfInteractionTest`
- `R61PersistentZoomSessionTest`
- `R62ZoomTargetHitTest`
- `R63TwoFingerGestureArbitrationTest`

These remain regression history only and do not prove `ContinuousDocumentViewport` behavior.

## R7_PURE_LOGIC

- `R7P0aGestureContractTest`
- `ContinuousDocumentGeometryTest`
- `ContinuousInteractionTransactionTest`
- `ContinuousViewportPolicyTest`
- `ContinuousRenderSchedulingTest`
- `ContinuousLazyPolicyTest`
- `PdfRenderQualityTest`
- `PdfPageBitmapCacheTest`
- shared navigation assertions in `PdfReadingModeTest`

These tests prove deterministic state/geometry/policy behavior only.

## R7_RUNTIME_REQUIRED

- `R7GestureLifecycleInstrumentedTest` — production viewport, real Compose lifecycle and pointer input
- `R7ConfigurationRecreationInstrumentedTest` — `ActivityScenario.recreate()` with retained activity-scoped VM

These are runtime evidence only when `connectedDebugAndroidTest` actually executes them on a target.

## STRUCTURAL_GUARD_ONLY

- `R7ContinuousViewportTest`
- `R71FrameStabilityTest`

Only an actually executed instrumentation/device test may be reported as runtime evidence.
`File.readText()/contains()` assertions must always be labeled structural guards.
