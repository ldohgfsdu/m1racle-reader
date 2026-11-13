#!/usr/bin/env bash
# Fail-closed Reader release: no debug fallback, no reuse of historical APK.
set -euo pipefail
die() { echo "::error::$*" >&2; exit 1; }
[[ "$GITHUB_REF" == refs/heads/master ]] || die "Only master can publish"
[[ -n "$GITHUB_SHA" && -n "$GITHUB_REPOSITORY" && -n "$RUNNER_TEMP" ]] || die "Missing CI identity"
for key in KEYSTORE_B64 M1RACLE_RELEASE_STORE_PASSWORD M1RACLE_RELEASE_KEY_ALIAS M1RACLE_RELEASE_KEY_PASSWORD GH_TOKEN; do
  [[ -n "$(printenv "$key" || true)" ]] || die "Required signing or GitHub secret absent: $key"
done
python3 tools/release_gate.py publish
code=$(sed -nE 's/^versionCode=([0-9]+)$/\1/p' app/version.properties)
version=$(sed -nE 's/^versionName=([^[:space:]]+)$/\1/p' app/version.properties)
tag="v$version"
apk="M1racle-Reader-$version-$code-release.apk"
git fetch origin master
[[ "$(git rev-parse origin/master)" == "$GITHUB_SHA" ]] || die "Master moved; abort stale release"
if git ls-remote --exit-code --tags origin "refs/tags/$tag" >/dev/null 2>&1; then die "Tag $tag already exists"; fi
if gh release view "$tag" >/dev/null 2>&1; then die "Release $tag already exists (including Draft)"; fi
previous=$(gh api "repos/$GITHUB_REPOSITORY/releases?per_page=100" --jq '[.[] | select(.draft == false and .prerelease == false)] | sort_by(.published_at) | last | .tag_name // empty')
[[ "$previous" != "$tag" ]] || die "Version already published"
umask 077
keystore="$RUNNER_TEMP/reader-release.jks"
trap 'rm -f "$keystore"' EXIT
printf '%s' "$KEYSTORE_B64" | base64 --decode > "$keystore"
[[ -s "$keystore" ]] || die "Keystore is empty"
export M1RACLE_RELEASE_STORE_FILE="$keystore"
jvm="-Xmx2g -XX:MaxMetaspaceSize=1024m -XX:ReservedCodeCacheSize=256m -XX:+UseSerialGC -Dfile.encoding=UTF-8"
./gradlew :app:verifyMarkdownEditorBundle :app:testQaUnitTest :app:testReleaseUnitTest --no-daemon --max-workers=1 --console=plain "-Dorg.gradle.jvmargs=$jvm"
./gradlew :app:lintRelease --no-daemon --max-workers=1 --console=plain "-Dorg.gradle.jvmargs=$jvm"
./gradlew :app:assembleRelease --no-daemon --max-workers=1 --console=plain "-Dorg.gradle.jvmargs=$jvm"
source_apk=app/build/outputs/apk/release/app-release.apk
[[ -s "$source_apk" ]] || die "Fresh signed APK missing"
signer="$ANDROID_HOME/build-tools/36.0.0/apksigner"
align="$ANDROID_HOME/build-tools/36.0.0/zipalign"
aapt="$ANDROID_HOME/build-tools/36.0.0/aapt2"
"$signer" verify --verbose --print-certs "$source_apk"
"$align" -c -P 16 -v 4 "$source_apk"
python3 tools/release_gate.py native-libs --apk "$source_apk"
badging=$("$aapt" dump badging "$source_apk")
pkg=$(printf '%s\n' "$badging" | grep '^package:')
[[ "$pkg" == *"name='com.m1racle.reader'"* ]] || die "Wrong package"
[[ "$pkg" == *"versionCode='$code'"* ]] || die "Wrong versionCode"
[[ "$pkg" == *"versionName='$version'"* ]] || die "Wrong versionName"
grep -q "sdkVersion:'31'" <<< "$badging" || die "Wrong minSdk"
grep -q "targetSdkVersion:'35'" <<< "$badging" || die "Wrong targetSdk"
if "$aapt" dump permissions "$source_apk" | grep -Fq android.permission.INTERNET; then die "Unexpected INTERNET permission"; fi
cert=$("$signer" verify --print-certs "$source_apk" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -1 | tr '[:upper:]' '[:lower:]')
expected="2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1"
[[ "$cert" == "$expected" ]] || die "Production signing identity differs from historical certificate"
if [[ -n "$previous" ]]; then
  mkdir -p "$RUNNER_TEMP/previous"
  gh release download "$previous" --pattern 'M1racle-Reader-*-release.apk' --dir "$RUNNER_TEMP/previous"
  old_file=$(find "$RUNNER_TEMP/previous" -maxdepth 1 -name 'M1racle-Reader-*-release.apk' -type f -print -quit)
  [[ -n "$old_file" ]] || die "Previous public signed APK not found"
  [[ "$(find "$RUNNER_TEMP/previous" -maxdepth 1 -name 'M1racle-Reader-*-release.apk' -type f | wc -l)" == 1 ]] || die "Ambiguous previous APK"
  "$signer" verify "$old_file"
  old_cert=$("$signer" verify --print-certs "$old_file" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -1 | tr '[:upper:]' '[:lower:]')
  [[ "$old_cert" == "$cert" ]] || die "Certificate not continuous"
  old_code=$("$aapt" dump badging "$old_file" | sed -nE "s/^package:.*versionCode='([0-9]+)'.*/\1/p" | head -1)
  [[ "$old_code" =~ ^[0-9]+$ ]] || die "Cannot parse previous versionCode"
  (( code > old_code )) || die "versionCode must increase"
