#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "$script_dir/.." && pwd)"
cd "$project_root"

# R9 top-bar QA through the real MainActivity path.
# Only com.markleaf.app.qa is installed/driven. No package data is cleared.

readonly QA_PACKAGE="com.markleaf.app.qa"
readonly PROTECTED_PACKAGE="com.markleaf.app"
readonly MAIN_ACTIVITY="$QA_PACKAGE/com.markleaf.app.MainActivity"
readonly DEFAULT_APK="dist/r9-markdown-scroll-chrome/markleaf-qa-r9-md-chrome-c-slow-drag.apk"

serial=""
apk="$DEFAULT_APK"
document="Large Search Document"
second_document="Highlight Hits Fixture"
search_query="QRS"
top_marker="Section 0"
gfx_runs=5
cycles_per_run=6
capture_perfetto=0
output_root="dist/r9-markdown-scroll-chrome/adb-runs"

usage() {
  printf 'Usage: %s [options]\n' "$0"
  printf '  --serial SERIAL             adb serial; required with multiple devices\n'
  printf '  --apk PATH                  QA APK (default: %s)\n' "$DEFAULT_APK"
  printf '  --document TITLE            existing QA Markdown card title\n'
  printf '  --second-document TITLE     document-reset card; empty disables this check\n'
  printf '  --search-query ASCII        search boundary query (default: QRS)\n'
  printf '  --top-marker TEXT           text proving the primary document reached top\n'
  printf '  --gfx-runs N                focused gfxinfo runs (default: 5)\n'
  printf '  --cycles-per-run N          fast round trips per run (default: 6)\n'
  printf '  --perfetto                  capture a focused 20-second Perfetto trace\n'
  printf '  --output-root DIR           artifact parent directory\n'
}

while (($#)); do
  case "$1" in
    --serial) serial="${2:?missing serial}"; shift 2 ;;
    --apk) apk="${2:?missing APK path}"; shift 2 ;;
    --document) document="${2:?missing document title}"; shift 2 ;;
    --second-document) second_document="${2-}"; shift 2 ;;
    --search-query) search_query="${2:?missing query}"; shift 2 ;;
    --top-marker) top_marker="${2:?missing marker}"; shift 2 ;;
    --gfx-runs) gfx_runs="${2:?missing run count}"; shift 2 ;;
    --cycles-per-run) cycles_per_run="${2:?missing cycle count}"; shift 2 ;;
    --perfetto) capture_perfetto=1; shift ;;
    --output-root) output_root="${2:?missing output root}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown option: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ "$gfx_runs" =~ ^[1-9][0-9]*$ ]] || { printf 'Invalid --gfx-runs: %s\n' "$gfx_runs" >&2; exit 2; }
[[ "$cycles_per_run" =~ ^[1-9][0-9]*$ ]] || {
  printf 'Invalid --cycles-per-run: %s\n' "$cycles_per_run" >&2
  exit 2
}
[[ -f "$apk" ]] || { printf 'QA APK not found: %s\n' "$apk" >&2; exit 2; }
for command in adb perl sha256sum mktemp; do
  command -v "$command" >/dev/null || { printf '%s is required\n' "$command" >&2; exit 2; }
done
if [[ -x "${AAPT2_PATH:-tools/aapt2}" ]]; then
  aapt2="${AAPT2_PATH:-tools/aapt2}"
else
  command -v aapt2 >/dev/null || {
    printf 'aapt2 is required to verify applicationId before installation\n' >&2
    exit 2
  }
  aapt2="$(command -v aapt2)"
fi

