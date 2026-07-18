-- Set these two timestamps to the first/last AndroidOwner:onTouch interval
-- reported by the bounds query before running the remaining statements.
-- The capture protocol requires the interval to be at least 30 seconds.

SELECT
  MIN(s.ts) AS touch_start_ns,
  MAX(s.ts + s.dur) AS touch_end_ns,
  ROUND((MAX(s.ts + s.dur) - MIN(s.ts)) / 1e9, 3) AS touch_span_s,
  COUNT(*) AS touch_slices
FROM slice s
JOIN thread_track tt ON s.track_id = tt.id
JOIN thread t ON tt.utid = t.utid
JOIN process p ON t.upid = p.upid
WHERE p.name = 'com.markleaf.app.qa'
  AND t.is_main_thread = 1
  AND s.name = 'AndroidOwner:onTouch';

-- Replace the literals in params with the touch bounds returned above.
WITH params(start_ns, end_ns) AS (VALUES (0, 0)),
app AS (
  SELECT upid FROM process WHERE name = 'com.markleaf.app.qa' LIMIT 1
),
frames AS (
  SELECT a.*
  FROM actual_frame_timeline_slice a, params, app
  WHERE a.upid = app.upid
    AND a.surface_frame_token != 0
    AND a.ts >= params.start_ns
    AND a.ts + a.dur <= params.end_ns
)
SELECT
  COUNT(*) AS frames,
  ROUND(COUNT(*) * 1e9 / (end_ns - start_ns), 3) AS avg_presented_fps,
  ROUND(1e9 / (PERCENTILE(dur, 99) / 1.0), 3) AS one_percent_low_fps,
  ROUND(PERCENTILE(dur, 50) / 1e6, 3) AS p50_ms,
  ROUND(PERCENTILE(dur, 95) / 1e6, 3) AS p95_ms,
  ROUND(PERCENTILE(dur, 99) / 1e6, 3) AS p99_ms,
  ROUND(MAX(dur) / 1e6, 3) AS max_ms,
  SUM(dur > 8333333) AS over_8_33,
  SUM(dur > 16666667) AS over_16_67,
  SUM(jank_type != 'None') AS jank_rows
FROM frames, params;

WITH params(start_ns, end_ns) AS (VALUES (0, 0)),
app AS (SELECT upid FROM process WHERE name = 'com.markleaf.app.qa' LIMIT 1),
main AS (
  SELECT t.utid FROM thread t, app WHERE t.upid = app.upid AND t.is_main_thread = 1
),
window_slices AS (
  SELECT s.*
  FROM slice s
  JOIN thread_track tt ON s.track_id = tt.id
  JOIN main ON tt.utid = main.utid
  JOIN params
  WHERE s.ts >= params.start_ns AND s.ts + s.dur <= params.end_ns
)
SELECT
  SUM(name GLOB 'Choreographer#doFrame *') AS doframe_count,
  SUM(name GLOB 'Choreographer#doFrame *' AND dur > 8333333) AS doframe_over_8_33,
  SUM(name = 'AndroidOwner:measureAndLayout' AND dur > 5000000) AS measure_over_5,
  SUM(name = 'Recomposer:recompose') AS recomposer_count,
  ROUND(SUM(CASE WHEN name = 'Recomposer:recompose' THEN dur ELSE 0 END) / 1e6, 3) AS recompose_ms,
  ROUND(SUM(CASE WHEN name = 'animation' THEN dur ELSE 0 END) / 1e6, 3) AS animation_ms,
  ROUND(MAX(CASE WHEN name = 'animation' THEN dur ELSE 0 END) / 1e6, 3) AS animation_max_ms
FROM window_slices;

WITH params(start_ns, end_ns) AS (VALUES (0, 0)),
app AS (SELECT upid FROM process WHERE name = 'com.markleaf.app.qa' LIMIT 1)
SELECT
  t.name,
  ROUND(SUM(s.dur) / 1e6, 3) AS total_ms,
  ROUND(MAX(s.dur) / 1e6, 3) AS max_ms,
  COUNT(*) AS slices
FROM slice s
JOIN thread_track tt ON s.track_id = tt.id
JOIN thread t ON tt.utid = t.utid
JOIN app ON t.upid = app.upid
JOIN params
WHERE t.name = 'RenderThread'
  AND s.ts >= params.start_ns AND s.ts + s.dur <= params.end_ns
GROUP BY t.name;