fi
outbox=.external_outbox/m1racle-reader-release-latest.apk
[[ -s "$outbox" ]] || die "Fresh Outbox APK missing"
cmp "$source_apk" "$outbox" || die "Outbox differs from new release"
cp "$source_apk" "$apk"
sha256sum "$apk" > "$apk.sha256"
sha256sum --check "$apk.sha256"
git fetch origin master
[[ "$(git rev-parse origin/master)" == "$GITHUB_SHA" ]] || die "Master advanced before publish"
notes="$RUNNER_TEMP/release-notes.md"
cp "docs/releases/v$version.md" "$notes"
{
 echo
 echo "## 安装包与校验"
 printf -- '- APK：%s（%s 字节）\n' "$apk" "$(stat -c %s "$apk")"
 printf -- '- SHA-256：%s\n' "$(cut -d ' ' -f 1 "$apk.sha256")"
 echo "- 自动验证：QA/Release 单测、生产 Lint、原签名、16 KiB 对齐及远端回读"
 echo "- 真机验收：待验证（CI 不等于物理手机）"
} >> "$notes"
gh release create "$tag" "$apk" "$apk.sha256" --draft --target "$GITHUB_SHA" --title "M1racle Reader $version" --notes-file "$notes"
mkdir -p "$RUNNER_TEMP/readback"
gh release download "$tag" --pattern "$apk" --dir "$RUNNER_TEMP/readback"
gh release download "$tag" --pattern "$apk.sha256" --dir "$RUNNER_TEMP/readback"
cmp "$apk" "$RUNNER_TEMP/readback/$apk" || die "Remote APK differs"
cmp "$apk.sha256" "$RUNNER_TEMP/readback/$apk.sha256" || die "Remote checksum differs"
(cd "$RUNNER_TEMP/readback" && sha256sum --check "$apk.sha256")
"$signer" verify --print-certs "$RUNNER_TEMP/readback/$apk"
"$align" -c -P 16 -v 4 "$RUNNER_TEMP/readback/$apk"
[[ "$(gh release view "$tag" --json assets --jq '.assets | length')" == 2 ]] || die "Expected exactly 2 assets"
gh release edit "$tag" --draft=false
[[ "$(gh release view "$tag" --json isDraft --jq .isDraft)" == false ]] || die "Still a Draft"
echo "Published $tag after signed remote read-back"