if [[ -z "$serial" ]]; then
  mapfile -t devices < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
  if ((${#devices[@]} != 1)); then
    printf 'Expected one online adb device, found %d; pass --serial.\n' "${#devices[@]}" >&2
    exit 2
  fi
  serial="${devices[0]}"
fi

adbq() { adb -s "$serial" "$@"; }
[[ "$(adbq get-state 2>/dev/null || true)" == "device" ]] || {
  printf 'adb device is not online: %s\n' "$serial" >&2
  exit 2
}
timestamp="$(date +%Y%m%d-%H%M%S)"
output_dir="$output_root/$timestamp"
mkdir -p "$output_dir"
results_tsv="$output_dir/results.tsv"
gfx_tsv="$output_dir/gfxinfo-runs.tsv"
summary="$output_dir/summary.txt"
touch "$results_tsv"
printf 'run\tforeground\tframes\tmodern_janky\tp50\tp90\tp95\tp99\tdeadline_missed\tslow_ui\n' > "$gfx_tsv"

original_accelerometer_rotation=""
original_user_rotation=""
rotation_changed=0
installed_copy=""
cleanup() {
  if ((rotation_changed)); then
    adbq shell settings put system user_rotation "$original_user_rotation" >/dev/null 2>&1 || true
    adbq shell settings put system accelerometer_rotation "$original_accelerometer_rotation" >/dev/null 2>&1 || true
  fi
  [[ -z "$installed_copy" ]] || rm -f "$installed_copy"
}
trap cleanup EXIT INT TERM

pass() { printf 'PASS\t%s\n' "$1" | tee -a "$results_tsv"; }
skip() { printf 'SKIP\t%s\n' "$1" | tee -a "$results_tsv"; }
fail() {
  printf 'FAIL\t%s\n' "$1" | tee -a "$results_tsv" >&2
  exit 1
}

remote_ui="/sdcard/markleaf-r9-qa-window.xml"
ui_dump() {
  local name="$1"
  local attempt
  for attempt in 1 2 3; do
    if adbq shell uiautomator dump "$remote_ui" >/dev/null 2>&1 &&
      adbq exec-out cat "$remote_ui" > "$output_dir/$name.xml" &&
      grep -q '<hierarchy' "$output_dir/$name.xml"; then
      printf '%s\n' "$output_dir/$name.xml"
      return 0
    fi
    sleep 1
  done
  fail "UIAutomator dump failed after 3 attempts: $name"
}

gesture_coords() {
  local file="$1" percent="$2"
  local bounds x1 y1 x2 y2 x center_y delta
  bounds="$(scroll_bounds "$file")" || fail "No scrollable bounds in $file"
  read -r x1 y1 x2 y2 <<< "$bounds"
  x=$(((x1 + x2) / 2))
  center_y=$(((y1 + y2) / 2))
  delta=$(((y2 - y1) * percent / 100))
  printf '%s %s %s\n' "$x" "$((center_y + delta / 2))" "$((center_y - delta / 2))"
}

ui_has() {
  local file="$1" attr="$2" value="$3"
  ATTR="$attr" VALUE="$value" perl -0777 -ne '
    my $attr = quotemeta($ENV{ATTR});
    my $value = quotemeta($ENV{VALUE});
    exit(/$attr="$value"/ ? 0 : 1);
  ' "$file"
}

ui_center() {
  local file="$1" attr="$2" value="$3"
  ATTR="$attr" VALUE="$value" perl -0777 -ne '
    my $attr = quotemeta($ENV{ATTR});
    my $value = quotemeta($ENV{VALUE});
    if (/$attr="$value"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/) {
      print int(($1 + $3) / 2), " ", int(($2 + $4) / 2), "\n";
      exit 0;
    }
    exit 1;
  ' "$file"
}

scroll_bounds() {
  local file="$1"
  perl -0777 -ne '
    if (/scrollable="true"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/) {
      print "$1 $2 $3 $4\n";
      exit 0;
    }
    exit 1;
  ' "$file"
}

assert_qa_foreground() {
  local file="$1" step="$2"
  ui_has "$file" package "$QA_PACKAGE" || fail "$step: foreground is not $QA_PACKAGE"
}

assert_topbar() {
  local file="$1" expected="$2" step="$3" present=0
  ui_has "$file" content-desc "返回" && present=1
  [[ "$expected" != "present" || "$present" -eq 1 ]] || fail "$step: reader top bar is absent"
  [[ "$expected" != "absent" || "$present" -eq 0 ]] || fail "$step: reader top bar is present"
  pass "$step"
}

tap_node() {
  local file="$1" attr="$2" value="$3" step="$4" xy x y
  xy="$(ui_center "$file" "$attr" "$value")" || fail "$step: node not found: $attr=$value"
  read -r x y <<< "$xy"
  adbq shell input tap "$x" "$y" >/dev/null
}

swipe_in_scrollable() {
  local file="$1" direction="$2" duration="$3" percent="${4:-18}"
  local bounds x1 y1 x2 y2 x center_y delta start_y end_y
  bounds="$(scroll_bounds "$file")" || fail "No scrollable bounds in $file"
  read -r x1 y1 x2 y2 <<< "$bounds"
  x=$(((x1 + x2) / 2))
  center_y=$(((y1 + y2) / 2))
  delta=$(((y2 - y1) * percent / 100))
  if [[ "$direction" == "down-document" ]]; then
    start_y=$((center_y + delta / 2))
    end_y=$((center_y - delta / 2))
  else
    start_y=$((center_y - delta / 2))
    end_y=$((center_y + delta / 2))
  fi
  adbq shell input swipe "$x" "$start_y" "$x" "$end_y" "$duration" >/dev/null
}

open_document_from_home() {
  local title="$1" prefix="$2" home_file attempt
  home_file="$(ui_dump "$prefix-home")"
  assert_qa_foreground "$home_file" "$prefix home"
  if ! ui_has "$home_file" text "$title"; then
    # Home may retain a prior list position. Search toward the beginning first.
    for ((attempt=1; attempt<=6; attempt++)); do
      swipe_in_scrollable "$home_file" up-document 120 70
      home_file="$(ui_dump "$prefix-home-up-$attempt")"
      ui_has "$home_file" text "$title" && break
    done
  fi
  if ! ui_has "$home_file" text "$title"; then
    # Then scan later cards one viewport at a time before declaring it missing.
    for ((attempt=1; attempt<=12; attempt++)); do
      swipe_in_scrollable "$home_file" down-document 120 70
      home_file="$(ui_dump "$prefix-home-down-$attempt")"
      ui_has "$home_file" text "$title" && break
    done
  fi
  tap_node "$home_file" text "$title" "open document: $title"
  ui_dump "$prefix-reader"
}

local_sha="$(sha256sum "$apk" | awk '{print $1}')"
[[ "$QA_PACKAGE" != "$PROTECTED_PACKAGE" ]] || fail "Safety guard resolved QA to protected package"
apk_application_id="$($aapt2 dump badging "$apk" | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -n 1)"
[[ "$apk_application_id" == "$QA_PACKAGE" ]] || {
  fail "REFUSED before install: APK applicationId=$apk_application_id, expected $QA_PACKAGE"
}
printf 'R9_QA_TARGET serial=%s package=%s\n' "$serial" "$QA_PACKAGE" | tee "$summary"
printf 'APK path=%s sha256=%s\n' "$apk" "$local_sha" | tee -a "$summary"

adbq install -r "$apk" | tee "$output_dir/install.txt"
grep -q 'Success' "$output_dir/install.txt" || fail "QA APK install failed"
pass "QA install -r"
package_path="$(adbq shell pm path "$QA_PACKAGE" | sed -n 's/^package://p' | head -n 1)"
[[ -n "$package_path" ]] || fail "Installed QA package path not found"
installed_copy="$(mktemp /tmp/markleaf-installed-qa.XXXXXX.apk)"
adbq pull "$package_path" "$installed_copy" >/dev/null
installed_sha="$(sha256sum "$installed_copy" | awk '{print $1}')"
[[ "$installed_sha" == "$local_sha" ]] || fail "Installed APK SHA-256 differs from requested APK"
rm -f "$installed_copy"
installed_copy=""
pass "installed APK SHA-256 matches"

adbq logcat -c
adbq shell am force-stop "$QA_PACKAGE"
adbq shell am start -W -n "$MAIN_ACTIVITY" > "$output_dir/launch.txt"
reader_ui="$(open_document_from_home "$document" primary)"
assert_qa_foreground "$reader_ui" "reader initial"
assert_topbar "$reader_ui" present "initial top bar visible"

swipe_in_scrollable "$reader_ui" down-document 2000 18
slow_hidden_ui="$(ui_dump slow-hidden)"
assert_topbar "$slow_hidden_ui" absent "slow downward drag hides top bar"
read -r sx1 sy1 sx2 sy2 <<< "$(scroll_bounds "$slow_hidden_ui")"
sx=$(((sx1 + sx2) / 2))
sy=$(((sy1 + sy2) / 2))
adbq shell input swipe "$sx" "$sy" "$sx" "$((sy + 20))" 500 >/dev/null
jitter_ui="$(ui_dump reverse-jitter)"
assert_topbar "$jitter_ui" absent "20px reverse jitter stays hidden"
swipe_in_scrollable "$jitter_ui" up-document 2000 18
slow_shown_ui="$(ui_dump slow-shown)"
assert_topbar "$slow_shown_ui" present "slow upward drag shows top bar"

read -r fast_x fast_bottom fast_top <<< "$(gesture_coords "$slow_shown_ui" 60)"
for ((i=0; i<10; i++)); do
  adbq shell input swipe "$fast_x" "$fast_bottom" "$fast_x" "$fast_top" 180 >/dev/null
  adbq shell input swipe "$fast_x" "$fast_top" "$fast_x" "$fast_bottom" 220 >/dev/null
done
[[ -n "$(adbq shell pidof -s "$QA_PACKAGE" || true)" ]] || fail "QA process died during fast cycles"
fast_final_ui="$(ui_dump fast-final)"
assert_qa_foreground "$fast_final_ui" "fast cycles"
assert_topbar "$fast_final_ui" present "fast cycles final top bar visible"

tap_node "$fast_final_ui" content-desc "搜索当前文档" "open search"
search_ui="$(ui_dump search-open)"
ui_has "$search_ui" content-desc "关闭搜索" || fail "search bar did not open"
[[ -z "$search_query" ]] || adbq shell input text "$search_query" >/dev/null
swipe_in_scrollable "$search_ui" down-document 180 60
search_after_ui="$(ui_dump search-after-fling)"
ui_has "$search_after_ui" content-desc "关闭搜索" || fail "search bar disappeared after fling"
ui_has "$search_after_ui" content-desc "返回" && fail "reader top bar overlaps search bar"
pass "search boundary and fling"
tap_node "$search_after_ui" content-desc "关闭搜索" "close search"
search_closed_ui="$(ui_dump search-closed)"
assert_topbar "$search_closed_ui" present "reader top bar restored after search"

original_accelerometer_rotation="$(adbq shell settings get system accelerometer_rotation | tr -d '\r')"
original_user_rotation="$(adbq shell settings get system user_rotation | tr -d '\r')"
rotation_changed=1
adbq shell settings put system accelerometer_rotation 0
adbq shell settings put system user_rotation 1
sleep 1
landscape_ui="$(ui_dump landscape)"
grep -q 'rotation="1"' "$landscape_ui" || fail "device did not enter landscape"
assert_topbar "$landscape_ui" present "top bar visible after landscape rotation"
swipe_in_scrollable "$landscape_ui" down-document 2000 18
landscape_hidden_ui="$(ui_dump landscape-hidden)"
assert_topbar "$landscape_hidden_ui" absent "landscape slow drag hides top bar"
# Landscape's scrollable height is much shorter than portrait. 18% is only
# about 59dp on the 25098PN5AC and cannot cross the production 88dp show
# threshold. Keep the slow duration, but use enough distance to exercise the
# actual show transition with input-delivery margin.
swipe_in_scrollable "$landscape_hidden_ui" up-document 2000 36
landscape_shown_ui="$(ui_dump landscape-shown)"
assert_topbar "$landscape_shown_ui" present "landscape slow reverse shows top bar"
adbq shell settings put system user_rotation 0
sleep 1
portrait_ui="$(ui_dump portrait-restored)"
grep -q 'rotation="0"' "$portrait_ui" || fail "device did not return to portrait"
pass "rotation settings restored"
adbq shell settings put system user_rotation "$original_user_rotation"
adbq shell settings put system accelerometer_rotation "$original_accelerometer_rotation"
rotation_changed=0

swipe_in_scrollable "$portrait_ui" down-document 180 60
hidden_before_back_ui="$(ui_dump hidden-before-back)"
assert_topbar "$hidden_before_back_ui" absent "top bar hidden before document switch"
adbq shell input keyevent 4 >/dev/null
home_after_ui="$(ui_dump home-after-back)"
assert_qa_foreground "$home_after_ui" "home after reader back"
if [[ -n "$second_document" ]] && ui_has "$home_after_ui" text "$second_document"; then
  tap_node "$home_after_ui" text "$second_document" "open second document"
  second_ui="$(ui_dump second-document)"
  assert_topbar "$second_ui" present "new document resets top bar visible"
  adbq shell input keyevent 4 >/dev/null
elif [[ -n "$second_document" ]]; then
  skip "second document card not found: $second_document"
else
  skip "document-switch check disabled"
fi
reader_ui="$(open_document_from_home "$document" performance)"
assert_topbar "$reader_ui" present "primary document reopened for performance"

if [[ -n "$top_marker" ]]; then
  for ((attempt=1; attempt<=12; attempt++)); do
    top_ui="$(ui_dump "top-check-$attempt")"
    ui_has "$top_ui" text "$top_marker" && break
    read -r top_x top_bottom top_top <<< "$(gesture_coords "$top_ui" 70)"
    for ((i=0; i<10; i++)); do
      adbq shell input swipe "$top_x" "$top_top" "$top_x" "$top_bottom" 100 >/dev/null
    done
  done
  ui_has "$top_ui" text "$top_marker" || fail "top marker not reached: $top_marker"
  assert_topbar "$top_ui" present "settled document top shows top bar"
fi

for ((run=1; run<=gfx_runs; run++)); do
  pre_ui="$(ui_dump "gfx-$run-pre")"
  if ! ui_has "$pre_ui" package "$QA_PACKAGE" ||
    ! ui_has "$pre_ui" content-desc "返回"; then
    printf '%s\tINVALID_READER_FOREGROUND\n' "$run" >> "$gfx_tsv"
    continue
  fi
  adbq shell dumpsys gfxinfo "$QA_PACKAGE" reset >/dev/null
  read -r gfx_x gfx_bottom gfx_top <<< "$(gesture_coords "$pre_ui" 60)"
  for ((i=0; i<cycles_per_run; i++)); do
    adbq shell input swipe "$gfx_x" "$gfx_bottom" "$gfx_x" "$gfx_top" 180 >/dev/null
    adbq shell input swipe "$gfx_x" "$gfx_top" "$gfx_x" "$gfx_bottom" 220 >/dev/null
  done
  post_ui="$(ui_dump "gfx-$run-post")"
  if ! ui_has "$post_ui" package "$QA_PACKAGE" ||
    ! ui_has "$post_ui" content-desc "返回"; then
    printf '%s\tINVALID_READER_FOREGROUND\n' "$run" >> "$gfx_tsv"
    continue
  fi
  gfx_file="$output_dir/gfxinfo-$run.txt"
  adbq shell dumpsys gfxinfo "$QA_PACKAGE" > "$gfx_file"
  field() { sed -n "s/^$1[[:space:]]*//p" "$gfx_file" | head -n 1; }
  printf '%s\tVALID\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$run" "$(field 'Total frames rendered:')" "$(field 'Janky frames:')" \
    "$(field '50th percentile:')" "$(field '90th percentile:')" \
    "$(field '95th percentile:')" "$(field '99th percentile:')" \
    "$(field 'Number Frame deadline missed:')" "$(field 'Number Slow UI thread:')" >> "$gfx_tsv"
done
valid_gfx="$(awk -F '\t' 'NR > 1 && $2 == "VALID" { n++ } END { print n+0 }' "$gfx_tsv")"
[[ "$valid_gfx" -eq "$gfx_runs" ]] || fail "Only $valid_gfx/$gfx_runs gfxinfo runs kept QA foreground"
pass "$valid_gfx focused gfxinfo runs"

if ((capture_perfetto)); then
  trace_name="r9-md-chrome-$(date +%Y%m%d-%H%M%S).pftrace"
  trace_device="/data/misc/perfetto-traces/$trace_name"
  trace_pid="$(adbq shell perfetto --background-wait -o "$trace_device" -t 20s \
    --app "$QA_PACKAGE" sched freq idle am wm gfx view binder_driver hal dalvik |
    tr -d '\r' | tail -n 1)"
  [[ "$trace_pid" =~ ^[0-9]+$ ]] || fail "Perfetto did not return a background PID"
  perfetto_ui="$(ui_dump perfetto-pre)"
  assert_qa_foreground "$perfetto_ui" "Perfetto preflight"
  assert_topbar "$perfetto_ui" present "Perfetto reader preflight"
  read -r perf_x perf_bottom perf_top <<< "$(gesture_coords "$perfetto_ui" 60)"
  for ((i=0; i<10; i++)); do
    adbq shell input swipe "$perf_x" "$perf_bottom" "$perf_x" "$perf_top" 180 >/dev/null
    adbq shell input swipe "$perf_x" "$perf_top" "$perf_x" "$perf_bottom" 220 >/dev/null
  done
  sleep 12
  perfetto_post_ui="$(ui_dump perfetto-post)"
  assert_qa_foreground "$perfetto_post_ui" "Perfetto postflight"
  assert_topbar "$perfetto_post_ui" present "Perfetto reader postflight"
  adbq pull "$trace_device" "$output_dir/$trace_name" >/dev/null
  trace_sha="$(sha256sum "$output_dir/$trace_name" | awk '{print $1}')"
  printf 'Perfetto path=%s sha256=%s\n' "$output_dir/$trace_name" "$trace_sha" | tee -a "$summary"
  pass "Perfetto capture"
fi

adbq logcat -b crash -d > "$output_dir/crash-buffer.txt"
adbq logcat -d -v threadtime > "$output_dir/logcat.txt"
if grep -Eq "Process: $QA_PACKAGE|ANR in $QA_PACKAGE" "$output_dir/crash-buffer.txt" "$output_dir/logcat.txt"; then
  fail "QA crash or ANR marker found"
fi
pass "no QA FATAL/ANR markers"

{
  printf 'Result: PASS\nSerial: %s\nPackage: %s\n' "$serial" "$QA_PACKAGE"
  printf 'APK SHA-256: %s\nInstalled SHA-256: %s\n' "$local_sha" "$installed_sha"
  printf 'Primary document: %s\nValid gfxinfo runs: %s/%s\n' "$document" "$valid_gfx" "$gfx_runs"
  printf 'Artifacts: %s\n' "$output_dir"
} | tee -a "$summary"
printf '\nR9_ADB_QA_PASS %s\n' "$output_dir"
